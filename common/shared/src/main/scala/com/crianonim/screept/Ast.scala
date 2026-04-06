package com.crianonim.screept

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.*

// ============ VALUES ============

sealed trait Value
case class NumberValue(value: Double) extends Value
case class TextValue(value: String) extends Value
case class FuncValue(body: Expression) extends Value

object Value:
  given Encoder[Value] = Encoder.instance {
    case NumberValue(v) => Json.obj("type" -> Json.fromString("number"), "value" -> Json.fromDoubleOrNull(v))
    case TextValue(v)   => Json.obj("type" -> Json.fromString("text"), "value" -> Json.fromString(v))
    case FuncValue(e)   => Json.obj("type" -> Json.fromString("func"), "body" -> Encoder[Expression].apply(e))
  }

  given Decoder[Value] = Decoder.instance { c =>
    c.get[String]("type").flatMap {
      case "number" => c.get[Double]("value").map(NumberValue.apply)
      case "text"   => c.get[String]("value").map(TextValue.apply)
      case "func"   => c.get[Expression]("body").map(FuncValue.apply)
      case other    => Left(io.circe.DecodingFailure(s"Unknown value type: $other", c.history))
    }
  }

// ============ OPERATORS ============

enum UnaryOperator:
  case Plus, Minus, Not

object UnaryOperator:
  given Encoder[UnaryOperator] = Encoder.encodeString.contramap {
    case UnaryOperator.Plus  => "+"
    case UnaryOperator.Minus => "-"
    case UnaryOperator.Not   => "!"
  }

  given Decoder[UnaryOperator] = Decoder.decodeString.emap {
    case "+" => Right(UnaryOperator.Plus)
    case "-" => Right(UnaryOperator.Minus)
    case "!" => Right(UnaryOperator.Not)
    case s   => Left(s"Unknown unary operator: $s")
  }

enum BinaryOperator:
  case Add, Sub, Mul, Div, IntDiv, Eq, Lt, Gt

object BinaryOperator:
  given Encoder[BinaryOperator] = Encoder.encodeString.contramap {
    case BinaryOperator.Add    => "+"
    case BinaryOperator.Sub    => "-"
    case BinaryOperator.Mul    => "*"
    case BinaryOperator.Div    => "/"
    case BinaryOperator.IntDiv => "//"
    case BinaryOperator.Eq     => "=="
    case BinaryOperator.Lt     => "<"
    case BinaryOperator.Gt     => ">"
  }

  given Decoder[BinaryOperator] = Decoder.decodeString.emap {
    case "+"  => Right(BinaryOperator.Add)
    case "-"  => Right(BinaryOperator.Sub)
    case "*"  => Right(BinaryOperator.Mul)
    case "/"  => Right(BinaryOperator.Div)
    case "//" => Right(BinaryOperator.IntDiv)
    case "==" => Right(BinaryOperator.Eq)
    case "<"  => Right(BinaryOperator.Lt)
    case ">"  => Right(BinaryOperator.Gt)
    case s    => Left(s"Unknown binary operator: $s")
  }

// ============ IDENTIFIERS ============

sealed trait Identifier
case class LiteralId(name: String) extends Identifier
case class ComputedId(expr: Expression) extends Identifier

object Identifier:
  given Encoder[Identifier] = Encoder.instance {
    case LiteralId(name)  => Json.obj("type" -> Json.fromString("literal"), "value" -> Json.fromString(name))
    case ComputedId(expr) => Json.obj("type" -> Json.fromString("computed"), "value" -> Encoder[Expression].apply(expr))
  }

  given Decoder[Identifier] = Decoder.instance { c =>
    c.get[String]("type").flatMap {
      case "literal"  => c.get[String]("value").map(LiteralId.apply)
      case "computed" => c.get[Expression]("value").map(ComputedId.apply)
      case other      => Left(io.circe.DecodingFailure(s"Unknown identifier type: $other", c.history))
    }
  }

// ============ EXPRESSIONS ============

