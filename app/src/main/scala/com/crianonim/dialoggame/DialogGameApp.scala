package com.crianonim.dialoggame

import cats.Monad
import cats.data.EitherT
import cats.effect.*
import cats.effect.std.Random
import cats.syntax.all.*
import tyrian.*
import tyrian.Html.*
import org.scalajs.dom
import io.circe.syntax.*
import io.circe.parser.*
import io.circe.generic.semiauto.*
import io.circe.{Decoder, Encoder}
import scala.scalajs.js

import com.crianonim.ui.*
import com.crianonim.dialog.*
import com.crianonim.screept.*

/** Full-featured dialog-game editor + player, reimplementing `dialog-game-next`'s `/dialoggame`:
  * a two-column player/editor with inline Screept editing, option/action reordering, action-type
  * switching, create/jump-to dialog, an environment inspector, a status line, and localStorage
  * persistence (auto-save + named saved games).
  *
  * As in the Dialog tab, all Screept evaluation is effectful and resolved to plain strings in
  * [[update]] (never in [[view]]).
  */
object DialogGameApp {

  // ============ VIEW DATA ============

  case class ResolvedOption(optionId: String, text: String)
  case class Resolved(
      dialogId: String,
      paragraphs: List[String],
      statusLine: Option[String],
      options: List[ResolvedOption]
  )

  case class SavedMeta(id: String, title: String, updatedAt: Long)
  object SavedMeta:
    given Encoder[SavedMeta] = deriveEncoder
    given Decoder[SavedMeta] = deriveDecoder

  // ============ EDITOR ADDRESSING ============

  enum Dir:
    case Up, Down

  enum ActionKind:
    case GoBack, GoDialog, Msg, Screept, Conditional, Block

  enum ActionField:
    case Destination, Value, Condition

  /** A step descending into a nested action list. */
  enum Container:
    case Then(index: Int)
    case Else(index: Int)
    case Block(index: Int)

  enum EditTarget:
    case DialogText(dialogId: String)
    case OptionText(dialogId: String, optionId: String)
    case OptionCondition(dialogId: String, optionId: String)
    case ActionValue(
        dialogId: String,
        optionId: String,
        path: List[Container],
        index: Int,
        field: ActionField
    )

  // ============ MODEL ============

  case class Model(
      gameDefinition: GameDefinition,
      initialGameState: GameState,
      currentGameId: Option[String],
      currentTitle: String,
      resolved: Option[Resolved],
      runError: Option[String],
      showEditor: Boolean,
      selectedDialogId: Option[String],
      edit: Option[EditTarget],
      editBuffer: String,
      editError: Option[String],
      savedGames: List[SavedMeta],
      importExportVisible: Boolean,
      importText: String,
      message: Option[String]
  )

  enum Msg:
    // Player
    case SelectOption(optionId: String)
    case Rendered(state: GameState, resolved: Resolved)
    case RunFailed(error: String)
    case ClearOutput
    case Restart
    case ToggleEditor
    // Editor structure
    case SelectDialog(dialogId: String)
    case AddDialog
    case DeleteDialog(dialogId: String)
    case AddOption(dialogId: String)
    case DeleteOption(dialogId: String, optionId: String)
    case MoveOption(dialogId: String, optionId: String, dir: Dir)
    case AddAction(dialogId: String, optionId: String, path: List[Container], kind: ActionKind)
    case DeleteAction(dialogId: String, optionId: String, path: List[Container], index: Int)
    case MoveAction(dialogId: String, optionId: String, path: List[Container], index: Int, dir: Dir)
    case ChangeActionType(
        dialogId: String,
        optionId: String,
        path: List[Container],
        index: Int,
        kind: ActionKind
    )
    case CreateDialogFromDestination(dest: String)
    case JumpToDialog(dest: String)
    // Inline edit
    case StartEdit(target: EditTarget)
    case UpdateBuffer(value: String)
    case SaveEdit
    case CancelEdit
    // Persistence
    case InitLoaded(games: List[SavedMeta], autosave: Option[GameDefinition])
    case SetTitle(title: String)
    case SaveGame(asNew: Boolean)
    case Saved(id: String, games: List[SavedMeta])
    case LoadGame(id: String)
    case GameLoaded(gd: GameDefinition, id: String)
    case DeleteGame(id: String)
    case GamesRefreshed(games: List[SavedMeta])
    case NewBlankGame
    case SetImportText(text: String)
    case ImportJson
    case ExportJson
    case ShowImportExport
    case HideImportExport
    case DismissMessage

  def init: Model = Model(
    gameDefinition = sampleGameDefinition,
    initialGameState = sampleGameDefinition.gameState,
    currentGameId = None,
    currentTitle = "",
    resolved = None,
    runError = None,
    showEditor = false,
    selectedDialogId = Some("start"),
    edit = None,
    editBuffer = "",
    editError = None,
    savedGames = List.empty,
    importExportVisible = false,
    importText = "",
    message = None
  )

  /** Loads the saved-games index and any autosave from localStorage; wired from `App.init`. */
  def initCmd: Cmd[IO, Msg] =
    Cmd.Run {
      IO {
        val games = LS
          .get("dg_index")
          .flatMap(s => decode[List[SavedMeta]](s).toOption)
          .getOrElse(List.empty)
        val autosave = LS.get("dg_autosave").flatMap(s => decode[GameDefinition](s).toOption)
        (games, autosave)
      }
    }(res => Msg.InitLoaded(res._1, res._2))

  // ============ localStorage ============

  private object LS:
    def get(key: String): Option[String] =
      Option(dom.window.localStorage.getItem(key))
    def set(key: String, value: String): Unit =
      dom.window.localStorage.setItem(key, value)
    def remove(key: String): Unit =
      dom.window.localStorage.removeItem(key)

