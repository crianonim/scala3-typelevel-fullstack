package com.crianonim.dialog

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
import scala.scalajs.js

import com.crianonim.ui.*
import com.crianonim.screept.*

/** Tyrian front-end for the ported dialog library: a player (walk a dialog game) and an editor
  * (author dialogs, options and action trees), with JSON import/export of a [[GameDefinition]].
  *
  * Screept evaluation is effectful, so the current dialog's text and visible options are resolved
  * to plain strings in [[update]] (via `Cmd.Run` with a `Random`) and stored in the model for
  * [[view]] to render — `view` never runs `IO`.
  */
object DialogApp {

  enum Mode {
    case Play, Edit
  }

  // Resolved (evaluated) view data produced in `update` for `view` to render.
  case class ResolvedOption(optionId: String, text: String)
  case class ResolvedDialog(dialogId: String, text: String, options: List[ResolvedOption])

  // ---- Editor drafts: mirror the AST but hold Screept *source* strings, parsed on Save ----
  enum ActionDraft {
    case GoBackD(id: String)
    case GoDialogD(id: String, destination: String)
    case MsgD(id: String, valueSrc: String)
    case ScreeptD(id: String, valueSrc: String)
    case ConditionalD(
        id: String,
        condSrc: String,
        thenA: List[ActionDraft],
        elseA: List[ActionDraft]
    )
    case BlockD(id: String, actions: List[ActionDraft])
  }

  case class OptionDraft(
      id: String,
      textSrc: String,
      conditionSrc: String,
      actions: List[ActionDraft]
  )
  case class DialogDraft(id: String, textSrc: String, options: List[OptionDraft])

  enum ActionKind {
    case GoBack, GoDialog, Msg, Screept, Conditional, Block
  }
  enum ActionField {
    case Destination, Value, Condition
  }

  /** A step descending into a nested action list; a `List[Container]` addresses an action list,
    * within which an index identifies the target action.
    */
  enum Container {
    case Then(index: Int)
    case Else(index: Int)
    case Block(index: Int)
  }

  case class Model(
      gameDefinition: GameDefinition,
      initialGameState: GameState,
      mode: Mode,
      resolved: Option[ResolvedDialog],
      runError: Option[String],
      draft: Option[DialogDraft],
      editError: Option[String],
      importExportVisible: Boolean,
      importError: Option[String]
  )

  enum Msg {
    // Play
    case SelectOption(optionId: String)
    case Restart
    // Common
    case SwitchMode(mode: Mode)
    case Rendered(state: GameState, resolved: ResolvedDialog)
    case RunFailed(error: String)
    // Editor
    case SelectDialogForEdit(dialogId: String)
    case AddDialog
    case DeleteDialog(dialogId: String)
    case UpdateDraftDialogText(src: String)
    case AddOption
    case DeleteOption(optionId: String)
    case UpdateOptionText(optionId: String, src: String)
    case UpdateOptionCondition(optionId: String, src: String)
    case AddAction(optionId: String, path: List[Container], kind: ActionKind)
    case DeleteAction(optionId: String, path: List[Container], index: Int)
    case UpdateActionField(
        optionId: String,
        path: List[Container],
        index: Int,
        field: ActionField,
        value: String
    )
    case SaveDialog
    // Import / Export
    case ShowImportExport
    case HideImportExport
    case Export
    case FileSelected(file: dom.File)
    case FileContentLoaded(content: String)
    case ClearImportError
  }

  def init: Model = Model(
    gameDefinition = sampleGameDefinition,
    initialGameState = sampleGameDefinition.gameState,
    mode = Mode.Play,
    resolved = None,
    runError = None,
    draft = None,
    editError = None,
    importExportVisible = false,
    importError = None
  )

  /** Cmd to resolve the initial dialog; wired from `App.init`. */
  def initCmd: Cmd[IO, Msg] = resolve(sampleGameDefinition)

  // ============ RESOLUTION (effectful) ============

