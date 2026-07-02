package com.crianonim.dialog

import com.crianonim.screept.{Environment, Expression, Statement}
import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.*

// ============ DIALOG ACTIONS ============

sealed trait DialogAction:
  def id: String

case class GoBack(id: String)                                extends DialogAction
case class GoDialog(id: String, destination: String)         extends DialogAction
case class MsgAction(id: String, value: Expression)          extends DialogAction
case class ScreeptAction(id: String, value: Statement)       extends DialogAction
case class Conditional(
    id: String,
    cond: Expression,
    thenActions: List[DialogAction],
    elseActions: List[DialogAction]
) extends DialogAction
case class BlockAction(id: String, actions: List[DialogAction]) extends DialogAction

object DialogAction:
  given Encoder[DialogAction] = Encoder.instance {
    case GoBack(id) =>
      Json.obj("type" -> Json.fromString("go back"), "id" -> Json.fromString(id))
    case GoDialog(id, destination) =>
      Json.obj(
        "type"        -> Json.fromString("go_dialog"),
        "destination" -> Json.fromString(destination),
        "id"          -> Json.fromString(id)
      )
    case MsgAction(id, value) =>
      Json.obj(
        "type"  -> Json.fromString("msg"),
        "value" -> Encoder[Expression].apply(value),
        "id"    -> Json.fromString(id)
      )
    case ScreeptAction(id, value) =>
      Json.obj(
        "type"  -> Json.fromString("screept"),
        "value" -> Encoder[Statement].apply(value),
        "id"    -> Json.fromString(id)
      )
    case Conditional(id, cond, thenActions, elseActions) =>
      Json.obj(
        "type" -> Json.fromString("conditional"),
        "if"   -> Encoder[Expression].apply(cond),
        "then" -> Encoder[List[DialogAction]].apply(thenActions),
        "else" -> Encoder[List[DialogAction]].apply(elseActions),
        "id"   -> Json.fromString(id)
      )
    case BlockAction(id, actions) =>
      Json.obj(
        "type"    -> Json.fromString("block"),
        "actions" -> Encoder[List[DialogAction]].apply(actions),
        "id"      -> Json.fromString(id)
      )
  }

  given Decoder[DialogAction] = Decoder.instance { c =>
    c.get[String]("type").flatMap {
      case "go back" =>
        c.get[String]("id").map(GoBack.apply)
      case "go_dialog" =>
        for { id <- c.get[String]("id"); d <- c.get[String]("destination") } yield GoDialog(id, d)
      case "msg" =>
        for { id <- c.get[String]("id"); v <- c.get[Expression]("value") } yield MsgAction(id, v)
      case "screept" =>
        for { id <- c.get[String]("id"); v <- c.get[Statement]("value") } yield ScreeptAction(id, v)
      case "conditional" =>
        for {
          id       <- c.get[String]("id")
          cond     <- c.get[Expression]("if")
          thenActs <- c.get[List[DialogAction]]("then")
          elseActs <- c.get[List[DialogAction]]("else")
        } yield Conditional(id, cond, thenActs, elseActs)
      case "block" =>
        for {
          id   <- c.get[String]("id")
          acts <- c.get[List[DialogAction]]("actions")
        } yield BlockAction(id, acts)
      case other =>
        Left(io.circe.DecodingFailure(s"Unknown dialog action type: $other", c.history))
    }
  }

// ============ DIALOG OPTION ============

case class DialogOption(
    id: String,
    text: Expression,
    condition: Option[Expression],
    actions: List[DialogAction]
)

object DialogOption:
  given Encoder[DialogOption] = deriveEncoder
  given Decoder[DialogOption] = deriveDecoder

// ============ DIALOG ============

case class Dialog(id: String, text: Expression, options: List[DialogOption])

object Dialog:
  given Encoder[Dialog] = deriveEncoder
  given Decoder[Dialog] = deriveDecoder

// ============ GAME STATE / DEFINITION ============

type Dialogs = Map[String, Dialog]

case class GameState(dialogStack: List[String], screeptEnv: Environment)

object GameState:
  given Encoder[GameState] = deriveEncoder
  given Decoder[GameState] = deriveDecoder

case class GameDefinition(dialogs: Dialogs, gameState: GameState)

object GameDefinition:
  given Encoder[GameDefinition] = deriveEncoder
  given Decoder[GameDefinition] = deriveDecoder