  private def autosaveCmd(gd: GameDefinition): Cmd[IO, Msg] =
    Cmd.SideEffect[IO, Unit](IO(LS.set("dg_autosave", gd.asJson.noSpaces)))

  private def gameKey(id: String): String = s"dg_game_$id"

  // ============ RESOLUTION (effectful) ============

  private def resolveProgram[F[_]: Monad: Random](
      dialogs: Dialogs,
      state: GameState
  ): EitherT[F, EvaluationError, Resolved] =
    state.dialogStack.headOption.flatMap(dialogs.get) match
      case None =>
        EitherT.leftT[F, Resolved](OtherError("No current dialog on the stack"))
      case Some(dialog) =>
        val env = state.screeptEnv
        for
          visible <- DialogEngine.getVisibleOptions[F](dialog.options, env)
          paras   <- DialogEngine.getSplitStringOnNL[F](dialog.text, env)
          status  <- DialogEngine.getStatusLine[F](env)
          opts <- visible.traverse { o =>
            EitherT(Screept.eval[F](o.text, env))
              .map(v => ResolvedOption(o.id, Evaluator.getStringValue(v)))
          }
        yield Resolved(dialog.id, paras, status, opts)

  private def resolve(gd: GameDefinition): Cmd[IO, Msg] =
    Cmd.Run {
      Random.scalaUtilRandom[IO].flatMap { implicit r =>
        resolveProgram[IO](gd.dialogs, gd.gameState).value
      }
    } {
      case Right(rd) => Msg.Rendered(gd.gameState, rd)
      case Left(err) => Msg.RunFailed(EvaluationError.show(err))
    }

  /** Standard result of a mutation: store new game def and re-resolve (which also autosaves). */
  private def mutated(model: Model, gd: GameDefinition): (Model, Cmd[IO, Msg]) =
    (model.copy(gameDefinition = gd), resolve(gd))

  // ============ UPDATE ============

