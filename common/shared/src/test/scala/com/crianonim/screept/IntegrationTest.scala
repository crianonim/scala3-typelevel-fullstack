package com.crianonim.screept

import munit.CatsEffectSuite
import cats.effect.IO
import cats.effect.std.Random
import io.circe.syntax.*
import io.circe.parser.decode

class IntegrationTest extends CatsEffectSuite:

  // Helper for full parse-and-execute workflow
  private def execute(code: String, env: Environment = Environment()): IO[Either[String, Environment]] =
    Random.scalaUtilRandom[IO].flatMap { implicit r =>
      Screept.execute[IO](code, env)
    }

  // ============ END-TO-END SCENARIOS ============

  test("simple calculator"):
    execute("result = (10 + 5) * 2 - 6 / 3; PRINT result").map { r =>
      assert(r.isRight)
      r.foreach { env =>
        assertEquals(env.vars.get("result"), Some(NumberValue(28.0)))
        assertEquals(env.output.map(_.value), List("28"))
      }
    }

  test("greeting generator"):
    execute("name = \"World\"; greeting = \"Hello, \" + name + \"!\"; PRINT greeting").map { r =>
      assert(r.isRight)
      r.foreach { env =>
        assertEquals(env.output.map(_.value), List("Hello, World!"))
      }
    }

  test("grade calculator"):
    val code = """
      score = 85;
      IF score > 90 THEN grade = "A"
      ELSE IF score > 80 THEN grade = "B"
      ELSE IF score > 70 THEN grade = "C"
      ELSE grade = "F";
      PRINT "Grade: " + grade
    """
    execute(code).map { r =>
      assert(r.isRight)
      r.foreach { env =>
        assertEquals(env.vars.get("grade"), Some(TextValue("B")))
      }
    }

  test("factorial-like accumulation"):
    val code = """
      n = 5;
      result = 1;
      result = result * 5;
      result = result * 4;
      result = result * 3;
      result = result * 2;
      result = result * 1;
      PRINT result
    """
    execute(code).map { r =>
      assert(r.isRight)
      r.foreach { env =>
        assertEquals(env.vars.get("result"), Some(NumberValue(120.0)))
        assertEquals(env.output.map(_.value), List("120"))
      }
    }

  test("min/max finder"):
    val code = """
      a = 15; b = 8; c = 23;
      max = a > b ? a : b;
      max = max > c ? max : c;
      min = a < b ? a : b;
      min = min < c ? min : c;
      PRINT "Max: " + max;
      PRINT "Min: " + min
    """
    execute(code).map { r =>
      assert(r.isRight)
      r.foreach { env =>
        assertEquals(env.vars.get("max"), Some(NumberValue(23.0)))
        assertEquals(env.vars.get("min"), Some(NumberValue(8.0)))
      }
    }

  test("string builder"):
    val code = """
      result = "";
      result = result + "Hello";
      result = result + " ";
      result = result + "World";
      result = result + "!";
      PRINT result
    """
    execute(code).map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.output.map(_.value), List("Hello World!")))
    }

  test("reusable procedures"):
    val code = """
      PROC square { _result = _0 * _0 };
      PROC cube { _result = _0 * _0 * _0 };
      RUN square(4);
      sq = _result;
      RUN cube(3);
      cu = _result;
      PRINT "4^2 = " + sq;
      PRINT "3^3 = " + cu
    """
    execute(code).map { r =>
      assert(r.isRight)
      r.foreach { env =>
        assertEquals(env.vars.get("sq"), Some(NumberValue(16.0)))
        assertEquals(env.vars.get("cu"), Some(NumberValue(27.0)))
      }
    }

  test("higher-order function pattern"):
    val code = """
      double = FUNC _0 * 2;
      triple = FUNC _0 * 3;
      x = 5;
      d = double(x);
      t = triple(x);
      PRINT d;
      PRINT t
    """
    execute(code).map { r =>
      assert(r.isRight)
      r.foreach { env =>
        assertEquals(env.vars.get("d"), Some(NumberValue(10.0)))
        assertEquals(env.vars.get("t"), Some(NumberValue(15.0)))
        assertEquals(env.output.map(_.value), List("10", "15"))
      }
    }

  test("computed identifiers for dynamic access"):
    val code = """
      item_1 = "apple";
      item_2 = "banana";
      item_3 = "cherry";
      index = 2;
      current = $["item_" + index];
      PRINT current
    """
    execute(code).map { r =>
      assert(r.isRight)
      r.foreach { env =>
        assertEquals(env.vars.get("current"), Some(TextValue("banana")))
        assertEquals(env.output.map(_.value), List("banana"))
      }
    }

  test("array simulation with computed identifiers"):
    val code = """
      arr_0 = 10;
      arr_1 = 20;
      arr_2 = 30;
      sum = arr_0 + arr_1 + arr_2;
      PRINT "Sum: " + sum
    """
    execute(code).map { r =>
      assert(r.isRight)
      r.foreach { env =>
        assertEquals(env.vars.get("sum"), Some(NumberValue(60.0)))
      }
    }

  test("coin flip simulation"):
    val code = """
      RND coin 0 1;
      result = coin == 1 ? "heads" : "tails";
      PRINT result
    """
    execute(code).map { r =>
      assert(r.isRight)
      r.foreach { env =>
        val result = env.vars.get("result")
        assert(result.contains(TextValue("heads")) || result.contains(TextValue("tails")))
      }
    }

  test("dice roll simulation"):
    val code = """
      RND d1 1 6;
      RND d2 1 6;
      total = d1 + d2;
      PRINT "Rolled: " + d1 + " + " + d2 + " = " + total
    """
    execute(code).map { r =>
      assert(r.isRight)
      r.foreach { env =>
        val total = env.vars.get("total")
        assert(total.isDefined)
        total.foreach {
          case NumberValue(v) => assert(v >= 2 && v <= 12)
          case _ => fail("Expected NumberValue")
        }
      }
    }

  test("state machine simulation"):
    val code = """
      state = "idle";
      IF state == "idle" THEN { state = "running"; PRINT "Started" };
      IF state == "running" THEN { state = "paused"; PRINT "Paused" };
      IF state == "paused" THEN { state = "running"; PRINT "Resumed" };
      PRINT "Final state: " + state
    """
    execute(code).map { r =>
      assert(r.isRight)
      r.foreach { env =>
        assertEquals(env.vars.get("state"), Some(TextValue("running")))
        assertEquals(env.output.map(_.value), List("Started", "Paused", "Resumed", "Final state: running"))
      }
    }

  test("boolean logic simulation"):
    val code = """
      a = 1;
      b = 0;
      and_result = a ? (b ? 1 : 0) : 0;
      or_result = a ? 1 : (b ? 1 : 0);
      xor_result = a ? (!b ? 1 : 0) : (b ? 1 : 0);
      PRINT "AND: " + and_result;
      PRINT "OR: " + or_result;
      PRINT "XOR: " + xor_result
    """
    execute(code).map { r =>
      assert(r.isRight)
      r.foreach { env =>
        assertEquals(env.vars.get("and_result"), Some(NumberValue(0.0)))
        assertEquals(env.vars.get("or_result"), Some(NumberValue(1.0)))
        assertEquals(env.vars.get("xor_result"), Some(NumberValue(1.0)))
      }
    }

  test("environment persistence across statements"):
    val env1 = Environment(vars = Map("initial" -> NumberValue(100.0)))
    execute("x = initial + 50; PRINT x", env1).map { r =>
      assert(r.isRight)
      r.foreach { env =>
        assertEquals(env.vars.get("x"), Some(NumberValue(150.0)))
        assertEquals(env.vars.get("initial"), Some(NumberValue(100.0)))
      }
    }

  // ============ ERROR HANDLING ============

  test("parse error returns Left"):
    execute("x = ").map { r =>
      assert(r.isLeft)
    }

  test("runtime error returns Left"):
    execute("x = undefined_var + 1").map { r =>
      assert(r.isLeft)
    }

  test("type error returns Left"):
    execute("x = \"hello\" - 1").map { r =>
      assert(r.isLeft)
    }

  // ============ EDGE CASES ============

  test("empty block"):
    execute("{ }").map { r =>
      assert(r.isRight)
    }

  test("deeply nested blocks"):
    execute("{ { { x = 1 } } }").map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.vars.get("x"), Some(NumberValue(1.0))))
    }

  test("long expression chain"):
    execute("x = 1 + 2 + 3 + 4 + 5 + 6 + 7 + 8 + 9 + 10").map { r =>
      assert(r.isRight)
      r.foreach(env => assertEquals(env.vars.get("x"), Some(NumberValue(55.0))))
    }

  test("many statements"):
    val stmts = (1 to 20).map(i => s"x$i = $i").mkString("; ")
    execute(stmts).map { r =>
      assert(r.isRight)
      r.foreach { env =>
        assertEquals(env.vars.get("x1"), Some(NumberValue(1.0)))
        assertEquals(env.vars.get("x20"), Some(NumberValue(20.0)))
      }
    }

