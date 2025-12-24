package com.crianonim.screept

import munit.CatsEffectSuite
import cats.effect.IO
import cats.effect.std.Random

class EvaluatorTest extends CatsEffectSuite:

  // Helper to get a random instance for tests
  private def withRandom[A](f: Random[IO] => IO[A]): IO[A] =
    Random.scalaUtilRandom[IO].flatMap(f)

  // Helper to evaluate expression
  private def evalExpr(expr: String, env: Environment = Environment()): IO[Either[EvaluationError, Value]] =
    withRandom { implicit r =>
      Parser.parseExpression(expr) match
        case Left(err) => IO.raiseError(new Exception(s"Parse error: $err"))
        case Right(e)  => Screept.eval[IO](e, env)
    }

  // Helper to run statement
  private def runStmt(stmt: String, env: Environment = Environment()): IO[Either[EvaluationError, Environment]] =
    withRandom { implicit r =>
      Parser.parseStatement(stmt) match
        case Left(err) => IO.raiseError(new Exception(s"Parse error: $err"))
        case Right(s)  => Screept.run[IO](s, env)
    }

  // ============ LITERAL EVALUATION ============

  test("evaluate number literal"):
    evalExpr("42").map(r => assertEquals(r, Right(NumberValue(42.0))))

  test("evaluate decimal literal"):
    evalExpr("3.14").map(r => assertEquals(r, Right(NumberValue(3.14))))

  test("evaluate string literal"):
    evalExpr("\"hello\"").map(r => assertEquals(r, Right(TextValue("hello"))))

  test("evaluate empty string"):
    evalExpr("\"\"").map(r => assertEquals(r, Right(TextValue(""))))

  // ============ ARITHMETIC OPERATIONS ============

  test("evaluate addition"):
    evalExpr("2 + 3").map(r => assertEquals(r, Right(NumberValue(5.0))))

  test("evaluate subtraction"):
    evalExpr("10 - 4").map(r => assertEquals(r, Right(NumberValue(6.0))))

  test("evaluate multiplication"):
    evalExpr("6 * 7").map(r => assertEquals(r, Right(NumberValue(42.0))))

  test("evaluate division"):
    evalExpr("15 / 3").map(r => assertEquals(r, Right(NumberValue(5.0))))

  test("evaluate integer division"):
    evalExpr("7 // 2").map(r => assertEquals(r, Right(NumberValue(3.0))))

  test("evaluate integer division with decimals"):
    evalExpr("7.5 // 2").map(r => assertEquals(r, Right(NumberValue(3.0))))

  test("evaluate complex arithmetic"):
    evalExpr("2 + 3 * 4").map(r => assertEquals(r, Right(NumberValue(14.0))))

  test("evaluate parenthesized arithmetic"):
    evalExpr("(2 + 3) * 4").map(r => assertEquals(r, Right(NumberValue(20.0))))

  test("evaluate nested arithmetic"):
    evalExpr("((1 + 2) * 3 - 4) / 5").map(r => assertEquals(r, Right(NumberValue(1.0))))

  // ============ STRING OPERATIONS ============

  test("string concatenation with +"):
    evalExpr("\"hello\" + \" \" + \"world\"").map(r => assertEquals(r, Right(TextValue("hello world"))))

  test("string and number concatenation"):
    evalExpr("\"value: \" + 42").map(r => assertEquals(r, Right(TextValue("value: 42"))))

  test("number and string concatenation"):
    evalExpr("42 + \" is the answer\"").map(r => assertEquals(r, Right(TextValue("42 is the answer"))))

  // ============ UNARY OPERATIONS ============

  test("unary plus"):
    evalExpr("+5").map(r => assertEquals(r, Right(NumberValue(5.0))))

  test("unary minus"):
    evalExpr("-5").map(r => assertEquals(r, Right(NumberValue(-5.0))))

  test("double negation"):
    evalExpr("--5").map(r => assertEquals(r, Right(NumberValue(5.0))))

  test("logical not on truthy value"):
    evalExpr("!1").map(r => assertEquals(r, Right(NumberValue(0.0))))

  test("logical not on falsy value"):
    evalExpr("!0").map(r => assertEquals(r, Right(NumberValue(1.0))))

  test("logical not on empty string"):
    evalExpr("!\"\"").map(r => assertEquals(r, Right(NumberValue(1.0))))

  test("logical not on non-empty string"):
    evalExpr("!\"hello\"").map(r => assertEquals(r, Right(NumberValue(0.0))))

  // ============ COMPARISON OPERATIONS ============

  test("equality - true"):
    evalExpr("5 == 5").map(r => assertEquals(r, Right(NumberValue(1.0))))

  test("equality - false"):
    evalExpr("5 == 6").map(r => assertEquals(r, Right(NumberValue(0.0))))

  test("equality - strings"):
    evalExpr("\"a\" == \"a\"").map(r => assertEquals(r, Right(NumberValue(1.0))))

  test("equality - type mismatch"):
    evalExpr("5 == \"5\"").map(r => assertEquals(r, Right(NumberValue(0.0))))

  test("less than - true"):
    evalExpr("3 < 5").map(r => assertEquals(r, Right(NumberValue(1.0))))

  test("less than - false"):
    evalExpr("5 < 3").map(r => assertEquals(r, Right(NumberValue(0.0))))

  test("less than - equal"):
    evalExpr("5 < 5").map(r => assertEquals(r, Right(NumberValue(0.0))))

  test("greater than - true"):
    evalExpr("5 > 3").map(r => assertEquals(r, Right(NumberValue(1.0))))

  test("greater than - false"):
    evalExpr("3 > 5").map(r => assertEquals(r, Right(NumberValue(0.0))))

  test("greater than - equal"):
    evalExpr("5 > 5").map(r => assertEquals(r, Right(NumberValue(0.0))))

  // ============ CONDITIONAL EXPRESSIONS ============

  test("conditional - true branch"):
    evalExpr("1 ? 10 : 20").map(r => assertEquals(r, Right(NumberValue(10.0))))

  test("conditional - false branch"):
    evalExpr("0 ? 10 : 20").map(r => assertEquals(r, Right(NumberValue(20.0))))

  test("conditional with comparison"):
    evalExpr("5 > 3 ? 1 : 0").map(r => assertEquals(r, Right(NumberValue(1.0))))

  test("nested conditional"):
    evalExpr("0 ? 1 : 1 ? 2 : 3").map(r => assertEquals(r, Right(NumberValue(2.0))))

  // ============ VARIABLE EVALUATION ============

  test("evaluate defined variable"):
    val env = Environment(vars = Map("x" -> NumberValue(42.0)))
    evalExpr("x", env).map(r => assertEquals(r, Right(NumberValue(42.0))))

  test("evaluate undefined variable"):
    evalExpr("undefined_var").map { r =>
      assert(r.isLeft)
      r.left.foreach(e => assert(e.isInstanceOf[UndefinedError]))
    }

  test("evaluate variable in expression"):
    val env = Environment(vars = Map("x" -> NumberValue(10.0), "y" -> NumberValue(5.0)))
    evalExpr("x + y * 2", env).map(r => assertEquals(r, Right(NumberValue(20.0))))

  // ============ COMPUTED IDENTIFIERS ============

  test("computed identifier"):
    val env = Environment(vars = Map(
      "name" -> TextValue("x"),
      "x" -> NumberValue(42.0)
    ))
    evalExpr("$[name]", env).map(r => assertEquals(r, Right(NumberValue(42.0))))

  test("computed identifier with expression"):
    val env = Environment(vars = Map(
      "var_1" -> NumberValue(100.0)
    ))
    evalExpr("$[\"var_\" + 1]", env).map(r => assertEquals(r, Right(NumberValue(100.0))))

  // ============ FUNCTION VALUES ============

  test("call function value"):
    val env = Environment(vars = Map(
      "double" -> FuncValue(BinaryOp(BinaryOperator.Mul, Var(LiteralId("_0")), Literal(NumberValue(2.0))))
    ))
    evalExpr("double(5)", env).map(r => assertEquals(r, Right(NumberValue(10.0))))

  test("call function with multiple args"):
    val env = Environment(vars = Map(
      "add" -> FuncValue(BinaryOp(BinaryOperator.Add, Var(LiteralId("_0")), Var(LiteralId("_1"))))
    ))
    evalExpr("add(3, 4)", env).map(r => assertEquals(r, Right(NumberValue(7.0))))

  test("call undefined function"):
    evalExpr("undefined()").map { r =>
      assert(r.isLeft)
    }

  // ============ TYPE ERRORS ============

  test("subtraction with string - type error"):
    evalExpr("\"hello\" - 1").map { r =>
      assert(r.isLeft)
      r.left.foreach(e => assert(e.isInstanceOf[TypeError]))
    }

  test("multiplication with string - type error"):
    evalExpr("\"hello\" * 2").map { r =>
      assert(r.isLeft)
      r.left.foreach(e => assert(e.isInstanceOf[TypeError]))
    }

  test("division with string - type error"):
    evalExpr("10 / \"2\"").map { r =>
      assert(r.isLeft)
      r.left.foreach(e => assert(e.isInstanceOf[TypeError]))
    }

  test("less than with string - type error"):
    evalExpr("\"a\" < \"b\"").map { r =>
      assert(r.isLeft)
      r.left.foreach(e => assert(e.isInstanceOf[TypeError]))
    }

  // ============ BIND STATEMENT ============

  test("bind variable"):
    runStmt("x = 10").map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.vars.get("x"), Some(NumberValue(10.0))))
    }

  test("bind with expression"):
    runStmt("x = 2 + 3").map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.vars.get("x"), Some(NumberValue(5.0))))
    }

  test("bind overwrites previous value"):
    val env = Environment(vars = Map("x" -> NumberValue(1.0)))
    runStmt("x = 100", env).map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.vars.get("x"), Some(NumberValue(100.0))))
    }

  test("bind with computed identifier"):
    val env = Environment(vars = Map("name" -> TextValue("result")))
    runStmt("$[name] = 42", env).map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.vars.get("result"), Some(NumberValue(42.0))))
    }

  // ============ PRINT STATEMENT ============

  test("print number"):
    runStmt("PRINT 42").map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.output.map(_.value), List("42")))
    }

  test("print string"):
    runStmt("PRINT \"hello\"").map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.output.map(_.value), List("hello")))
    }

  test("print expression"):
    runStmt("PRINT 2 + 3").map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.output.map(_.value), List("5")))
    }

  test("multiple prints"):
    runStmt("PRINT 1; PRINT 2; PRINT 3").map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.output.map(_.value), List("1", "2", "3")))
    }

  // ============ EMIT STATEMENT ============

  test("emit adds nothing to output by default"):
    runStmt("EMIT \"event\"").map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.output, Nil))
    }

  test("emit calls handler"):
    var emitted: List[String] = Nil
    val handler: String => IO[Unit] = s => IO { emitted = emitted :+ s }
    withRandom { implicit r =>
      Parser.parseStatement("EMIT \"test_event\"") match
        case Left(err) => IO.raiseError(new Exception(err))
        case Right(stmt) =>
          Screept.run[IO](stmt, Environment(), Some(handler)).map { r =>
            assert(r.isRight)
            assertEquals(emitted, List("test_event"))
          }
    }

  // ============ BLOCK STATEMENT ============

  test("block with single statement"):
    runStmt("{ x = 10 }").map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.vars.get("x"), Some(NumberValue(10.0))))
    }

  test("block with multiple statements"):
    runStmt("{ x = 5; y = x + 1; z = y * 2 }").map { r =>
      assert(r.isRight)
      r.foreach { env =>
        assertEquals(env.vars.get("x"), Some(NumberValue(5.0)))
        assertEquals(env.vars.get("y"), Some(NumberValue(6.0)))
        assertEquals(env.vars.get("z"), Some(NumberValue(12.0)))
      }
    }

  test("nested blocks"):
    runStmt("{ x = 1; { y = 2; z = 3 } }").map { r =>
      assert(r.isRight)
      r.foreach { env =>
        assertEquals(env.vars.get("x"), Some(NumberValue(1.0)))
        assertEquals(env.vars.get("y"), Some(NumberValue(2.0)))
        assertEquals(env.vars.get("z"), Some(NumberValue(3.0)))
      }
    }

  // ============ IF STATEMENT ============

  test("if true executes then branch"):
    runStmt("IF 1 THEN x = 10").map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.vars.get("x"), Some(NumberValue(10.0))))
    }

  test("if false skips then branch"):
    runStmt("IF 0 THEN x = 10").map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.vars.get("x"), None))
    }

  test("if-else true branch"):
    runStmt("IF 1 THEN x = 10 ELSE x = 20").map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.vars.get("x"), Some(NumberValue(10.0))))
    }

  test("if-else false branch"):
    runStmt("IF 0 THEN x = 10 ELSE x = 20").map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.vars.get("x"), Some(NumberValue(20.0))))
    }

  test("if with comparison condition"):
    val env = Environment(vars = Map("n" -> NumberValue(5.0)))
    runStmt("IF n > 3 THEN result = 1 ELSE result = 0", env).map { r =>
      assert(r.isRight)
      r.foreach(e => assertEquals(e.vars.get("result"), Some(NumberValue(1.0))))
    }

  test("if with block body"):
    runStmt("IF 1 THEN { x = 1; y = 2 }").map { r =>
      assert(r.isRight)
      r.foreach { env =>
        assertEquals(env.vars.get("x"), Some(NumberValue(1.0)))
        assertEquals(env.vars.get("y"), Some(NumberValue(2.0)))
      }
    }

  // ============ PROCEDURE DEFINITION AND RUN ============

  test("define and run procedure"):
    runStmt("PROC greet PRINT \"hello\"; RUN greet()").map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.output.map(_.value), List("hello")))
    }

  test("procedure with arguments"):
    runStmt("PROC double { result = _0 * 2 }; RUN double(5)").map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.vars.get("result"), Some(NumberValue(10.0))))
    }

  test("procedure with multiple arguments"):
    runStmt("PROC add { sum = _0 + _1 }; RUN add(3, 7)").map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.vars.get("sum"), Some(NumberValue(10.0))))
    }

  test("procedure with block body"):
    runStmt("PROC calc { a = _0; b = _1; result = a * b }; RUN calc(6, 7)").map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.vars.get("result"), Some(NumberValue(42.0))))
    }

  test("run undefined procedure"):
    runStmt("RUN undefined_proc()").map { r =>
      assert(r.isLeft)
      r.left.foreach(e => assert(e.isInstanceOf[UndefinedError]))
    }

  // ============ RANDOM STATEMENT ============

  test("random generates number in range"):
    runStmt("RND x 1 10").map { r =>
      assert(r.isRight)
      r.foreach { env =>
        val x = env.vars.get("x")
        assert(x.isDefined)
        x.foreach {
          case NumberValue(v) =>
            assert(v >= 1 && v <= 10, s"Random value $v not in range [1, 10]")
          case _ => fail("Expected NumberValue")
        }
      }
    }

  test("random with same bounds"):
    runStmt("RND x 5 5").map { r =>
      assert(r.isRight)
      r.foreach { env =>
        assertEquals(env.vars.get("x"), Some(NumberValue(5.0)))
      }
    }

  test("random with expressions"):
    val env = Environment(vars = Map("min" -> NumberValue(1.0), "max" -> NumberValue(100.0)))
    runStmt("RND x min max", env).map { r =>
      assert(r.isRight)
      r.foreach { e =>
        val x = e.vars.get("x")
        assert(x.isDefined)
      }
    }

  // ============ HELPER FUNCTIONS ============

  test("isTruthy - number zero is falsy"):
    assertEquals(Evaluator.isTruthy(NumberValue(0.0)), false)

  test("isTruthy - number non-zero is truthy"):
    assertEquals(Evaluator.isTruthy(NumberValue(1.0)), true)
    assertEquals(Evaluator.isTruthy(NumberValue(-1.0)), true)
    assertEquals(Evaluator.isTruthy(NumberValue(0.5)), true)

  test("isTruthy - empty string is falsy"):
    assertEquals(Evaluator.isTruthy(TextValue("")), false)

  test("isTruthy - non-empty string is truthy"):
    assertEquals(Evaluator.isTruthy(TextValue("a")), true)
    assertEquals(Evaluator.isTruthy(TextValue(" ")), true)

  test("isTruthy - function is truthy"):
    assertEquals(Evaluator.isTruthy(FuncValue(Literal(NumberValue(0.0)))), true)

  test("getStringValue - number"):
    assertEquals(Evaluator.getStringValue(NumberValue(42.0)), "42")

  test("getStringValue - decimal"):
    assertEquals(Evaluator.getStringValue(NumberValue(3.14)), "3.14")

  test("getStringValue - text"):
    assertEquals(Evaluator.getStringValue(TextValue("hello")), "hello")

  // ============ COMPLEX SCENARIOS ============

  test("fibonacci-like sequence"):
    runStmt("a = 1; b = 1; c = a + b; d = b + c; e = c + d").map { r =>
      assert(r.isRight)
      r.foreach { env =>
        assertEquals(env.vars.get("a"), Some(NumberValue(1.0)))
        assertEquals(env.vars.get("b"), Some(NumberValue(1.0)))
        assertEquals(env.vars.get("c"), Some(NumberValue(2.0)))
        assertEquals(env.vars.get("d"), Some(NumberValue(3.0)))
        assertEquals(env.vars.get("e"), Some(NumberValue(5.0)))
      }
    }

  test("counter simulation"):
    runStmt("count = 0; count = count + 1; count = count + 1; count = count + 1").map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.vars.get("count"), Some(NumberValue(3.0))))
    }

  test("conditional with side effects"):
    runStmt("x = 5; IF x > 3 THEN { y = 1; PRINT \"big\" } ELSE { y = 0; PRINT \"small\" }").map { r =>
      assert(r.isRight)
      r.foreach { env =>
        assertEquals(env.vars.get("y"), Some(NumberValue(1.0)))
        assertEquals(env.output.map(_.value), List("big"))
      }
    }

  test("procedure calling another procedure"):
    runStmt("""
      PROC inner { result = _0 * 2 };
      PROC outer { RUN inner(_0 + 1) };
      RUN outer(4)
    """).map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.vars.get("result"), Some(NumberValue(10.0))))
    }