  def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = {
    case Msg.SelectOption(optionId) =>
      val gd = model.gameDefinition
      DialogEngine.getDialogFromStack(gd).flatMap(_.options.find(_.id == optionId)) match
        case None => (model, Cmd.None)
        case Some(opt) =>
          val cmd = Cmd.Run {
            Random.scalaUtilRandom[IO].flatMap { implicit r =>
              val prog =
                for
                  newState <- DialogEngine.gameStateReducer[IO](gd.gameState, opt.actions)
                  rd       <- resolveProgram[IO](gd.dialogs, newState)
                yield (newState, rd)
              prog.value
            }
          } {
            case Right((st, rd)) => Msg.Rendered(st, rd)
            case Left(err)       => Msg.RunFailed(EvaluationError.show(err))
          }
          (model, cmd)

    case Msg.Rendered(state, rd) =>
      val gd = model.gameDefinition.copy(gameState = state)
      (model.copy(gameDefinition = gd, resolved = Some(rd), runError = None), autosaveCmd(gd))

    case Msg.RunFailed(err) =>
      (model.copy(runError = Some(err)), Cmd.None)

    case Msg.ClearOutput =>
      val gd = model.gameDefinition
      val cleared = gd.copy(gameState =
        gd.gameState.copy(screeptEnv = gd.gameState.screeptEnv.copy(output = List.empty))
      )
      mutated(model, cleared)

    case Msg.Restart =>
      val gd = model.gameDefinition.copy(gameState = model.initialGameState)
      (model.copy(gameDefinition = gd, runError = None), resolve(gd))

    case Msg.ToggleEditor =>
      (model.copy(showEditor = !model.showEditor), Cmd.None)

    // ---- Editor structure ----
    case Msg.SelectDialog(dialogId) =>
      (model.copy(selectedDialogId = Some(dialogId), edit = None), Cmd.None)

    case Msg.AddDialog =>
      val d  = DialogEngine.generateNewDialog()
      val gd = model.gameDefinition.copy(dialogs = model.gameDefinition.dialogs.updated(d.id, d))
      val (m, cmd) = mutated(model.copy(selectedDialogId = Some(d.id)), gd)
      (m, cmd)

    case Msg.DeleteDialog(dialogId) =>
      val gd = model.gameDefinition.copy(dialogs = model.gameDefinition.dialogs - dialogId)
      val sel = model.selectedDialogId.filterNot(_ == dialogId)
      mutated(model.copy(selectedDialogId = sel), gd)

    case Msg.AddOption(dialogId) =>
      mutated(
        model,
        mapDialog(model.gameDefinition, dialogId)(d =>
          d.copy(options = d.options :+ DialogEngine.generateNewOption())
        )
      )

    case Msg.DeleteOption(dialogId, optionId) =>
      mutated(
        model,
        mapDialog(model.gameDefinition, dialogId)(d =>
          d.copy(options = d.options.filterNot(_.id == optionId))
        )
      )

    case Msg.MoveOption(dialogId, optionId, dir) =>
      mutated(
        model,
        mapDialog(model.gameDefinition, dialogId) { d =>
          val i = d.options.indexWhere(_.id == optionId)
          d.copy(options = move(d.options, i, dir))
        }
      )

    case Msg.AddAction(dialogId, optionId, path, kind) =>
      mutated(
        model,
        mapActions(model.gameDefinition, dialogId, optionId)(root =>
          modifyActionList(root, path)(_ :+ newAction(kind))
        )
      )

    case Msg.DeleteAction(dialogId, optionId, path, index) =>
      mutated(
        model,
        mapActions(model.gameDefinition, dialogId, optionId)(root =>
          modifyActionList(root, path)(_.patch(index, Nil, 1))
        )
      )

    case Msg.MoveAction(dialogId, optionId, path, index, dir) =>
      mutated(
        model,
        mapActions(model.gameDefinition, dialogId, optionId)(root =>
          modifyActionList(root, path)(list => move(list, index, dir))
        )
      )

    case Msg.ChangeActionType(dialogId, optionId, path, index, kind) =>
      mutated(
        model,
        mapActions(model.gameDefinition, dialogId, optionId)(root =>
          modifyActionList(root, path)(list =>
            if index < 0 || index >= list.length then list
            else list.updated(index, convertAction(list(index), kind))
          )
        )
      )

    case Msg.CreateDialogFromDestination(dest) =>
      if model.gameDefinition.dialogs.contains(dest) then (model, Cmd.None)
      else
        val d  = Dialog(dest, Screept.litText("New dialog"), List.empty)
        val gd = model.gameDefinition.copy(dialogs = model.gameDefinition.dialogs.updated(dest, d))
        mutated(model.copy(selectedDialogId = Some(dest)), gd)

    case Msg.JumpToDialog(dest) =>
      (model.copy(selectedDialogId = Some(dest), showEditor = true), Cmd.None)

    // ---- Inline edit ----
    case Msg.StartEdit(target) =>
      (
        model.copy(
          edit = Some(target),
          editBuffer = currentSource(model.gameDefinition, target),
          editError = None
        ),
        Cmd.None
      )

    case Msg.UpdateBuffer(value) =>
      (model.copy(editBuffer = value), Cmd.None)

    case Msg.CancelEdit =>
      (model.copy(edit = None, editBuffer = "", editError = None), Cmd.None)

    case Msg.SaveEdit =>
      model.edit match
        case None => (model, Cmd.None)
        case Some(target) =>
          applyEdit(model.gameDefinition, target, model.editBuffer) match
            case Right(gd) =>
              mutated(model.copy(edit = None, editBuffer = "", editError = None), gd)
            case Left(err) =>
              (model.copy(editError = Some(err)), Cmd.None)

    // ---- Persistence ----
    case Msg.InitLoaded(games, autosave) =>
      val gd = autosave.getOrElse(model.gameDefinition)
      (
        model.copy(
          gameDefinition = gd,
          initialGameState = gd.gameState,
          savedGames = games
        ),
        resolve(gd)
      )

    case Msg.SetTitle(title) =>
      (model.copy(currentTitle = title), Cmd.None)

    case Msg.SaveGame(asNew) =>
      val id    = if asNew then genId("game") else model.currentGameId.getOrElse(genId("game"))
      val title = if model.currentTitle.trim.isEmpty then "Untitled" else model.currentTitle
      val gd    = model.gameDefinition
      val cmd = Cmd.Run {
        IO {
          LS.set(gameKey(id), gd.asJson.noSpaces)
          val idx     = LS.get("dg_index").flatMap(decode[List[SavedMeta]](_).toOption).getOrElse(Nil)
          val updated = idx.filterNot(_.id == id) :+ SavedMeta(id, title, System.currentTimeMillis())
          LS.set("dg_index", updated.asJson.noSpaces)
          updated
        }
      }(games => Msg.Saved(id, games))
      (model.copy(currentGameId = Some(id), currentTitle = title), cmd)

    case Msg.Saved(id, games) =>
      (model.copy(currentGameId = Some(id), savedGames = games, message = Some("Saved")), Cmd.None)

    case Msg.LoadGame(id) =>
      val cmd = Cmd.Run {
        IO(LS.get(gameKey(id)).flatMap(decode[GameDefinition](_).toOption))
      } {
        case Some(gd) => Msg.GameLoaded(gd, id)
        case None     => Msg.RunFailed(s"Could not load game $id")
      }
      (model, cmd)

    case Msg.GameLoaded(gd, id) =>
      val title = model.savedGames.find(_.id == id).map(_.title).getOrElse(model.currentTitle)
      (
        model.copy(
          gameDefinition = gd,
          initialGameState = gd.gameState,
          currentGameId = Some(id),
          currentTitle = title,
          selectedDialogId = gd.gameState.dialogStack.headOption,
          importExportVisible = false,
          message = Some(s"Loaded $title")
        ),
        resolve(gd)
      )

    case Msg.DeleteGame(id) =>
      val cmd = Cmd.Run {
        IO {
          LS.remove(gameKey(id))
          val idx     = LS.get("dg_index").flatMap(decode[List[SavedMeta]](_).toOption).getOrElse(Nil)
          val updated = idx.filterNot(_.id == id)
          LS.set("dg_index", updated.asJson.noSpaces)
          updated
        }
      }(Msg.GamesRefreshed.apply)
      val clearedCurrent = if model.currentGameId.contains(id) then None else model.currentGameId
      (model.copy(currentGameId = clearedCurrent), cmd)

    case Msg.GamesRefreshed(games) =>
      (model.copy(savedGames = games), Cmd.None)

    case Msg.NewBlankGame =>
      val gd = DialogEngine.generateNewGameDefinition()
      (
        model.copy(
          gameDefinition = gd,
          initialGameState = gd.gameState,
          currentGameId = None,
          currentTitle = "",
          selectedDialogId = gd.dialogs.keys.headOption,
          message = Some("New blank game")
        ),
        resolve(gd)
      )

    case Msg.SetImportText(text) =>
      (model.copy(importText = text), Cmd.None)

    case Msg.ImportJson =>
      decode[GameDefinition](model.importText) match
        case Right(gd) =>
          (
            model.copy(
              gameDefinition = gd,
              initialGameState = gd.gameState,
              currentGameId = None,
              selectedDialogId = gd.gameState.dialogStack.headOption.orElse(gd.dialogs.keys.headOption),
              importExportVisible = false,
              importText = "",
              message = Some("Imported")
            ),
            resolve(gd)
          )
        case Left(err) =>
          (model.copy(message = Some(s"Import failed: ${err.getMessage}")), Cmd.None)

    case Msg.ExportJson =>
      val jsonStr = model.gameDefinition.asJson.spaces2
      val cmd = Cmd.SideEffect[IO, Unit](
        IO {
          val blob = new dom.Blob(
            js.Array(jsonStr),
            dom.BlobPropertyBag(`type` = "application/json")
          )
          val url  = dom.URL.createObjectURL(blob)
          val link = dom.document.createElement("a").asInstanceOf[dom.HTMLAnchorElement]
          link.href = url
          link.download = "dialog-game.json"
          dom.document.body.appendChild(link)
          link.click()
          dom.document.body.removeChild(link)
          dom.URL.revokeObjectURL(url)
        }
      )
      (model, cmd)

    case Msg.ShowImportExport =>
      (model.copy(importExportVisible = true), Cmd.None)

    case Msg.HideImportExport =>
      (model.copy(importExportVisible = false), Cmd.None)

    case Msg.DismissMessage =>
      (model.copy(message = None), Cmd.None)
  }

