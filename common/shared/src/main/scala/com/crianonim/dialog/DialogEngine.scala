package com.crianonim.dialog

import cats.Monad
import cats.data.EitherT
import cats.effect.std.Random
import cats.syntax.all.*

import com.crianonim.screept.{
  Bind,
  Environment,
  EvaluationError,
  Evaluator,
  Expression,
  FunCall,
  Literal,
  LiteralId,
  NumberValue,
  Screept,
  Statement
}

/** Scala port of the `@crianonim/dialog` library engine.
  *
  * The core divergence from the TypeScript original is that Screept evaluation is effectful here
  * (`F[_]: Monad: Random`), so every function that evaluates a dialog/option text, a condition, or
  * runs an action is expressed as `EitherT[F, EvaluationError, _]`. Pure navigation/lookup and the
  * editor mutation helpers stay pure.
  */
object DialogEngine:

  // ============ PURE LOOKUP / NAVIGATION ============

  def getDialogFromStack(gd: GameDefinition): Option[Dialog] =
    gd.gameState.dialogStack.headOption.flatMap(gd.dialogs.get)

  def getDialogById(dialogs: Dialogs, id: String): Option[Dialog] =
    dialogs.get(id)

  // ============ EFFECTFUL REDUCER ============

  /** Execute a single action against the game state, mirroring the TS `executeAction`. */
  def executeAction[F[_]: Monad: Random](
      state: GameState,
      action: DialogAction
  ): EitherT[F, EvaluationError, GameState] =
    action match
      case GoDialog(_, destination) =>
        EitherT.pure(state.copy(dialogStack = destination :: state.dialogStack))

      case GoBack(_) =>
        EitherT.pure(state.copy(dialogStack = state.dialogStack.drop(1)))

      case BlockAction(_, actions) =>
        gameStateReducer(state, actions)

      case Conditional(_, cond, thenActions, elseActions) =>
        EitherT(Screept.eval[F](cond, state.screeptEnv)).flatMap { value =>
          if Evaluator.isTruthy(value) then gameStateReducer(state, thenActions)
          else gameStateReducer(state, elseActions)
        }

      case ScreeptAction(_, stmt) =>
        EitherT(Screept.run[F](stmt, state.screeptEnv)).map { env =>
          state.copy(screeptEnv = env)
        }

      case MsgAction(_, value) =>
        EitherT(Screept.eval[F](value, state.screeptEnv)).map { v =>
          val line = Evaluator.createOutputLine(Evaluator.getStringValue(v))
          state.copy(screeptEnv =
            state.screeptEnv.copy(output = state.screeptEnv.output :+ line)
          )
        }

  /** Fold a list of actions, threading the game state through each (mirrors `gameStateReducer`). */
  def gameStateReducer[F[_]: Monad: Random](
      state: GameState,
      actions: List[DialogAction]
  ): EitherT[F, EvaluationError, GameState] =
    actions.foldLeftM(state)((s, a) => executeAction(s, a))

  /** Keep only options with no condition, or whose condition evaluates truthy.
    *
    * Mirrors the TS `getVisibleOptions`: when the environment defines `_specialOption` with a
    * value other than `"0"`, a synthetic "Menu" option is appended whose actions clear
    * `_specialOption` and jump to the dialog named by its value.
    */
  def getVisibleOptions[F[_]: Monad: Random](
      options: List[DialogOption],
      env: Environment
  ): EitherT[F, EvaluationError, List[DialogOption]] =
    val withSpecial = specialOption(env).fold(options)(options :+ _)
    withSpecial.filterA { opt =>
      opt.condition match
        case None       => EitherT.pure(true)
        case Some(cond) => EitherT(Screept.eval[F](cond, env)).map(Evaluator.isTruthy)
    }

  private def specialOption(env: Environment): Option[DialogOption] =
    env.vars.get("_specialOption").map(Evaluator.getStringValue).filter(_ != "0").map { destination =>
      DialogOption(
        id = s"special-${System.currentTimeMillis()}",
        text = Screept.litText("Menu"),
        condition = None,
        actions = List(
          ScreeptAction(
            s"special-clear-${System.currentTimeMillis()}",
            Bind(LiteralId("_specialOption"), Literal(NumberValue(0)))
          ),
          GoDialog(s"special-go-${System.currentTimeMillis()}", destination)
        )
      )
    }

  /** If the environment defines a `__statusLine` procedure/function, evaluate `__statusLine()` and
    * return its string value; otherwise `None`. Mirrors the TS `getStatusLine`.
    */
  def getStatusLine[F[_]: Monad: Random](
      env: Environment
  ): EitherT[F, EvaluationError, Option[String]] =
    if env.vars.contains("__statusLine") then
      EitherT(Screept.eval[F](FunCall(LiteralId("__statusLine"), Nil), env))
        .map(v => Some(Evaluator.getStringValue(v)))
    else EitherT.pure(None)

  /** Evaluate an expression to a string and split it on the `<nl>` marker (mirrors TS). */
  def getSplitStringOnNL[F[_]: Monad: Random](
      expr: Expression,
      env: Environment
  ): EitherT[F, EvaluationError, List[String]] =
    EitherT(Screept.eval[F](expr, env)).map(v => Evaluator.getStringValue(v).split("<nl>").toList)

  // ============ PURE EDITOR HELPERS ============

  def updateDialog(dialogs: Dialogs, dialog: Dialog): Dialogs =
    dialogs.updated(dialog.id, dialog)

  def updateDialogText(dialogs: Dialogs, dialogId: String, text: Expression): Dialogs =
    dialogs.get(dialogId).fold(dialogs)(d => dialogs.updated(dialogId, d.copy(text = text)))

  def updateDialogOptions(
      dialogs: Dialogs,
      dialogId: String,
      options: List[DialogOption]
  ): Dialogs =
    dialogs.get(dialogId).fold(dialogs)(d => dialogs.updated(dialogId, d.copy(options = options)))

  /** Replace an option (matched by id) within a dialog. */
  def updateDialogOption(dialogs: Dialogs, dialogId: String, option: DialogOption): Dialogs =
    dialogs.get(dialogId).fold(dialogs) { d =>
      val updated = d.options.map(o => if o.id == option.id then option else o)
      dialogs.updated(dialogId, d.copy(options = updated))
    }

  /** Replace an action (matched by id) within a specific option of a dialog. */
  def updateDialogAction(
      dialogs: Dialogs,
      dialogId: String,
      optionId: String,
      action: DialogAction
  ): Dialogs =
    dialogs.get(dialogId).fold(dialogs) { d =>
      val updatedOptions = d.options.map { o =>
        if o.id == optionId then
          o.copy(actions = o.actions.map(a => if a.id == action.id then action else a))
        else o
      }
      dialogs.updated(dialogId, d.copy(options = updatedOptions))
    }

  // ============ GENERATORS ============

  private def genId(prefix: String): String =
    s"$prefix-${System.currentTimeMillis()}-${scala.util.Random.nextInt(100000)}"

  def generateNewDialog(): Dialog =
    Dialog(genId("dialog"), Screept.litText("New dialog"), List.empty)

  def generateNewOption(): DialogOption =
    DialogOption(genId("option"), Screept.litText("New option"), None, List.empty)

  def generateNewGameDefinition(): GameDefinition =
    val dialog = generateNewDialog()
    GameDefinition(
      dialogs = Map(dialog.id -> dialog),
      gameState = GameState(dialogStack = List(dialog.id), screeptEnv = Environment())
    )

  // ============ LIST REORDERING ============

  /** Swap element `i` with its predecessor; no-op if out of range or already first. */
  def moveUp[A](xs: List[A], i: Int): List[A] =
    if i <= 0 || i >= xs.length then xs
    else xs.updated(i - 1, xs(i)).updated(i, xs(i - 1))

  /** Swap element `i` with its successor; no-op if out of range or already last. */
  def moveDown[A](xs: List[A], i: Int): List[A] =
    if i < 0 || i >= xs.length - 1 then xs
    else xs.updated(i, xs(i + 1)).updated(i + 1, xs(i))