sealed trait Expression
case class Literal(value: Value) extends Expression
case class UnaryOp(op: UnaryOperator, x: Expression) extends Expression
case class BinaryOp(op: BinaryOperator, x: Expression, y: Expression) extends Expression
case class Var(identifier: Identifier) extends Expression
case class Condition(condition: Expression, onTrue: Expression, onFalse: Expression) extends Expression
case class FunCall(identifier: Identifier, args: List[Expression]) extends Expression
case class Parens(expression: Expression) extends Expression

object Expression:
  given Encoder[Expression] = Encoder.instance {
    case Literal(v) =>
      Json.obj("type" -> Json.fromString("literal"), "value" -> Encoder[Value].apply(v))
    case UnaryOp(op, x) =>
      Json.obj(
        "type" -> Json.fromString("unary_op"),
        "op"   -> Encoder[UnaryOperator].apply(op),
        "x"    -> Encoder[Expression].apply(x)
      )
    case BinaryOp(op, x, y) =>
      Json.obj(
        "type" -> Json.fromString("binary_op"),
        "op"   -> Encoder[BinaryOperator].apply(op),
        "x"    -> Encoder[Expression].apply(x),
        "y"    -> Encoder[Expression].apply(y)
      )
    case Var(id) =>
      Json.obj("type" -> Json.fromString("var"), "identifier" -> Encoder[Identifier].apply(id))
    case Condition(cond, onTrue, onFalse) =>
      Json.obj(
        "type"      -> Json.fromString("condition"),
        "condition" -> Encoder[Expression].apply(cond),
        "onTrue"    -> Encoder[Expression].apply(onTrue),
        "onFalse"   -> Encoder[Expression].apply(onFalse)
      )
    case FunCall(id, args) =>
      Json.obj(
        "type"       -> Json.fromString("fun_call"),
        "identifier" -> Encoder[Identifier].apply(id),
        "args"       -> Encoder[List[Expression]].apply(args)
      )
    case Parens(expr) =>
      Json.obj("type" -> Json.fromString("parens"), "expression" -> Encoder[Expression].apply(expr))
  }

  given Decoder[Expression] = Decoder.instance { c =>
    c.get[String]("type").flatMap {
      case "literal"    => c.get[Value]("value").map(Literal.apply)
      case "unary_op"   => for { op <- c.get[UnaryOperator]("op"); x <- c.get[Expression]("x") } yield UnaryOp(op, x)
      case "binary_op"  => for { op <- c.get[BinaryOperator]("op"); x <- c.get[Expression]("x"); y <- c.get[Expression]("y") } yield BinaryOp(op, x, y)
      case "var"        => c.get[Identifier]("identifier").map(Var.apply)
      case "condition"  => for { cond <- c.get[Expression]("condition"); t <- c.get[Expression]("onTrue"); f <- c.get[Expression]("onFalse") } yield Condition(cond, t, f)
      case "fun_call"   => for { id <- c.get[Identifier]("identifier"); args <- c.get[List[Expression]]("args") } yield FunCall(id, args)
      case "parens"     => c.get[Expression]("expression").map(Parens.apply)
      case other        => Left(io.circe.DecodingFailure(s"Unknown expression type: $other", c.history))
    }
  }

// ============ STATEMENTS ============

sealed trait Statement
case class Bind(identifier: Identifier, value: Expression) extends Statement
case class Print(value: Expression) extends Statement
case class Emit(value: Expression) extends Statement
case class Block(statements: List[Statement]) extends Statement
case class ProcDef(identifier: Identifier, statement: Statement) extends Statement
case class ProcRun(identifier: Identifier, args: List[Expression]) extends Statement
case class RandomStmt(identifier: Identifier, from: Expression, to: Expression) extends Statement
case class If(condition: Expression, thenStmt: Statement, elseStmt: Option[Statement]) extends Statement