  // ============ EDITOR PURE HELPERS ============

  private def genId(prefix: String): String =
    s"$prefix-${System.currentTimeMillis()}-${scala.util.Random.nextInt(100000)}"

  private def move[A](xs: List[A], i: Int, dir: Dir): List[A] = dir match
    case Dir.Up   => DialogEngine.moveUp(xs, i)
    case Dir.Down => DialogEngine.moveDown(xs, i)

  private def mapDialog(gd: GameDefinition, dialogId: String)(f: Dialog => Dialog): GameDefinition =
    gd.dialogs.get(dialogId).fold(gd)(d => gd.copy(dialogs = gd.dialogs.updated(dialogId, f(d))))

  private def mapOption(gd: GameDefinition, dialogId: String, optionId: String)(
      f: DialogOption => DialogOption
  ): GameDefinition =
    mapDialog(gd, dialogId)(d =>
      d.copy(options = d.options.map(o => if o.id == optionId then f(o) else o))
    )

  private def mapActions(gd: GameDefinition, dialogId: String, optionId: String)(
      f: List[DialogAction] => List[DialogAction]
  ): GameDefinition =
    mapOption(gd, dialogId, optionId)(o => o.copy(actions = f(o.actions)))

  private def findOption(gd: GameDefinition, dialogId: String, optionId: String): Option[DialogOption] =
    gd.dialogs.get(dialogId).flatMap(_.options.find(_.id == optionId))

  /** Apply `f` to the action list addressed by `path` (empty path = root list). */
  private def modifyActionList(root: List[DialogAction], path: List[Container])(
      f: List[DialogAction] => List[DialogAction]
  ): List[DialogAction] =
    path match
      case Nil => f(root)
      case step :: rest =>
        root.zipWithIndex.map { case (a, idx) =>
          step match
            case Container.Then(i) if i == idx =>
              a match
                case c: Conditional => c.copy(thenActions = modifyActionList(c.thenActions, rest)(f))
                case other          => other
            case Container.Else(i) if i == idx =>
              a match
                case c: Conditional => c.copy(elseActions = modifyActionList(c.elseActions, rest)(f))
                case other          => other
            case Container.Block(i) if i == idx =>
              a match
                case b: BlockAction => b.copy(actions = modifyActionList(b.actions, rest)(f))
                case other          => other
            case _ => a
        }

  private def descend(actions: List[DialogAction], path: List[Container]): Option[List[DialogAction]] =
    path match
      case Nil => Some(actions)
      case Container.Then(i) :: rest =>
        actions.lift(i).collect { case c: Conditional => c }.flatMap(c => descend(c.thenActions, rest))
      case Container.Else(i) :: rest =>
        actions.lift(i).collect { case c: Conditional => c }.flatMap(c => descend(c.elseActions, rest))
      case Container.Block(i) :: rest =>
        actions.lift(i).collect { case b: BlockAction => b }.flatMap(b => descend(b.actions, rest))

  private def getActionAt(
      gd: GameDefinition,
      dialogId: String,
      optionId: String,
      path: List[Container],
      index: Int
  ): Option[DialogAction] =
    findOption(gd, dialogId, optionId).flatMap(o => descend(o.actions, path)).flatMap(_.lift(index))

  private def newAction(kind: ActionKind): DialogAction = kind match
    case ActionKind.GoBack      => GoBack(genId("action"))
    case ActionKind.GoDialog    => GoDialog(genId("action"), "")
    case ActionKind.Msg         => MsgAction(genId("action"), Screept.litText(""))
    case ActionKind.Screept     => ScreeptAction(genId("action"), Print(Screept.litText("Hello!")))
    case ActionKind.Conditional => Conditional(genId("action"), Screept.litNum(1), Nil, Nil)
    case ActionKind.Block       => BlockAction(genId("action"), Nil)

  /** Convert an action to a new kind, preserving id and compatible fields. */
  private def convertAction(a: DialogAction, kind: ActionKind): DialogAction =
    val id = a.id
    kind match
      case ActionKind.GoBack => GoBack(id)
      case ActionKind.GoDialog =>
        GoDialog(id, a match { case g: GoDialog => g.destination; case _ => "" })
      case ActionKind.Msg =>
        MsgAction(id, a match { case m: MsgAction => m.value; case _ => Screept.litText("") })
      case ActionKind.Screept =>
        ScreeptAction(id, a match { case s: ScreeptAction => s.value; case _ => Print(Screept.litText("")) })
      case ActionKind.Conditional =>
        a match { case c: Conditional => c; case _ => Conditional(id, Screept.litNum(1), Nil, Nil) }
      case ActionKind.Block =>
        a match { case b: BlockAction => b; case _ => BlockAction(id, Nil) }

