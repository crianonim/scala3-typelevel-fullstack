package com.crianonim.screept

import cats.Monad
import cats.effect.std.Random
import cats.effect.{IO, IOApp}
import cats.syntax.all.*

object Screept:
  // ============ PARSING (pure, no effect needed) ============

  def parse(input: String): Either[String, Statement] =
    Parser.parseStatement(input)

  def parseExpr(input: String): Either[String, Expression] =
    Parser.parseExpression(input)

  // ============ EVALUATION (requires Random effect) ============

  def evaluator[F[_]: Monad: Random]: Evaluator[F] =
    Evaluator[F]

  def run[F[_]: Monad: Random](
      stmt: Statement,
      env: Environment = Environment(),
      handler: Option[String => F[Unit]] = None
  ): F[Either[EvaluationError, Environment]] =
    evaluator[F].runStatement(stmt, env, handler)

  def eval[F[_]: Monad: Random](
      expr: Expression,
      env: Environment = Environment()
  ): F[Either[EvaluationError, Value]] =
    evaluator[F].evaluateExpression(expr, env)

  // ============ CONVENIENCE: Parse and run in one step ============

  def execute[F[_]: Monad: Random](
      input: String,
      env: Environment = Environment(),
      handler: Option[String => F[Unit]] = None
  ): F[Either[String, Environment]] =
    parse(input) match
      case Left(parseError) => Left(parseError).pure[F]
      case Right(stmt) =>
        run(stmt, env, handler).map(_.left.map(EvaluationError.show))

  // ============ HELPER FUNCTIONS (re-exported from Evaluator) ============

  def isTruthy(value: Value): Boolean = Evaluator.isTruthy(value)
  def getStringValue(value: Value): String = Evaluator.getStringValue(value)

  // ============ AST CONSTRUCTORS (for programmatic AST building) ============

  def num(n: Double): Value = NumberValue(n)
  def num(n: Int): Value = NumberValue(n.toDouble)
  def text(s: String): Value = TextValue(s)
  def func(body: Expression): Value = FuncValue(body)

  def lit(v: Value): Expression = Literal(v)
  def litNum(n: Double): Expression = Literal(NumberValue(n))
  def litNum(n: Int): Expression = Literal(NumberValue(n.toDouble))
  def litText(s: String): Expression = Literal(TextValue(s))

  def varRef(name: String): Expression = Var(LiteralId(name))

// ============ TEST RUNNER ============

object TestScreept extends IOApp.Simple:
  import Screept.*

  def run: IO[Unit] = Random.scalaUtilRandom[IO].flatMap { implicit random =>
    given Random[IO] = random

    for
      // Test expression parsing and evaluation
      _ <- IO.println("=== Testing Screept ===")
      _ <- IO.println("\n--- Expression Parsing ---")

      // Simple arithmetic
      expr1 <- IO.fromEither(parseExpr("1 + 2 * 3").left.map(new Exception(_)))
      result1 <- Screept.eval[IO](expr1)
      _ <- IO.println(s"1 + 2 * 3 = $result1")

      // String concatenation
      expr2 <- IO.fromEither(parseExpr("\"Hello \" + \"World\"").left.map(new Exception(_)))
      result2 <- Screept.eval[IO](expr2)
      _ <- IO.println(s""""Hello " + "World" = $result2""")

      // Conditional
      expr3 <- IO.fromEither(parseExpr("1 > 0 ? 10 : 20").left.map(new Exception(_)))
      result3 <- Screept.eval[IO](expr3)
      _ <- IO.println(s"1 > 0 ? 10 : 20 = $result3")

      _ <- IO.println("\n--- Statement Execution ---")

      // Variable binding and print
      stmt1 <- IO.fromEither(parse("x = 10; y = 20; PRINT x + y").left.map(new Exception(_)))
      env1 <- Screept.run[IO](stmt1).flatMap(r => IO.fromEither(r.left.map(e => new Exception(EvaluationError.show(e)))))
      _ <- IO.println(s"x = 10; y = 20; PRINT x + y => output: ${env1.output.map(_.value)}")

      // If statement
      stmt2 <- IO.fromEither(parse("a = 5; IF a > 3 THEN PRINT \"big\" ELSE PRINT \"small\"").left.map(new Exception(_)))
      env2 <- Screept.run[IO](stmt2).flatMap(r => IO.fromEither(r.left.map(e => new Exception(EvaluationError.show(e)))))
      _ <- IO.println(s"IF a > 3 THEN PRINT big ELSE PRINT small => output: ${env2.output.map(_.value)}")

      // Procedure definition and call
      stmt3 <- IO.fromEither(parse("PROC greet { PRINT \"Hello \"; PRINT _0 }; RUN greet(\"World\")").left.map(new Exception(_)))
      env3 <- Screept.run[IO](stmt3).flatMap(r => IO.fromEither(r.left.map(e => new Exception(EvaluationError.show(e)))))
      _ <- IO.println(s"PROC greet; RUN greet(World) => output: ${env3.output.map(_.value)}")

      // Random number
      stmt4 <- IO.fromEither(parse("RND x 1 10; PRINT x").left.map(new Exception(_)))
      env4 <- Screept.run[IO](stmt4).flatMap(r => IO.fromEither(r.left.map(e => new Exception(EvaluationError.show(e)))))
      _ <- IO.println(s"RND x 1 10; PRINT x => output: ${env4.output.map(_.value)}")

      // Function value
      stmt5 <- IO.fromEither(parse("add = FUNC _0 + _1; result = add(3, 4); PRINT result").left.map(new Exception(_)))
      env5 <- Screept.run[IO](stmt5).flatMap(r => IO.fromEither(r.left.map(e => new Exception(EvaluationError.show(e)))))
      _ <- IO.println(s"add = FUNC _0 + _1; result = add(3, 4) => output: ${env5.output.map(_.value)}")

      _ <- IO.println("\n=== All tests passed! ===")
    yield ()
  }