  private def resolveProgram[F[_]: Monad: Random](
      dialogs: Dialogs,
      state: GameState
  ): EitherT[F, EvaluationError, ResolvedDialog] =
    state.dialogStack.headOption.flatMap(dialogs.get) match
      case None =>
        EitherT.leftT[F, ResolvedDialog](OtherError("No current dialog on the stack"))
      case Some(dialog) =>
        for
          visible <- DialogEngine.getVisibleOptions[F](dialog.options, state.screeptEnv)
          dtext   <- EitherT(Screept.eval[F](dialog.text, state.screeptEnv))
          opts <- visible.traverse { o =>
            EitherT(Screept.eval[F](o.text, state.screeptEnv))
              .map(v => ResolvedOption(o.id, Evaluator.getStringValue(v)))
          }
        yield ResolvedDialog(dialog.id, Evaluator.getStringValue(dtext), opts)

  private def resolve(gd: GameDefinition): Cmd[IO, Msg] =
    Cmd.Run {
      Random.scalaUtilRandom[IO].flatMap { implicit r =>
        resolveProgram[IO](gd.dialogs, gd.gameState).value
      }
    } {
      case Right(rd)  => Msg.Rendered(gd.gameState, rd)
      case Left(err)  => Msg.RunFailed(EvaluationError.show(err))
    }

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

    case Msg.Restart =>
      val gd = model.gameDefinition.copy(gameState = model.initialGameState)
      (model.copy(gameDefinition = gd, runError = None), resolve(gd))

    case Msg.SwitchMode(mode) =>
      val m = model.copy(mode = mode)
      if mode == Mode.Play then (m.copy(runError = None), resolve(model.gameDefinition))
      else (m, Cmd.None)

    case Msg.Rendered(state, rd) =>
      (
        model.copy(
          gameDefinition = model.gameDefinition.copy(gameState = state),
          resolved = Some(rd),
          runError = None
        ),
        Cmd.None
      )

    case Msg.RunFailed(err) =>
      (model.copy(runError = Some(err)), Cmd.None)

    // ---- Editor ----
    case Msg.SelectDialogForEdit(dialogId) =>
      model.gameDefinition.dialogs.get(dialogId) match
        case Some(d) => (model.copy(draft = Some(toDraft(d)), editError = None), Cmd.None)
        case None    => (model, Cmd.None)

    case Msg.AddDialog =>
      val d  = DialogEngine.generateNewDialog()
      val gd = model.gameDefinition.copy(dialogs = model.gameDefinition.dialogs.updated(d.id, d))
      (model.copy(gameDefinition = gd, draft = Some(toDraft(d)), editError = None), Cmd.None)

    case Msg.DeleteDialog(dialogId) =>
      val gd = model.gameDefinition.copy(dialogs = model.gameDefinition.dialogs - dialogId)
      val newDraft = model.draft.filterNot(_.id == dialogId)
      (model.copy(gameDefinition = gd, draft = newDraft), Cmd.None)

    case Msg.UpdateDraftDialogText(src) =>
      (updateDraft(model)(_.copy(textSrc = src)), Cmd.None)

    case Msg.AddOption =>
      val opt = OptionDraft(genId("option"), "\"New option\"", "", List.empty)
      (updateDraft(model)(d => d.copy(options = d.options :+ opt)), Cmd.None)

    case Msg.DeleteOption(optionId) =>
      (updateDraft(model)(d => d.copy(options = d.options.filterNot(_.id == optionId))), Cmd.None)

    case Msg.UpdateOptionText(optionId, src) =>
      (updateOption(model, optionId)(_.copy(textSrc = src)), Cmd.None)

    case Msg.UpdateOptionCondition(optionId, src) =>
      (updateOption(model, optionId)(_.copy(conditionSrc = src)), Cmd.None)

    case Msg.AddAction(optionId, path, kind) =>
      (
        updateOption(model, optionId)(o =>
          o.copy(actions = modifyActionList(o.actions, path)(_ :+ newActionDraft(kind)))
        ),
        Cmd.None
      )