  private def kindOf(a: DialogAction): ActionKind = a match
    case _: GoBack        => ActionKind.GoBack
    case _: GoDialog      => ActionKind.GoDialog
    case _: MsgAction     => ActionKind.Msg
    case _: ScreeptAction => ActionKind.Screept
    case _: Conditional   => ActionKind.Conditional
    case _: BlockAction   => ActionKind.Block

  private def kindLabel(k: ActionKind): String = k match
    case ActionKind.GoBack      => "go back"
    case ActionKind.GoDialog    => "go dialog"
    case ActionKind.Msg         => "msg"
    case ActionKind.Screept     => "screept"
    case ActionKind.Conditional => "conditional"
    case ActionKind.Block       => "block"

  private def parseKind(s: String): ActionKind =
    ActionKind.values.find(k => kindLabel(k) == s).getOrElse(ActionKind.GoBack)

  // ---- source rendering + parsing for inline edit ----

  private def currentSource(gd: GameDefinition, target: EditTarget): String = target match
    case EditTarget.DialogText(did) =>
      gd.dialogs.get(did).map(d => ScreeptPrinter.expr(d.text)).getOrElse("")
    case EditTarget.OptionText(did, oid) =>
      findOption(gd, did, oid).map(o => ScreeptPrinter.expr(o.text)).getOrElse("")
    case EditTarget.OptionCondition(did, oid) =>
      findOption(gd, did, oid).flatMap(_.condition).map(ScreeptPrinter.expr).getOrElse("")
    case EditTarget.ActionValue(did, oid, path, idx, field) =>
      getActionAt(gd, did, oid, path, idx) match
        case Some(GoDialog(_, dest)) if field == ActionField.Destination  => dest
        case Some(MsgAction(_, v)) if field == ActionField.Value          => ScreeptPrinter.expr(v)
        case Some(ScreeptAction(_, v)) if field == ActionField.Value      => ScreeptPrinter.stmt(v)
        case Some(Conditional(_, c, _, _)) if field == ActionField.Condition => ScreeptPrinter.expr(c)
        case _                                                            => ""

  private def applyEdit(
      gd: GameDefinition,
      target: EditTarget,
      buffer: String
  ): Either[String, GameDefinition] = target match
    case EditTarget.DialogText(did) =>
      Screept.parseExpr(buffer).map(e => gd.copy(dialogs = DialogEngine.updateDialogText(gd.dialogs, did, e)))
    case EditTarget.OptionText(did, oid) =>
      Screept.parseExpr(buffer).map(e => mapOption(gd, did, oid)(_.copy(text = e)))
    case EditTarget.OptionCondition(did, oid) =>
      if buffer.trim.isEmpty then Right(mapOption(gd, did, oid)(_.copy(condition = None)))
      else Screept.parseExpr(buffer).map(e => mapOption(gd, did, oid)(_.copy(condition = Some(e))))
    case EditTarget.ActionValue(did, oid, path, idx, field) =>
      def setAt(f: DialogAction => Either[String, DialogAction]): Either[String, GameDefinition] =
        getActionAt(gd, did, oid, path, idx) match
          case None => Left("Action not found")
          case Some(a) =>
            f(a).map(na =>
              mapActions(gd, did, oid)(root =>
                modifyActionList(root, path)(list =>
                  if idx < 0 || idx >= list.length then list else list.updated(idx, na)
                )
              )
            )
      field match
        case ActionField.Destination =>
          setAt {
            case g: GoDialog => Right(g.copy(destination = buffer))
            case _           => Left("Not a go-dialog action")
          }
        case ActionField.Value =>
          setAt {
            case m: MsgAction     => Screept.parseExpr(buffer).map(e => m.copy(value = e))
            case s: ScreeptAction => Screept.parse(buffer).map(st => s.copy(value = st))
            case _                => Left("Action has no value")
          }
        case ActionField.Condition =>
          setAt {
            case c: Conditional => Screept.parseExpr(buffer).map(e => c.copy(cond = e))
            case _              => Left("Not a conditional")
          }

  // ============ VIEW ============

  def view(model: Model): Html[Msg] =
    div(cls := "flex flex-col gap-3")(
      viewTopBar(model),
      model.message match
        case Some(msg) =>
          div(cls := "flex items-center gap-2 bg-green-50 border border-green-200 rounded px-3 py-1 text-sm text-green-800")(
            div()(text(msg)),
            Button.secondary("✕", Msg.DismissMessage, Button.Size.Small)
          )
        case None => div()()
      ,
      div(cls := "flex gap-4")(
        div(cls := (if model.showEditor then "w-1/2 flex flex-col gap-3" else "w-full flex flex-col gap-3"))(
          viewPlayer(model),
          viewEnv(model)
        ),
        if model.showEditor then div(cls := "w-1/2")(viewEditor(model)) else div()()
      ),
      viewImportExportModal(model)
    )

  private def viewTopBar(model: Model): Html[Msg] =
    div(cls := "flex flex-wrap justify-between items-center gap-2")(
      div(cls := "flex items-center gap-2")(
        div(cls := "text-lg font-semibold text-gray-700")(text("Dialog Game")),
        Input.interactive(model.currentTitle, Msg.SetTitle.apply),
        Button.primary("Save", Msg.SaveGame(false), Button.Size.Small),
        Button.secondary("Save As", Msg.SaveGame(true), Button.Size.Small),
        Button.secondary("New", Msg.NewBlankGame, Button.Size.Small)
      ),
      div(cls := "flex items-center gap-2")(
        Button.secondary("Restart", Msg.Restart, Button.Size.Small),
        Button.secondary("Import/Export", Msg.ShowImportExport, Button.Size.Small),
        Button.primary(
          if model.showEditor then "Hide Editor" else "Show Editor",
          Msg.ToggleEditor,
          Button.Size.Small
        )
      )
    )