object Statement:
  given Encoder[Statement] = Encoder.instance {
    case Bind(id, value) =>
      Json.obj(
        "type"       -> Json.fromString("bind"),
        "identifier" -> Encoder[Identifier].apply(id),
        "value"      -> Encoder[Expression].apply(value)
      )
    case Print(value) =>
      Json.obj("type" -> Json.fromString("print"), "value" -> Encoder[Expression].apply(value))
    case Emit(value) =>
      Json.obj("type" -> Json.fromString("emit"), "value" -> Encoder[Expression].apply(value))
    case Block(stmts) =>
      Json.obj("type" -> Json.fromString("block"), "statements" -> Encoder[List[Statement]].apply(stmts))
    case ProcDef(id, stmt) =>
      Json.obj(
        "type"       -> Json.fromString("proc_def"),
        "identifier" -> Encoder[Identifier].apply(id),
        "statement"  -> Encoder[Statement].apply(stmt)
      )
    case ProcRun(id, args) =>
      Json.obj(
        "type"       -> Json.fromString("proc_run"),
        "identifier" -> Encoder[Identifier].apply(id),
        "args"       -> Encoder[List[Expression]].apply(args)
      )
    case RandomStmt(id, from, to) =>
      Json.obj(
        "type"       -> Json.fromString("random"),
        "identifier" -> Encoder[Identifier].apply(id),
        "from"       -> Encoder[Expression].apply(from),
        "to"         -> Encoder[Expression].apply(to)
      )
    case If(cond, thenS, elseS) =>
      Json.obj(
        "type"          -> Json.fromString("if"),
        "condition"     -> Encoder[Expression].apply(cond),
        "thenStatement" -> Encoder[Statement].apply(thenS),
        "elseStatement" -> elseS.fold(Json.Null)(s => Encoder[Statement].apply(s))
      )
  }

  given Decoder[Statement] = Decoder.instance { c =>
    c.get[String]("type").flatMap {
      case "bind" =>
        for { id <- c.get[Identifier]("identifier"); v <- c.get[Expression]("value") } yield Bind(id, v)
      case "print" =>
        c.get[Expression]("value").map(Print.apply)
      case "emit" =>
        c.get[Expression]("value").map(Emit.apply)
      case "block" =>
        c.get[List[Statement]]("statements").map(Block.apply)
      case "proc_def" =>
        for { id <- c.get[Identifier]("identifier"); s <- c.get[Statement]("statement") } yield ProcDef(id, s)
      case "proc_run" =>
        for { id <- c.get[Identifier]("identifier"); args <- c.get[List[Expression]]("args") } yield ProcRun(id, args)
      case "random" =>
        for { id <- c.get[Identifier]("identifier"); f <- c.get[Expression]("from"); t <- c.get[Expression]("to") } yield RandomStmt(id, f, t)
      case "if" =>
        for {
          cond  <- c.get[Expression]("condition")
          thenS <- c.get[Statement]("thenStatement")
          elseS <- c.get[Option[Statement]]("elseStatement")
        } yield If(cond, thenS, elseS)
      case other =>
        Left(io.circe.DecodingFailure(s"Unknown statement type: $other", c.history))
    }
  }

// ============ ENVIRONMENT ============

case class OutputLine(ts: Long, value: String)

object OutputLine:
  given Encoder[OutputLine] = deriveEncoder
  given Decoder[OutputLine] = deriveDecoder

case class Environment(
    vars: Map[String, Value] = Map.empty,
    procedures: Map[String, Statement] = Map.empty,
    output: List[OutputLine] = List.empty
)

object Environment:
  given Encoder[Environment] = deriveEncoder
  given Decoder[Environment] = deriveDecoder

// ============ ERRORS ============

sealed trait EvaluationError
case class TypeError(expected: String, ctx: String) extends EvaluationError
case class UndefinedError(name: String, ctx: String) extends EvaluationError
case class OtherError(message: String) extends EvaluationError

object EvaluationError:
  def show(e: EvaluationError): String = e match
    case TypeError(exp, ctx)     => s"Type error - expected $exp: $ctx"
    case UndefinedError(name, _) => s"Undefined: $name"
    case OtherError(msg)         => s"Error: $msg"