    case Msg.DeleteAction(optionId, path, index) =>
      (
        updateOption(model, optionId)(o =>
          o.copy(actions = modifyActionList(o.actions, path)(_.patch(index, Nil, 1)))
        ),
        Cmd.None
      )

    case Msg.UpdateActionField(optionId, path, index, field, value) =>
      (
        updateOption(model, optionId)(o =>
          o.copy(actions =
            modifyActionList(o.actions, path)(list =>
              if index < 0 || index >= list.length then list
              else list.updated(index, setActionField(list(index), field, value))
            )
          )
        ),
        Cmd.None
      )

    case Msg.SaveDialog =>
      model.draft match
        case None => (model, Cmd.None)
        case Some(d) =>
          fromDraft(d) match
            case Right(dialog) =>
              val gd = model.gameDefinition.copy(
                dialogs = DialogEngine.updateDialog(model.gameDefinition.dialogs, dialog)
              )
              (model.copy(gameDefinition = gd, editError = None), Cmd.None)
            case Left(err) =>
              (model.copy(editError = Some(err)), Cmd.None)

    // ---- Import / Export ----
    case Msg.ShowImportExport =>
      (model.copy(importExportVisible = true, importError = None), Cmd.None)

    case Msg.HideImportExport =>
      (model.copy(importExportVisible = false, importError = None), Cmd.None)

    case Msg.Export =>
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

    case Msg.FileSelected(file) =>
      (model, FileInput.readFileCmd(file)(Msg.FileContentLoaded.apply))

    case Msg.FileContentLoaded(content) =>
      if content.isEmpty then (model.copy(importError = Some("Failed to read file")), Cmd.None)
      else
        decode[GameDefinition](content) match
          case Right(gd) =>
            (
              model.copy(
                gameDefinition = gd,
                initialGameState = gd.gameState,
                importExportVisible = false,
                importError = None,
                draft = None,
                mode = Mode.Play
              ),
              resolve(gd)
            )
          case Left(error) =>
            (model.copy(importError = Some(s"Invalid JSON: ${error.getMessage}")), Cmd.None)