  // ---- Player ----
  private def viewPlayer(model: Model): Html[Msg] =
    Card.simple(Card.Variant.Elevated, Card.Padding.Medium)(
      div(cls := "flex flex-col gap-3")(
        model.resolved match
          case None =>
            div(cls := "text-gray-400 italic")(text("Loading…"))
          case Some(rd) =>
            div(cls := "flex flex-col gap-3")(
              rd.statusLine match
                case Some(s) => div(cls := "text-sm text-slate-600 border-b border-gray-200 pb-1")(text(s))
                case None    => div()()
              ,
              div(cls := "flex flex-col gap-1 text-gray-900")(
                rd.paragraphs.map(p => div()(text(p)))
              ),
              div(cls := "flex flex-col gap-2")(
                if rd.options.isEmpty then
                  List(div(cls := "text-gray-400 italic")(text("No available options")))
                else
                  rd.options.map(o =>
                    div()(Button.primary(o.text, Msg.SelectOption(o.optionId), Button.Size.Medium))
                  )
              )
            )
        ,
        model.runError match
          case Some(err) => div(cls := "text-red-600 text-sm")(text(s"Error: $err"))
          case None      => div()()
        ,
        viewLog(model)
      )
    )

  private def viewLog(model: Model): Html[Msg] =
    val out = model.gameDefinition.gameState.screeptEnv.output
    div(cls := "flex flex-col border border-gray-300 rounded-md overflow-hidden")(
      div(cls := "flex justify-between items-center px-3 py-1.5 bg-gray-100 border-b border-gray-300")(
        div(cls := "text-xs font-semibold text-gray-600")(text("Log")),
        Button.secondary("Clear", Msg.ClearOutput, Button.Size.Small)
      ),
      div(cls := "max-h-[200px] overflow-auto p-3 font-mono text-sm bg-white")(
        if out.isEmpty then List(div(cls := "text-gray-400 italic")(text("No messages yet")))
        else out.reverse.map(line => div()(text(line.value)))
      )
    )

  private def viewEnv(model: Model): Html[Msg] =
    val env = model.gameDefinition.gameState.screeptEnv
    val varRows = env.vars.toList.sortBy(_._1).map { case (name, value) =>
      tr()(
        td(cls := "px-2 py-1 font-mono text-sm border-b border-gray-200")(text(name)),
        td(cls := "px-2 py-1 font-mono text-sm border-b border-gray-200")(
          text(Evaluator.getStringValue(value))
        ),
        td(cls := "px-2 py-1 text-xs text-gray-500 border-b border-gray-200")(
          text(value match
            case _: NumberValue => "number"
            case _: TextValue   => "text"
            case _: FuncValue   => "func"
          )
        )
      )
    }
    val procRows = env.procedures.keys.toList.sorted.map { name =>
      tr()(
        td(cls := "px-2 py-1 font-mono text-sm border-b border-gray-200")(text(name)),
        td(cls := "px-2 py-1 font-mono text-sm border-b border-gray-200")(text("…")),
        td(cls := "px-2 py-1 text-xs text-gray-500 border-b border-gray-200")(text("proc"))
      )
    }
    val rows = varRows ++ procRows
    div(cls := "flex flex-col border border-gray-300 rounded-md overflow-hidden")(
      div(cls := "px-3 py-1.5 bg-gray-100 text-xs font-semibold text-gray-600 border-b border-gray-300")(
        text("Environment")
      ),
      div(cls := "max-h-[250px] overflow-auto p-2 bg-white")(
        if rows.isEmpty then div(cls := "text-gray-400 text-sm italic")(text("No bindings"))
        else table(cls := "w-full")(tbody()(rows))
      )
    )

  // ---- Editor ----
  private def viewEditor(model: Model): Html[Msg] =
    Card.simple(Card.Variant.Outlined, Card.Padding.Medium)(
      div(cls := "flex flex-col gap-3")(
        // Saved games list
        viewSavedGames(model),
        // Dialog picker
        div(cls := "flex items-center gap-2 pt-2 border-t border-gray-200")(
          div(cls := "text-sm font-medium text-gray-700")(text("Dialog:")),
          dialogPicker(model),
          Button.primary("+ Dialog", Msg.AddDialog, Button.Size.Small),
          model.selectedDialogId match
            case Some(id) => Button.secondary("Delete", Msg.DeleteDialog(id), Button.Size.Small)
            case None     => div()()
        ),
        model.selectedDialogId.flatMap(model.gameDefinition.dialogs.get) match
          case Some(d) => viewDialogEditor(model, d)
          case None    => div(cls := "text-gray-500 italic")(text("Select or add a dialog"))
      )
    )

  private def viewSavedGames(model: Model): Html[Msg] =
    div(cls := "flex flex-col gap-1")(
      div(cls := "text-sm font-medium text-gray-700")(text("Saved games")),
      if model.savedGames.isEmpty then div(cls := "text-gray-400 text-sm italic")(text("None saved"))
      else
        div(cls := "flex flex-col gap-1 max-h-[120px] overflow-auto")(
          model.savedGames.sortBy(-_.updatedAt).map { g =>
            div(cls := "flex justify-between items-center border rounded px-2 py-1")(
              div(cls := "text-sm truncate")(text(g.title)),
              div(cls := "flex gap-1")(
                Button.secondary("Load", Msg.LoadGame(g.id), Button.Size.Small),
                Button.secondary("✕", Msg.DeleteGame(g.id), Button.Size.Small)
              )
            )
          }
        )
    )

