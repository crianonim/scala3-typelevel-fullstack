package com.crianonim.screept

import cats.Monad
import cats.syntax.all.*
import cats.effect.std.Random

trait Evaluator[F[_]]:
  def evaluateExpression(expr: Expression, env: Environment): F[Either[EvaluationError, Value]]
  def runStatement(
      stmt: Statement,
      env: Environment,
      handler: Option[String => F[Unit]] = None
  ): F[Either[EvaluationError, Environment]]

object Evaluator:
  def apply[F[_]: Monad: Random]: Evaluator[F] = new EvaluatorImpl[F]

  // Helper functions (pure, no effect needed)
  def isTruthy(value: Value): Boolean = value match
    case NumberValue(v) => v != 0
    case TextValue(v)   => v.nonEmpty
    case FuncValue(_)   => true

  def getStringValue(value: Value): String = value match
    case NumberValue(v) => if v == v.toLong then v.toLong.toString else v.toString
    case TextValue(v)   => v
    case FuncValue(e)   => s"FUNC($e)"

  def createOutputLine(value: String): OutputLine =
    OutputLine(System.currentTimeMillis(), value)

private class EvaluatorImpl[F[_]: Monad: Random] extends Evaluator[F]:
  import Evaluator.*

  private def evaluateIdentifier(
      env: Environment,
      id: Identifier
  ): F[Either[EvaluationError, String]] =
    id match
      case LiteralId(name) => Right(name).pure[F]
      case ComputedId(expr) =>
        evaluateExpression(expr, env).map(_.map(getStringValue))

  override def evaluateExpression(
      expr: Expression,
      env: Environment
  ): F[Either[EvaluationError, Value]] =
    expr match
      case Literal(value) =>
        Right(value).pure[F]

      case Var(identifier) =>
        evaluateIdentifier(env, identifier).map {
          case Left(err) => Left(err)
          case Right(name) =>
            env.vars.get(name) match
              case Some(v) => Right(v)
              case None    => Left(UndefinedError(name, s"Variable '$name' not found"))
        }

      case UnaryOp(op, x) =>
        evaluateExpression(x, env).map { result =>
          result.flatMap { value =>
            op match
              case UnaryOperator.Plus =>
                value match
                  case NumberValue(v) => Right(NumberValue(v))
                  case _ => Left(TypeError("number", "unary +"))
              case UnaryOperator.Minus =>
                value match
                  case NumberValue(v) => Right(NumberValue(-v))
                  case _ => Left(TypeError("number", "unary -"))
              case UnaryOperator.Not =>
                Right(NumberValue(if isTruthy(value) then 0 else 1))
          }
        }

      case BinaryOp(op, x, y) =>
        for
          xResult <- evaluateExpression(x, env)
          yResult <- evaluateExpression(y, env)
        yield for
          xVal <- xResult
          yVal <- yResult
          result <- evaluateBinaryOp(op, xVal, yVal)
        yield result

      case Condition(cond, onTrue, onFalse) =>
        evaluateExpression(cond, env).flatMap {
          case Left(err) => Left(err).pure[F]
          case Right(condVal) =>
            if isTruthy(condVal)
            then evaluateExpression(onTrue, env)
            else evaluateExpression(onFalse, env)
        }

      case FunCall(identifier, args) =>
        evaluateIdentifier(env, identifier).flatMap {
          case Left(err) => Left(err).pure[F]
          case Right(name) =>
            env.vars.get(name) match
              case Some(FuncValue(bodyExpr)) =>
                // Evaluate args and bind to _0, _1, etc.
                environmentWithArgs(args, env).flatMap {
                  case Left(err)      => Left(err).pure[F]
                  case Right(argsEnv) => evaluateExpression(bodyExpr, argsEnv)
                }
              case Some(_) =>
                Left(TypeError("func", s"'$name' is not a function")).pure[F]
              case None =>
                Left(UndefinedError(name, s"Function '$name' not found")).pure[F]
        }

      case Parens(expression) =>
        evaluateExpression(expression, env)

  private def evaluateBinaryOp(
      op: BinaryOperator,
      x: Value,
      y: Value
  ): Either[EvaluationError, Value] =
    import BinaryOperator.*
    op match
      case Add =>
        (x, y) match
          case (NumberValue(a), NumberValue(b)) => Right(NumberValue(a + b))
          case _ => Right(TextValue(getStringValue(x) + getStringValue(y)))

      case Sub =>
        (x, y) match
          case (NumberValue(a), NumberValue(b)) => Right(NumberValue(a - b))
          case _ => Left(TypeError("number", "subtraction"))

      case Mul =>
        (x, y) match
          case (NumberValue(a), NumberValue(b)) => Right(NumberValue(a * b))
          case _ => Left(TypeError("number", "multiplication"))

      case Div =>
        (x, y) match
          case (NumberValue(a), NumberValue(b)) => Right(NumberValue(a / b))
          case _ => Left(TypeError("number", "division"))

      case IntDiv =>
        (x, y) match
          case (NumberValue(a), NumberValue(b)) => Right(NumberValue(math.floor(a / b)))
          case _ => Left(TypeError("number", "integer division"))

      case Eq =>
        val result =
          x.getClass == y.getClass && getStringValue(x) == getStringValue(y)
        Right(NumberValue(if result then 1 else 0))

      case Lt =>
        (x, y) match
          case (NumberValue(a), NumberValue(b)) =>
            Right(NumberValue(if a < b then 1 else 0))
          case _ => Left(TypeError("number", "less than comparison"))

      case Gt =>
        (x, y) match
          case (NumberValue(a), NumberValue(b)) =>
            Right(NumberValue(if a > b then 1 else 0))
          case _ => Left(TypeError("number", "greater than comparison"))

  override def runStatement(
      stmt: Statement,
      env: Environment,
      handler: Option[String => F[Unit]]
  ): F[Either[EvaluationError, Environment]] =
    stmt match
      case Print(value) =>
        evaluateExpression(value, env).map { result =>
          result.map { v =>
            val output = createOutputLine(getStringValue(v))
            env.copy(output = env.output :+ output)
          }
        }

      case Emit(value) =>
        evaluateExpression(value, env).flatMap {
          case Left(err) => Left(err).pure[F]
          case Right(v) =>
            val msg = getStringValue(v)
            handler match
              case Some(h) => h(msg).as(Right(env))
              case None    => Right(env).pure[F]
        }

      case Bind(identifier, value) =>
        for
          idResult <- evaluateIdentifier(env, identifier)
          valResult <- evaluateExpression(value, env)
        yield for
          name <- idResult
          v <- valResult
        yield env.copy(vars = env.vars + (name -> v))

      case Block(statements) =>
        statements.foldLeftM[F, Either[EvaluationError, Environment]](Right(env)) {
          (accF, stmt) =>
            accF match
              case Left(err) => Left(err).pure[F]
              case Right(currentEnv) => runStatement(stmt, currentEnv, handler)
        }

      case If(condition, thenStmt, elseStmt) =>
        evaluateExpression(condition, env).flatMap {
          case Left(err) => Left(err).pure[F]
          case Right(condVal) =>
            if isTruthy(condVal)
            then runStatement(thenStmt, env, handler)
            else elseStmt match
              case Some(s) => runStatement(s, env, handler)
              case None    => Right(env).pure[F]
        }

      case ProcDef(identifier, statement) =>
        evaluateIdentifier(env, identifier).map {
          case Left(err) => Left(err)
          case Right(name) =>
            Right(env.copy(procedures = env.procedures + (name -> statement)))
        }

      case ProcRun(identifier, args) =>
        evaluateIdentifier(env, identifier).flatMap {
          case Left(err) => Left(err).pure[F]
          case Right(name) =>
            env.procedures.get(name) match
              case None =>
                Left(UndefinedError(name, s"Procedure '$name' not found")).pure[F]
              case Some(procStmt) =>
                environmentWithArgs(args, env).flatMap {
                  case Left(err) => Left(err).pure[F]
                  case Right(argsEnv) =>
                    runStatement(procStmt, argsEnv, handler)
                }
        }

      case RandomStmt(identifier, from, to) =>
        for
          idResult <- evaluateIdentifier(env, identifier)
          fromResult <- evaluateExpression(from, env)
          toResult <- evaluateExpression(to, env)
          result <- (idResult, fromResult, toResult) match
            case (Right(name), Right(NumberValue(fromVal)), Right(NumberValue(toVal))) =>
              Random[F].betweenDouble(fromVal, toVal + 1).map { rnd =>
                val value = NumberValue(math.round(rnd).toDouble)
                Right(env.copy(vars = env.vars + (name -> value)))
              }
            case (Left(err), _, _) => Left(err).pure[F]
            case (_, Left(err), _) => Left(err).pure[F]
            case (_, _, Left(err)) => Left(err).pure[F]
            case _ => Left(TypeError("number", "RND requires numeric bounds")).pure[F]
        yield result

  private def environmentWithArgs(
      args: List[Expression],
      env: Environment
  ): F[Either[EvaluationError, Environment]] =
    args.zipWithIndex
      .traverse { case (expr, idx) =>
        evaluateExpression(expr, env).map(_.map(v => (s"_$idx", v)))
      }
      .map { results =>
        results.sequence.map { pairs =>
          pairs.foldLeft(env) { case (accEnv, (name, value)) =>
            accEnv.copy(vars = accEnv.vars + (name -> value))
          }
        }
      }