    case Msg.ClearImportError =>
      (model.copy(importError = None), Cmd.None)
  }

  // ============ EDITOR STATE HELPERS ============

  private def genId(prefix: String): String =
    s"$prefix-${System.currentTimeMillis()}-${scala.util.Random.nextInt(100000)}"

  private def updateDraft(model: Model)(f: DialogDraft => DialogDraft): Model =
    model.copy(draft = model.draft.map(f))

  private def updateOption(model: Model, optionId: String)(f: OptionDraft => OptionDraft): Model =
    updateDraft(model)(d =>
      d.copy(options = d.options.map(o => if o.id == optionId then f(o) else o))
    )

  private def newActionDraft(kind: ActionKind): ActionDraft = kind match
    case ActionKind.GoBack      => ActionDraft.GoBackD(genId("action"))
    case ActionKind.GoDialog    => ActionDraft.GoDialogD(genId("action"), "")
    case ActionKind.Msg         => ActionDraft.MsgD(genId("action"), "\"\"")
    case ActionKind.Screept     => ActionDraft.ScreeptD(genId("action"), "")
    case ActionKind.Conditional => ActionDraft.ConditionalD(genId("action"), "1", Nil, Nil)
    case ActionKind.Block       => ActionDraft.BlockD(genId("action"), Nil)

  private def setActionField(a: ActionDraft, field: ActionField, value: String): ActionDraft =
    (a, field) match
      case (d: ActionDraft.GoDialogD, ActionField.Destination) => d.copy(destination = value)
      case (d: ActionDraft.MsgD, ActionField.Value)            => d.copy(valueSrc = value)
      case (d: ActionDraft.ScreeptD, ActionField.Value)        => d.copy(valueSrc = value)
      case (d: ActionDraft.ConditionalD, ActionField.Condition) => d.copy(condSrc = value)
      case (other, _)                                          => other

  /** Apply `f` to the action list addressed by `path` (empty path = the root list). */
  private def modifyActionList(root: List[ActionDraft], path: List[Container])(
      f: List[ActionDraft] => List[ActionDraft]
  ): List[ActionDraft] =
    path match
      case Nil => f(root)
      case step :: rest =>
        root.zipWithIndex.map { case (a, idx) =>
          step match
            case Container.Then(i) if i == idx =>
              a match
                case c: ActionDraft.ConditionalD => c.copy(thenA = modifyActionList(c.thenA, rest)(f))
                case other                       => other
            case Container.Else(i) if i == idx =>
              a match
                case c: ActionDraft.ConditionalD => c.copy(elseA = modifyActionList(c.elseA, rest)(f))
                case other                       => other
            case Container.Block(i) if i == idx =>
              a match
                case b: ActionDraft.BlockD => b.copy(actions = modifyActionList(b.actions, rest)(f))
                case other                 => other
            case _ => a
        }

  // ============ DRAFT <-> AST ============

  private def toDraft(d: Dialog): DialogDraft =
    DialogDraft(d.id, ScreeptPrinter.expr(d.text), d.options.map(toOptionDraft))

  private def toOptionDraft(o: DialogOption): OptionDraft =
    OptionDraft(
      o.id,
      ScreeptPrinter.expr(o.text),
      o.condition.map(ScreeptPrinter.expr).getOrElse(""),
      o.actions.map(toActionDraft)
    )

  private def toActionDraft(a: DialogAction): ActionDraft = a match
    case GoBack(id)            => ActionDraft.GoBackD(id)
    case GoDialog(id, dest)    => ActionDraft.GoDialogD(id, dest)
    case MsgAction(id, v)      => ActionDraft.MsgD(id, ScreeptPrinter.expr(v))
    case ScreeptAction(id, v)  => ActionDraft.ScreeptD(id, ScreeptPrinter.stmt(v))
    case Conditional(id, c, t, e) =>
      ActionDraft.ConditionalD(id, ScreeptPrinter.expr(c), t.map(toActionDraft), e.map(toActionDraft))
    case BlockAction(id, as) => ActionDraft.BlockD(id, as.map(toActionDraft))

  private def fromDraft(d: DialogDraft): Either[String, Dialog] =
    for
      text <- Screept.parseExpr(d.textSrc).left.map(e => s"Dialog text: $e")
      opts <- d.options.traverse(fromOptionDraft)
    yield Dialog(d.id, text, opts)

  private def fromOptionDraft(o: OptionDraft): Either[String, DialogOption] =
    for
      text <- Screept.parseExpr(o.textSrc).left.map(e => s"Option text: $e")
      cond <-
        if o.conditionSrc.trim.isEmpty then Right(None)
        else Screept.parseExpr(o.conditionSrc).map(Some(_)).left.map(e => s"Condition: $e")
      acts <- o.actions.traverse(fromActionDraft)
    yield DialogOption(o.id, text, cond, acts)

  private def fromActionDraft(a: ActionDraft): Either[String, DialogAction] = a match
    case ActionDraft.GoBackD(id)         => Right(GoBack(id))
    case ActionDraft.GoDialogD(id, dest) => Right(GoDialog(id, dest))
    case ActionDraft.MsgD(id, src) =>
      Screept.parseExpr(src).map(MsgAction(id, _)).left.map(e => s"msg: $e")
    case ActionDraft.ScreeptD(id, src) =>
      Screept.parse(src).map(ScreeptAction(id, _)).left.map(e => s"screept: $e")
    case ActionDraft.ConditionalD(id, c, t, e) =>
      for
        cc <- Screept.parseExpr(c).left.map(err => s"if: $err")
        tt <- t.traverse(fromActionDraft)
        ee <- e.traverse(fromActionDraft)
      yield Conditional(id, cc, tt, ee)
    case ActionDraft.BlockD(id, as) =>
      as.traverse(fromActionDraft).map(BlockAction(id, _))

  // ============ VIEW ============

  def view(model: Model): Html[Msg] =
    div(cls := "flex flex-col gap-4")(
      viewHeader(model),
      model.mode match
        case Mode.Play => viewPlay(model)
        case Mode.Edit => viewEdit(model)
      ,
      viewImportExportModal(model)
    )

  private def viewHeader(model: Model): Html[Msg] =
    val playBtn =
      if model.mode == Mode.Play then Button.primary("Play", Msg.SwitchMode(Mode.Play), Button.Size.Small)
      else Button.secondary("Play", Msg.SwitchMode(Mode.Play), Button.Size.Small)
    val editBtn =
      if model.mode == Mode.Edit then Button.primary("Edit", Msg.SwitchMode(Mode.Edit), Button.Size.Small)
      else Button.secondary("Edit", Msg.SwitchMode(Mode.Edit), Button.Size.Small)
    div(cls := "flex justify-between items-center")(
      div(cls := "flex items-center gap-2")(
        div(cls := "text-lg font-semibold text-gray-700")(text("Dialog")),
        playBtn,
        editBtn
      ),
      div(cls := "flex gap-2")(
        Button.secondary("Restart", Msg.Restart, Button.Size.Small),
        Button.secondary("Import/Export", Msg.ShowImportExport, Button.Size.Small)
      )
    )

  // ---- Play ----
  private def viewPlay(model: Model): Html[Msg] =
    div(cls := "flex gap-4")(
      div(cls := "flex flex-col gap-4 w-2/3")(
        model.runError match
          case Some(err) =>
            Card.simple(Card.Variant.Outlined, Card.Padding.Medium)(
              div(cls := "text-red-600")(text(s"Error: $err"))
            )
          case None => div()()
        ,
        model.resolved match
          case None =>
            Card.simple(Card.Variant.Elevated, Card.Padding.Large)(
              div(cls := "text-gray-400 italic")(text("Loading…"))
            )
          case Some(rd) =>
            Card.simple(Card.Variant.Elevated, Card.Padding.Large)(
              div(cls := "flex flex-col gap-4")(
                div(cls := "text-gray-900 whitespace-pre-wrap")(text(rd.text)),
                div(cls := "flex flex-col gap-2")(
                  if rd.options.isEmpty then
                    List(div(cls := "text-gray-400 italic")(text("No available options")))
                  else
                    rd.options.map(o =>
                      div()(Button.primary(o.text, Msg.SelectOption(o.optionId), Button.Size.Medium))
                    )
                )
              )
            )
      ),
      // Output log
      div(cls := "w-1/3 flex flex-col border border-gray-300 rounded-md overflow-hidden max-h-[70vh]")(
        div(cls := "px-3 py-1.5 bg-gray-100 text-xs font-semibold text-gray-600 border-b border-gray-300")(
          text("Log")
        ),
        viewLog(model)
      )
    )

  private def viewLog(model: Model): Html[Msg] =
    val out = model.gameDefinition.gameState.screeptEnv.output
    val children: List[Html[Msg]] =
      if out.isEmpty then List(div(cls := "text-gray-400 italic")(text("No messages yet")))
      else out.map(line => div()(text(line.value)))
    div(cls := "flex-1 overflow-auto p-3 font-mono text-sm bg-white")(children)

  // ---- Edit ----
  private def viewEdit(model: Model): Html[Msg] =
    div(cls := "flex gap-4")(
      // Left: dialog list
      div(cls := "w-1/4 flex flex-col gap-2")(
        div(cls := "flex justify-between items-center")(
          div(cls := "font-medium text-gray-700")(text("Dialogs")),
          Button.primary("+ Add", Msg.AddDialog, Button.Size.Small)
        ),
        div(cls := "flex flex-col border rounded max-h-[70vh] overflow-y-auto")(
          model.gameDefinition.dialogs.keys.toList.sorted.map { id =>
            val selected = model.draft.exists(_.id == id)
            val itemCls =
              if selected then "flex justify-between items-center p-2 bg-blue-100 border-b cursor-pointer"
              else "flex justify-between items-center p-2 hover:bg-gray-50 border-b cursor-pointer"
            div(cls := itemCls, onClick(Msg.SelectDialogForEdit(id)))(
              div(cls := "font-mono text-sm truncate")(text(id)),
              Button.secondary("✕", Msg.DeleteDialog(id), Button.Size.Small)
            )
          }
        )
      ),
      // Right: selected dialog editor
      div(cls := "w-3/4")(
        model.draft match
          case None =>
            div(cls := "text-gray-500 italic p-4")(text("Select or add a dialog to edit"))
          case Some(d) => viewDialogEditor(model, d)
      )
    )

  private def viewDialogEditor(model: Model, d: DialogDraft): Html[Msg] =
    Card.simple(Card.Variant.Outlined, Card.Padding.Medium)(
      div(cls := "flex flex-col gap-4")(
        div(cls := "flex justify-between items-center")(
          div(cls := "font-mono text-sm text-gray-500")(text(d.id)),
          Button.primary("Save", Msg.SaveDialog, Button.Size.Small)
        ),
        model.editError match
          case Some(err) =>
            div(cls := "bg-red-50 border border-red-200 rounded-md p-2 text-red-700 text-sm")(
              text(err)
            )
          case None => div()()
        ,
        labeled("Dialog text (Screept expression)")(
          Input.interactive(d.textSrc, Msg.UpdateDraftDialogText.apply)
        ),
        div(cls := "flex justify-between items-center pt-2 border-t border-gray-200")(
          div(cls := "font-medium text-gray-700")(text("Options")),
          Button.primary("+ Add Option", Msg.AddOption, Button.Size.Small)
        ),
        div(cls := "flex flex-col gap-3")(
          d.options.map(o => viewOptionEditor(o))
        )
      )
    )

  private def viewOptionEditor(o: OptionDraft): Html[Msg] =
    Card.simple(Card.Variant.Default, Card.Padding.Small)(
      div(cls := "flex flex-col gap-2")(
        div(cls := "flex justify-between items-center")(
          div(cls := "font-mono text-xs text-gray-400")(text(o.id)),
          Button.secondary("Remove option", Msg.DeleteOption(o.id), Button.Size.Small)
        ),
        labeled("Text")(
          Input.interactive(o.textSrc, s => Msg.UpdateOptionText(o.id, s))
        ),
        labeled("Condition (optional, Screept expression)")(
          Input.interactive(o.conditionSrc, s => Msg.UpdateOptionCondition(o.id, s))
        ),
        div(cls := "flex flex-col gap-1 pt-1")(
          div(cls := "text-xs font-medium text-gray-500")(text("Actions")),
          viewActions(o.id, Nil, o.actions)
        )
      )
    )

  /** Recursive editor for an action list at `path` within option `optionId`. */
  private def viewActions(
      optionId: String,
      path: List[Container],
      actions: List[ActionDraft]
  ): Html[Msg] =
    div(cls := "flex flex-col gap-1 pl-2 border-l-2 border-gray-200")(
      (actions.zipWithIndex.map { case (a, idx) =>
        viewAction(optionId, path, idx, a)
      } :+ viewAddActionBar(optionId, path))*
    )

  private def viewAddActionBar(optionId: String, path: List[Container]): Html[Msg] =
    div(cls := "flex flex-wrap gap-1 py-1")(
      List(
        "+back"    -> ActionKind.GoBack,
        "+go"      -> ActionKind.GoDialog,
        "+msg"     -> ActionKind.Msg,
        "+screept" -> ActionKind.Screept,
        "+if"      -> ActionKind.Conditional,
        "+block"   -> ActionKind.Block
      ).map { case (label, kind) =>
        Button.secondary(label, Msg.AddAction(optionId, path, kind), Button.Size.Small)
      }
    )

  private def viewAction(
      optionId: String,
      path: List[Container],
      idx: Int,
      a: ActionDraft
  ): Html[Msg] =
    val delete = Button.secondary("✕", Msg.DeleteAction(optionId, path, idx), Button.Size.Small)
    def tag_(label: String): Html[Msg] =
      div(cls := "text-xs font-mono font-semibold text-gray-600 w-16 shrink-0")(text(label))
    def field(placeholderLabel: String, value: String, fld: ActionField): Html[Msg] =
      div(cls := "flex-1")(
        Input.interactive(value, s => Msg.UpdateActionField(optionId, path, idx, fld, s))
      )
    a match
      case ActionDraft.GoBackD(_) =>
        div(cls := "flex items-center gap-2")(tag_("go back"), div(cls := "flex-1")(), delete)
      case ActionDraft.GoDialogD(_, dest) =>
        div(cls := "flex items-center gap-2")(
          tag_("go →"),
          field("destination dialog id", dest, ActionField.Destination),
          delete
        )
      case ActionDraft.MsgD(_, v) =>
        div(cls := "flex items-center gap-2")(tag_("msg"), field("expression", v, ActionField.Value), delete)
      case ActionDraft.ScreeptD(_, v) =>
        div(cls := "flex items-center gap-2")(tag_("screept"), field("statement", v, ActionField.Value), delete)
      case ActionDraft.ConditionalD(_, cond, thenA, elseA) =>
        div(cls := "flex flex-col gap-1 border border-gray-200 rounded p-2")(
          div(cls := "flex items-center gap-2")(
            tag_("if"),
            field("condition", cond, ActionField.Condition),
            delete
          ),
          div(cls := "text-xs text-gray-500 pl-2")(text("then:")),
          viewActions(optionId, path :+ Container.Then(idx), thenA),
          div(cls := "text-xs text-gray-500 pl-2")(text("else:")),
          viewActions(optionId, path :+ Container.Else(idx), elseA)
        )
      case ActionDraft.BlockD(_, actions) =>
        div(cls := "flex flex-col gap-1 border border-gray-200 rounded p-2")(
          div(cls := "flex items-center gap-2")(tag_("block"), div(cls := "flex-1")(), delete),
          viewActions(optionId, path :+ Container.Block(idx), actions)
        )

  private def labeled(label: String)(control: Html[Msg]): Html[Msg] =
    div(cls := "flex flex-col gap-1")(
      div(cls := "text-xs font-medium text-gray-500")(text(label)),
      control
    )

  private def viewImportExportModal(model: Model): Html[Msg] =
    Modal.withTitle(
      model.importExportVisible,
      Msg.HideImportExport,
      "Import / Export Dialog Game",
      Modal.Size.Large
    )(
      div(cls := "flex flex-col gap-6")(
        Card.simple(Card.Variant.Outlined, Card.Padding.Medium)(
          div(cls := "flex flex-col gap-3")(
            div(cls := "text-lg font-semibold text-gray-800")(text("Export")),
            div(cls := "text-sm text-gray-600")(
              text(s"Download the current game (${model.gameDefinition.dialogs.size} dialogs) as JSON")
            ),
            Button.primary("Download dialog-game.json", Msg.Export, Button.Size.Medium)
          )
        ),
        Card.simple(Card.Variant.Outlined, Card.Padding.Medium)(
          div(cls := "flex flex-col gap-3")(
            div(cls := "text-lg font-semibold text-gray-800")(text("Import")),
            div(cls := "text-sm text-orange-600 font-medium")(
              text("Warning: this replaces the current game.")
            ),
            FileInput(
              Msg.FileSelected.apply,
              accept = ".json,application/json",
              label = Some("Choose JSON file")
            ),
            model.importError match
              case Some(err) =>
                div(cls := "bg-red-50 border border-red-200 rounded-md p-3")(
                  div(cls := "text-red-700 text-sm")(text(err)),
                  div(cls := "mt-2")(
                    Button.secondary("Clear Error", Msg.ClearImportError, Button.Size.Small)
                  )
                )
              case None => div()()
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
    def t(s: String): Expression        = Screept.litText(s)
    def n(v: Int): Expression           = Screept.litNum(v)
    val gold                            = Screept.varRef("gold")
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
        DialogOption(
          "start-rest",
          t("Rest under a tree"),
          None,
          List(GoDialog("a-rest", "rest"))
        )
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