  private def dialogPicker(model: Model): Html[Msg] =
    val ids = model.gameDefinition.dialogs.keys.toList.sorted
    select(
      cls := "px-2 py-1 rounded border border-gray-300 text-sm",
      onChange(Msg.SelectDialog.apply)
    )(
      ids.map { id =>
        if model.selectedDialogId.contains(id) then option(value := id, selected := true)(text(id))
        else option(value := id)(text(id))
      }
    )

  private def viewDialogEditor(model: Model, d: Dialog): Html[Msg] =
    div(cls := "flex flex-col gap-3")(
      labeled("Dialog text")(
        editableField(model, EditTarget.DialogText(d.id))
      ),
      div(cls := "flex justify-between items-center pt-1 border-t border-gray-200")(
        div(cls := "text-sm font-medium text-gray-700")(text("Options")),
        Button.primary("+ Option", Msg.AddOption(d.id), Button.Size.Small)
      ),
      div(cls := "flex flex-col gap-2")(
        d.options.zipWithIndex.map { case (o, i) => viewOptionEditor(model, d.id, o, i, d.options.length) }
      )
    )

  private def viewOptionEditor(
      model: Model,
      dialogId: String,
      o: DialogOption,
      index: Int,
      total: Int
  ): Html[Msg] =
    Card.simple(Card.Variant.Default, Card.Padding.Small)(
      div(cls := "flex flex-col gap-2")(
        div(cls := "flex justify-between items-center")(
          div(cls := "font-mono text-xs text-gray-400")(text(o.id)),
          div(cls := "flex gap-1")(
            Button.secondary("▲", Msg.MoveOption(dialogId, o.id, Dir.Up), Button.Size.Small),
            Button.secondary("▼", Msg.MoveOption(dialogId, o.id, Dir.Down), Button.Size.Small),
            Button.secondary("Remove", Msg.DeleteOption(dialogId, o.id), Button.Size.Small)
          )
        ),
        labeled("Text")(editableField(model, EditTarget.OptionText(dialogId, o.id))),
        labeled("Condition (optional)")(
          editableField(model, EditTarget.OptionCondition(dialogId, o.id))
        ),
        div(cls := "flex flex-col gap-1 pt-1")(
          div(cls := "text-xs font-medium text-gray-500")(text("Actions")),
          viewActions(model, dialogId, o.id, Nil, o.actions)
        )
      )
    )

  private def viewActions(
      model: Model,
      dialogId: String,
      optionId: String,
      path: List[Container],
      actions: List[DialogAction]
  ): Html[Msg] =
    div(cls := "flex flex-col gap-1 pl-2 border-l-2 border-gray-200")(
      (actions.zipWithIndex.map { case (a, idx) =>
        viewAction(model, dialogId, optionId, path, idx, a, actions.length)
      } :+ viewAddActionBar(dialogId, optionId, path))*
    )

  private def viewAddActionBar(dialogId: String, optionId: String, path: List[Container]): Html[Msg] =
    div(cls := "flex flex-wrap gap-1 py-1")(
      ActionKind.values.toList.map { kind =>
        Button.secondary(
          s"+${kindLabel(kind)}",
          Msg.AddAction(dialogId, optionId, path, kind),
          Button.Size.Small
        )
      }
    )

  private def viewAction(
      model: Model,
      dialogId: String,
      optionId: String,
      path: List[Container],
      idx: Int,
      a: DialogAction,
      total: Int
  ): Html[Msg] =
    val controls = div(cls := "flex items-center gap-1")(
      typeSelect(dialogId, optionId, path, idx, a),
      Button.secondary("▲", Msg.MoveAction(dialogId, optionId, path, idx, Dir.Up), Button.Size.Small),
      Button.secondary("▼", Msg.MoveAction(dialogId, optionId, path, idx, Dir.Down), Button.Size.Small),
      Button.secondary("✕", Msg.DeleteAction(dialogId, optionId, path, idx), Button.Size.Small)
    )
    val body = a match
      case _: GoBack => div()()
      case GoDialog(_, dest) =>
        div(cls := "flex items-center gap-2")(
          div(cls := "flex-1")(
            editableField(model, EditTarget.ActionValue(dialogId, optionId, path, idx, ActionField.Destination))
          ),
          if model.gameDefinition.dialogs.contains(dest) && dest.nonEmpty then
            Button.secondary("↗ jump", Msg.JumpToDialog(dest), Button.Size.Small)
          else if dest.nonEmpty then
            Button.secondary("＋ create", Msg.CreateDialogFromDestination(dest), Button.Size.Small)
          else div()()
        )
      case _: MsgAction =>
        editableField(model, EditTarget.ActionValue(dialogId, optionId, path, idx, ActionField.Value))
      case _: ScreeptAction =>
        editableField(model, EditTarget.ActionValue(dialogId, optionId, path, idx, ActionField.Value))
      case Conditional(_, _, thenA, elseA) =>
        div(cls := "flex flex-col gap-1")(
          editableField(model, EditTarget.ActionValue(dialogId, optionId, path, idx, ActionField.Condition)),
          div(cls := "text-xs text-gray-500")(text("then:")),
          viewActions(model, dialogId, optionId, path :+ Container.Then(idx), thenA),
          div(cls := "text-xs text-gray-500")(text("else:")),
          viewActions(model, dialogId, optionId, path :+ Container.Else(idx), elseA)
        )
      case BlockAction(_, inner) =>
        div(cls := "flex flex-col gap-1")(
          viewActions(model, dialogId, optionId, path :+ Container.Block(idx), inner)
        )
    div(cls := "flex flex-col gap-1 border border-gray-200 rounded p-2")(controls, body)