class JsonCodecTest extends CatsEffectSuite:

  // ============ VALUE CODECS ============

  test("NumberValue roundtrip"):
    val v: Value = NumberValue(42.5)
    val json = v.asJson.noSpaces
    val decoded = decode[Value](json)
    assertEquals(decoded, Right(v))

  test("TextValue roundtrip"):
    val v: Value = TextValue("hello world")
    val json = v.asJson.noSpaces
    val decoded = decode[Value](json)
    assertEquals(decoded, Right(v))

  test("FuncValue roundtrip"):
    val v: Value = FuncValue(BinaryOp(BinaryOperator.Add, Var(LiteralId("_0")), Var(LiteralId("_1"))))
    val json = v.asJson.noSpaces
    val decoded = decode[Value](json)
    assertEquals(decoded, Right(v))

  // ============ OPERATOR CODECS ============

  test("UnaryOperator roundtrip"):
    UnaryOperator.values.foreach { op =>
      val json = op.asJson.noSpaces
      val decoded = decode[UnaryOperator](json)
      assertEquals(decoded, Right(op), s"Failed for $op")
    }

  test("BinaryOperator roundtrip"):
    BinaryOperator.values.foreach { op =>
      val json = op.asJson.noSpaces
      val decoded = decode[BinaryOperator](json)
      assertEquals(decoded, Right(op), s"Failed for $op")
    }

  // ============ IDENTIFIER CODECS ============

  test("LiteralId roundtrip"):
    val id: Identifier = LiteralId("my_var")
    val json = id.asJson.noSpaces
    val decoded = decode[Identifier](json)
    assertEquals(decoded, Right(id))

  test("ComputedId roundtrip"):
    val id: Identifier = ComputedId(Var(LiteralId("name")))
    val json = id.asJson.noSpaces
    val decoded = decode[Identifier](json)
    assertEquals(decoded, Right(id))

  // ============ EXPRESSION CODECS ============

  test("Literal expression roundtrip"):
    val expr: Expression = Literal(NumberValue(42.0))
    val json = expr.asJson.noSpaces
    val decoded = decode[Expression](json)
    assertEquals(decoded, Right(expr))

  test("UnaryOp expression roundtrip"):
    val expr: Expression = UnaryOp(UnaryOperator.Minus, Literal(NumberValue(5.0)))
    val json = expr.asJson.noSpaces
    val decoded = decode[Expression](json)
    assertEquals(decoded, Right(expr))

  test("BinaryOp expression roundtrip"):
    val expr: Expression = BinaryOp(BinaryOperator.Add, Literal(NumberValue(1.0)), Literal(NumberValue(2.0)))
    val json = expr.asJson.noSpaces
    val decoded = decode[Expression](json)
    assertEquals(decoded, Right(expr))

  test("Var expression roundtrip"):
    val expr: Expression = Var(LiteralId("x"))
    val json = expr.asJson.noSpaces
    val decoded = decode[Expression](json)
    assertEquals(decoded, Right(expr))

  test("Condition expression roundtrip"):
    val expr: Expression = Condition(
      Literal(NumberValue(1.0)),
      Literal(NumberValue(10.0)),
      Literal(NumberValue(20.0))
    )
    val json = expr.asJson.noSpaces
    val decoded = decode[Expression](json)
    assertEquals(decoded, Right(expr))

  test("FunCall expression roundtrip"):
    val expr: Expression = FunCall(LiteralId("foo"), List(Literal(NumberValue(1.0)), Literal(NumberValue(2.0))))
    val json = expr.asJson.noSpaces
    val decoded = decode[Expression](json)
    assertEquals(decoded, Right(expr))

  test("Parens expression roundtrip"):
    val expr: Expression = Parens(BinaryOp(BinaryOperator.Add, Literal(NumberValue(1.0)), Literal(NumberValue(2.0))))
    val json = expr.asJson.noSpaces
    val decoded = decode[Expression](json)
    assertEquals(decoded, Right(expr))

  test("complex nested expression roundtrip"):
    val expr: Expression = Condition(
      BinaryOp(BinaryOperator.Gt, Var(LiteralId("x")), Literal(NumberValue(0.0))),
      FunCall(LiteralId("f"), List(Var(LiteralId("x")))),
      BinaryOp(BinaryOperator.Mul, Var(LiteralId("x")), Literal(NumberValue(-1.0)))
    )
    val json = expr.asJson.noSpaces
    val decoded = decode[Expression](json)
    assertEquals(decoded, Right(expr))

  // ============ STATEMENT CODECS ============

  test("Bind statement roundtrip"):
    val stmt: Statement = Bind(LiteralId("x"), Literal(NumberValue(42.0)))
    val json = stmt.asJson.noSpaces
    val decoded = decode[Statement](json)
    assertEquals(decoded, Right(stmt))

  test("Print statement roundtrip"):
    val stmt: Statement = Print(Literal(TextValue("hello")))
    val json = stmt.asJson.noSpaces
    val decoded = decode[Statement](json)
    assertEquals(decoded, Right(stmt))

  test("Emit statement roundtrip"):
    val stmt: Statement = Emit(Literal(TextValue("event")))
    val json = stmt.asJson.noSpaces
    val decoded = decode[Statement](json)
    assertEquals(decoded, Right(stmt))

  test("Block statement roundtrip"):
    val stmt: Statement = Block(List(
      Bind(LiteralId("x"), Literal(NumberValue(1.0))),
      Bind(LiteralId("y"), Literal(NumberValue(2.0)))
    ))
    val json = stmt.asJson.noSpaces
    val decoded = decode[Statement](json)
    assertEquals(decoded, Right(stmt))

  test("ProcDef statement roundtrip"):
    val stmt: Statement = ProcDef(LiteralId("greet"), Print(Literal(TextValue("hello"))))
    val json = stmt.asJson.noSpaces
    val decoded = decode[Statement](json)
    assertEquals(decoded, Right(stmt))

  test("ProcRun statement roundtrip"):
    val stmt: Statement = ProcRun(LiteralId("greet"), List(Literal(TextValue("world"))))
    val json = stmt.asJson.noSpaces
    val decoded = decode[Statement](json)
    assertEquals(decoded, Right(stmt))

  test("RandomStmt statement roundtrip"):
    val stmt: Statement = RandomStmt(LiteralId("x"), Literal(NumberValue(1.0)), Literal(NumberValue(10.0)))
    val json = stmt.asJson.noSpaces
    val decoded = decode[Statement](json)
    assertEquals(decoded, Right(stmt))

  test("If statement roundtrip - without else"):
    val stmt: Statement = If(
      Literal(NumberValue(1.0)),
      Print(Literal(TextValue("true"))),
      None
    )
    val json = stmt.asJson.noSpaces
    val decoded = decode[Statement](json)
    assertEquals(decoded, Right(stmt))

  test("If statement roundtrip - with else"):
    val stmt: Statement = If(
      Literal(NumberValue(1.0)),
      Print(Literal(TextValue("true"))),
      Some(Print(Literal(TextValue("false"))))
    )
    val json = stmt.asJson.noSpaces
    val decoded = decode[Statement](json)
    assertEquals(decoded, Right(stmt))

  // ============ ENVIRONMENT CODEC ============

  test("Environment roundtrip"):
    val env = Environment(
      vars = Map(
        "x" -> NumberValue(42.0),
        "name" -> TextValue("test")
      ),
      procedures = Map(
        "greet" -> Print(Var(LiteralId("name")))
      ),
      output = List(OutputLine(12345L, "hello"))
    )
    val json = env.asJson.noSpaces
    val decoded = decode[Environment](json)
    assertEquals(decoded, Right(env))

  test("empty Environment roundtrip"):
    val env = Environment()
    val json = env.asJson.noSpaces
    val decoded = decode[Environment](json)
    assertEquals(decoded, Right(env))

  // ============ PARSE AND SERIALIZE ============

  test("parse expression and serialize"):
    Parser.parseExpression("1 + 2 * 3") match
      case Left(err) => fail(s"Parse failed: $err")
      case Right(expr) =>
        val json = expr.asJson.noSpaces
        val decoded = decode[Expression](json)
        assertEquals(decoded, Right(expr))

  test("parse statement and serialize"):
    Parser.parseStatement("x = 10; PRINT x") match
      case Left(err) => fail(s"Parse failed: $err")
      case Right(stmt) =>
        val json = stmt.asJson.noSpaces
        val decoded = decode[Statement](json)
        assertEquals(decoded, Right(stmt))

  test("parse complex program and serialize"):
    val code = """
      PROC greet { PRINT "Hello, " + _0 };
      name = "World";
      RUN greet(name)
    """
    Parser.parseStatement(code) match
      case Left(err) => fail(s"Parse failed: $err")
      case Right(stmt) =>
        val json = stmt.asJson.noSpaces
        val decoded = decode[Statement](json)
        assertEquals(decoded, Right(stmt))