  private def typeSelect(
      dialogId: String,
      optionId: String,
      path: List[Container],
      idx: Int,
      a: DialogAction
  ): Html[Msg] =
    val current = kindOf(a)
    select(
      cls := "px-1 py-0.5 rounded border border-gray-300 text-xs font-mono",
      onChange(s => Msg.ChangeActionType(dialogId, optionId, path, idx, parseKind(s)))
    )(
      ActionKind.values.toList.map { k =>
        if k == current then option(value := kindLabel(k), selected := true)(text(kindLabel(k)))
        else option(value := kindLabel(k))(text(kindLabel(k)))
      }
    )

  /** Inline click-to-edit field: shows the current Screept source; a textarea + Save/Cancel while
    * this exact target is active.
    */
  private def editableField(model: Model, target: EditTarget): Html[Msg] =
    if model.edit.contains(target) then
      div(cls := "flex flex-col gap-1")(
        textarea(
          cls := "w-full font-mono text-sm p-2 border border-blue-400 rounded resize-y focus:outline-none focus:ring-1 focus:ring-blue-500",
          rows := "2",
          onInput(Msg.UpdateBuffer.apply),
          value := model.editBuffer
        )(),
        div(cls := "flex gap-1")(
          Button.primary("Save", Msg.SaveEdit, Button.Size.Small),
          Button.secondary("Cancel", Msg.CancelEdit, Button.Size.Small)
        ),
        model.editError match
          case Some(err) => div(cls := "text-red-600 text-xs")(text(err))
          case None      => div()()
      )
    else
      val display = currentSource(model.gameDefinition, target)
      val shown   = if display.trim.isEmpty then "(empty — click to edit)" else display
      div(
        cls := "font-mono text-sm px-2 py-1 rounded border border-gray-200 bg-gray-50 cursor-pointer hover:bg-gray-100 whitespace-pre-wrap",
        onClick(Msg.StartEdit(target))
      )(text(shown))

  private def labeled(label: String)(control: Html[Msg]): Html[Msg] =
    div(cls := "flex flex-col gap-1")(
      div(cls := "text-xs font-medium text-gray-500")(text(label)),
      control
    )

  private def viewImportExportModal(model: Model): Html[Msg] =
    Modal.withTitle(
      model.importExportVisible,
      Msg.HideImportExport,
      "Import / Export",
      Modal.Size.Large
    )(
      div(cls := "flex flex-col gap-6")(
        Card.simple(Card.Variant.Outlined, Card.Padding.Medium)(
          div(cls := "flex flex-col gap-3")(
            div(cls := "text-lg font-semibold text-gray-800")(text("Export")),
            Button.primary("Download dialog-game.json", Msg.ExportJson, Button.Size.Medium)
          )
        ),
        Card.simple(Card.Variant.Outlined, Card.Padding.Medium)(
          div(cls := "flex flex-col gap-3")(
            div(cls := "text-lg font-semibold text-gray-800")(text("Import (paste GameDefinition JSON)")),
            textarea(
              cls := "w-full h-40 font-mono text-xs p-2 border border-gray-300 rounded resize-y",
              placeholder := "Paste JSON here…",
              onInput(Msg.SetImportText.apply),
              value := model.importText
            )(),
            Button.primary("Import JSON", Msg.ImportJson, Button.Size.Medium)
          )
        ),
        div(cls := "flex justify-end pt-2 border-t border-gray-200")(
          Button.secondary("Close", Msg.HideImportExport, Button.Size.Medium)
        )
      )
    )

  def subscriptions(model: Model): Sub[IO, Msg] = Sub.None

  // ============ SAMPLE GAME ============

  private def sampleGameDefinition: GameDefinition =
    def t(s: String): Expression          = Screept.litText(s)
    def n(v: Int): Expression             = Screept.litNum(v)
    val gold                              = Screept.varRef("gold")
    def add(a: Expression, b: Expression) = BinaryOp(BinaryOperator.Add, a, b)
    def gt(a: Expression, b: Expression)  = BinaryOp(BinaryOperator.Gt, a, b)
    def setGold(expr: Expression): Statement = Bind(LiteralId("gold"), expr)

    val start = Dialog(
      id = "start",
      text = add(add(t("You stand at a crossroads. Your purse holds "), gold), t(" gold.")),
      options = List(
        DialogOption(
          "start-search",
          t("Search the roadside for coins"),
          None,
          List(
            ScreeptAction("a-search-1", setGold(add(gold, n(10)))),
            MsgAction("a-search-2", t("You rummage around and find 10 gold!"))
          )
        ),
        DialogOption(
          "start-shop",
          t("Visit the shop"),
          Some(gt(gold, n(0))),
          List(GoDialog("a-shop", "shop"))
        ),
        DialogOption("start-rest", t("Rest under a tree"), None, List(GoDialog("a-rest", "rest")))
      )
    )

    val shop = Dialog(
      id = "shop",
      text = add(add(t("The shopkeeper glances at your "), gold), t(" gold.")),
      options = List(
        DialogOption(
          "shop-buy",
          t("Buy a torch (10 gold)"),
          Some(gt(gold, n(9))),
          List(
            ScreeptAction("a-buy-1", setGold(BinaryOp(BinaryOperator.Sub, gold, n(10)))),
            MsgAction("a-buy-2", t("You buy a sturdy torch."))
          )
        ),
        DialogOption("shop-leave", t("Leave the shop"), None, List(GoBack("a-leave")))
      )
    )

    val rest = Dialog(
      id = "rest",
      text = t("You rest for a while and feel refreshed."),
      options = List(
        DialogOption("rest-wake", t("Get back on the road"), None, List(GoBack("a-wake")))
      )
    )

    GameDefinition(
      dialogs = Map("start" -> start, "shop" -> shop, "rest" -> rest),
      gameState = GameState(
        dialogStack = List("start"),
        screeptEnv = Environment(vars = Map("gold" -> NumberValue(0)))
      )
    )
}
