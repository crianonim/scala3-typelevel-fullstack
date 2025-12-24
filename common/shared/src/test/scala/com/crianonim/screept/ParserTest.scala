package com.crianonim.screept

import munit.FunSuite

class ParserTest extends FunSuite:

  // ============ NUMBER PARSING ============

  test("parse integer number"):
    assertEquals(
      Parser.parseExpression("42"),
      Right(Literal(NumberValue(42.0)))
    )

  test("parse decimal number"):
    assertEquals(
      Parser.parseExpression("3.14"),
      Right(Literal(NumberValue(3.14)))
    )

  test("parse negative number"):
    assertEquals(
      Parser.parseExpression("-5"),
      Right(UnaryOp(UnaryOperator.Minus, Literal(NumberValue(5.0))))
    )

  test("parse zero"):
    assertEquals(
      Parser.parseExpression("0"),
      Right(Literal(NumberValue(0.0)))
    )

  // ============ STRING PARSING ============

  test("parse simple string"):
    assertEquals(
      Parser.parseExpression("\"hello\""),
      Right(Literal(TextValue("hello")))
    )

  test("parse empty string"):
    assertEquals(
      Parser.parseExpression("\"\""),
      Right(Literal(TextValue("")))
    )

  test("parse string with spaces"):
    assertEquals(
      Parser.parseExpression("\"hello world\""),
      Right(Literal(TextValue("hello world")))
    )

  test("parse string with numbers"):
    assertEquals(
      Parser.parseExpression("\"test123\""),
      Right(Literal(TextValue("test123")))
    )

  // ============ VARIABLE PARSING ============

  test("parse simple variable"):
    assertEquals(
      Parser.parseExpression("x"),
      Right(Var(LiteralId("x")))
    )

  test("parse variable with underscore"):
    assertEquals(
      Parser.parseExpression("my_var"),
      Right(Var(LiteralId("my_var")))
    )

  test("parse variable with numbers"):
    assertEquals(
      Parser.parseExpression("var123"),
      Right(Var(LiteralId("var123")))
    )

  test("parse underscore-prefixed variable"):
    assertEquals(
      Parser.parseExpression("_0"),
      Right(Var(LiteralId("_0")))
    )

  // ============ COMPUTED IDENTIFIER PARSING ============

  test("parse computed identifier with variable"):
    assertEquals(
      Parser.parseExpression("$[x]"),
      Right(Var(ComputedId(Var(LiteralId("x")))))
    )

  test("parse computed identifier with expression"):
    assertEquals(
      Parser.parseExpression("$[1 + 2]"),
      Right(Var(ComputedId(BinaryOp(BinaryOperator.Add, Literal(NumberValue(1.0)), Literal(NumberValue(2.0))))))
    )

  // ============ UNARY OPERATORS ============

  test("parse unary plus"):
    assertEquals(
      Parser.parseExpression("+5"),
      Right(UnaryOp(UnaryOperator.Plus, Literal(NumberValue(5.0))))
    )

  test("parse unary minus"):
    assertEquals(
      Parser.parseExpression("-10"),
      Right(UnaryOp(UnaryOperator.Minus, Literal(NumberValue(10.0))))
    )

  test("parse logical not"):
    assertEquals(
      Parser.parseExpression("!x"),
      Right(UnaryOp(UnaryOperator.Not, Var(LiteralId("x"))))
    )

  test("parse double negation"):
    assertEquals(
      Parser.parseExpression("--5"),
      Right(UnaryOp(UnaryOperator.Minus, UnaryOp(UnaryOperator.Minus, Literal(NumberValue(5.0)))))
    )

  // ============ BINARY OPERATORS ============

  test("parse addition"):
    assertEquals(
      Parser.parseExpression("1 + 2"),
      Right(BinaryOp(BinaryOperator.Add, Literal(NumberValue(1.0)), Literal(NumberValue(2.0))))
    )

  test("parse subtraction"):
    assertEquals(
      Parser.parseExpression("5 - 3"),
      Right(BinaryOp(BinaryOperator.Sub, Literal(NumberValue(5.0)), Literal(NumberValue(3.0))))
    )

  test("parse multiplication"):
    assertEquals(
      Parser.parseExpression("4 * 2"),
      Right(BinaryOp(BinaryOperator.Mul, Literal(NumberValue(4.0)), Literal(NumberValue(2.0))))
    )

  test("parse division"):
    assertEquals(
      Parser.parseExpression("10 / 2"),
      Right(BinaryOp(BinaryOperator.Div, Literal(NumberValue(10.0)), Literal(NumberValue(2.0))))
    )

  test("parse integer division"):
    assertEquals(
      Parser.parseExpression("7 // 2"),
      Right(BinaryOp(BinaryOperator.IntDiv, Literal(NumberValue(7.0)), Literal(NumberValue(2.0))))
    )

  test("parse equality"):
    assertEquals(
      Parser.parseExpression("x == 5"),
      Right(BinaryOp(BinaryOperator.Eq, Var(LiteralId("x")), Literal(NumberValue(5.0))))
    )

  test("parse less than"):
    assertEquals(
      Parser.parseExpression("x < 10"),
      Right(BinaryOp(BinaryOperator.Lt, Var(LiteralId("x")), Literal(NumberValue(10.0))))
    )

  test("parse greater than"):
    assertEquals(
      Parser.parseExpression("x > 0"),
      Right(BinaryOp(BinaryOperator.Gt, Var(LiteralId("x")), Literal(NumberValue(0.0))))
    )

  // ============ OPERATOR PRECEDENCE ============

  test("multiplication before addition"):
    // 1 + 2 * 3 = 1 + (2 * 3) = 7
    val result = Parser.parseExpression("1 + 2 * 3")
    assertEquals(
      result,
      Right(BinaryOp(
        BinaryOperator.Add,
        Literal(NumberValue(1.0)),
        BinaryOp(BinaryOperator.Mul, Literal(NumberValue(2.0)), Literal(NumberValue(3.0)))
      ))
    )

  test("division before subtraction"):
    // 10 - 6 / 2 = 10 - (6 / 2) = 7
    val result = Parser.parseExpression("10 - 6 / 2")
    assertEquals(
      result,
      Right(BinaryOp(
        BinaryOperator.Sub,
        Literal(NumberValue(10.0)),
        BinaryOp(BinaryOperator.Div, Literal(NumberValue(6.0)), Literal(NumberValue(2.0)))
      ))
    )

  test("parentheses override precedence"):
    // (1 + 2) * 3 = 9
    val result = Parser.parseExpression("(1 + 2) * 3")
    assertEquals(
      result,
      Right(BinaryOp(
        BinaryOperator.Mul,
        Parens(BinaryOp(BinaryOperator.Add, Literal(NumberValue(1.0)), Literal(NumberValue(2.0)))),
        Literal(NumberValue(3.0))
      ))
    )

  test("left associativity for addition"):
    // 1 + 2 + 3 = (1 + 2) + 3
    val result = Parser.parseExpression("1 + 2 + 3")
    assertEquals(
      result,
      Right(BinaryOp(
        BinaryOperator.Add,
        BinaryOp(BinaryOperator.Add, Literal(NumberValue(1.0)), Literal(NumberValue(2.0))),
        Literal(NumberValue(3.0))
      ))
    )

  test("comparison after arithmetic"):
    // 1 + 2 == 3
    val result = Parser.parseExpression("1 + 2 == 3")
    assertEquals(
      result,
      Right(BinaryOp(
        BinaryOperator.Eq,
        BinaryOp(BinaryOperator.Add, Literal(NumberValue(1.0)), Literal(NumberValue(2.0))),
        Literal(NumberValue(3.0))
      ))
    )

  // ============ CONDITIONAL EXPRESSIONS ============

  test("parse simple conditional"):
    val result = Parser.parseExpression("1 ? 2 : 3")
    assertEquals(
      result,
      Right(Condition(
        Literal(NumberValue(1.0)),
        Literal(NumberValue(2.0)),
        Literal(NumberValue(3.0))
      ))
    )

  test("parse conditional with comparison"):
    val result = Parser.parseExpression("x > 0 ? 1 : 0")
    assertEquals(
      result,
      Right(Condition(
        BinaryOp(BinaryOperator.Gt, Var(LiteralId("x")), Literal(NumberValue(0.0))),
        Literal(NumberValue(1.0)),
        Literal(NumberValue(0.0))
      ))
    )

  test("parse nested conditional in else branch"):
    val result = Parser.parseExpression("a ? 1 : b ? 2 : 3")
    assert(result.isRight)

  // ============ FUNCTION CALLS ============

  test("parse function call with no args"):
    assertEquals(
      Parser.parseExpression("foo()"),
      Right(FunCall(LiteralId("foo"), Nil))
    )

  test("parse function call with one arg"):
    assertEquals(
      Parser.parseExpression("foo(1)"),
      Right(FunCall(LiteralId("foo"), List(Literal(NumberValue(1.0)))))
    )

  test("parse function call with multiple args"):
    assertEquals(
      Parser.parseExpression("add(1, 2)"),
      Right(FunCall(LiteralId("add"), List(Literal(NumberValue(1.0)), Literal(NumberValue(2.0)))))
    )

  test("parse function call with expression args"):
    val result = Parser.parseExpression("calc(1 + 2, x * 3)")
    assert(result.isRight)
    result.foreach { expr =>
      assert(expr.isInstanceOf[FunCall])
    }

  // ============ FUNC LITERALS ============

  test("parse simple FUNC literal"):
    val result = Parser.parseExpression("FUNC _0 + 1")
    assertEquals(
      result,
      Right(Literal(FuncValue(BinaryOp(BinaryOperator.Add, Var(LiteralId("_0")), Literal(NumberValue(1.0))))))
    )

  test("parse FUNC with two parameters"):
    val result = Parser.parseExpression("FUNC _0 + _1")
    assertEquals(
      result,
      Right(Literal(FuncValue(BinaryOp(BinaryOperator.Add, Var(LiteralId("_0")), Var(LiteralId("_1"))))))
    )

  // ============ STATEMENT PARSING ============

  test("parse variable binding"):
    assertEquals(
      Parser.parseStatement("x = 10"),
      Right(Bind(LiteralId("x"), Literal(NumberValue(10.0))))
    )

  test("parse PRINT statement"):
    assertEquals(
      Parser.parseStatement("PRINT 42"),
      Right(Print(Literal(NumberValue(42.0))))
    )

  test("parse PRINT with expression"):
    val result = Parser.parseStatement("PRINT x + 1")
    assertEquals(
      result,
      Right(Print(BinaryOp(BinaryOperator.Add, Var(LiteralId("x")), Literal(NumberValue(1.0)))))
    )

  test("parse EMIT statement"):
    assertEquals(
      Parser.parseStatement("EMIT \"event\""),
      Right(Emit(Literal(TextValue("event"))))
    )

  test("parse block with single statement"):
    assertEquals(
      Parser.parseStatement("{ x = 1 }"),
      Right(Block(List(Bind(LiteralId("x"), Literal(NumberValue(1.0))))))
    )

  test("parse block with multiple statements"):
    val result = Parser.parseStatement("{ x = 1; y = 2 }")
    assertEquals(
      result,
      Right(Block(List(
        Bind(LiteralId("x"), Literal(NumberValue(1.0))),
        Bind(LiteralId("y"), Literal(NumberValue(2.0)))
      )))
    )

  test("parse block with trailing semicolon"):
    val result = Parser.parseStatement("{ x = 1; }")
    assert(result.isRight)

  test("parse IF-THEN statement"):
    val result = Parser.parseStatement("IF x > 0 THEN PRINT x")
    assertEquals(
      result,
      Right(If(
        BinaryOp(BinaryOperator.Gt, Var(LiteralId("x")), Literal(NumberValue(0.0))),
        Print(Var(LiteralId("x"))),
        None
      ))
    )

  test("parse IF-THEN-ELSE statement"):
    val result = Parser.parseStatement("IF x > 0 THEN PRINT x ELSE PRINT 0")
    assertEquals(
      result,
      Right(If(
        BinaryOp(BinaryOperator.Gt, Var(LiteralId("x")), Literal(NumberValue(0.0))),
        Print(Var(LiteralId("x"))),
        Some(Print(Literal(NumberValue(0.0))))
      ))
    )

  test("parse IF with block body"):
    val result = Parser.parseStatement("IF x THEN { a = 1; b = 2 }")
    assert(result.isRight)

  test("parse PROC definition"):
    val result = Parser.parseStatement("PROC greet PRINT \"hello\"")
    assertEquals(
      result,
      Right(ProcDef(LiteralId("greet"), Print(Literal(TextValue("hello")))))
    )

  test("parse PROC definition with block"):
    val result = Parser.parseStatement("PROC foo { x = 1; PRINT x }")
    assert(result.isRight)

  test("parse RUN statement"):
    assertEquals(
      Parser.parseStatement("RUN foo()"),
      Right(ProcRun(LiteralId("foo"), Nil))
    )

  test("parse RUN with arguments"):
    assertEquals(
      Parser.parseStatement("RUN greet(\"world\")"),
      Right(ProcRun(LiteralId("greet"), List(Literal(TextValue("world")))))
    )

  test("parse RND statement"):
    assertEquals(
      Parser.parseStatement("RND x 1 10"),
      Right(RandomStmt(LiteralId("x"), Literal(NumberValue(1.0)), Literal(NumberValue(10.0))))
    )

  test("parse RND with expressions"):
    val result = Parser.parseStatement("RND x min max")
    assertEquals(
      result,
      Right(RandomStmt(LiteralId("x"), Var(LiteralId("min")), Var(LiteralId("max"))))
    )

  // ============ MULTIPLE STATEMENTS ============

  test("parse multiple statements separated by semicolon"):
    val result = Parser.parseStatement("x = 1; y = 2; PRINT x + y")
    assert(result.isRight)
    result.foreach {
      case Block(stmts) => assertEquals(stmts.length, 3)
      case _ => fail("Expected Block")
    }

  test("parse statements with trailing semicolon"):
    val result = Parser.parseStatement("x = 1; y = 2;")
    assert(result.isRight)

  // ============ COMPLEX EXPRESSIONS ============

  test("parse complex nested expression"):
    val result = Parser.parseExpression("(a + b) * (c - d) / e")
    assert(result.isRight)

  test("parse chained function calls"):
    val result = Parser.parseExpression("f(g(x))")
    assert(result.isRight)

  test("parse function call in conditional"):
    val result = Parser.parseExpression("f(x) > 0 ? 1 : 0")
    assert(result.isRight)

  // ============ WHITESPACE HANDLING ============

  test("parse expression with extra whitespace"):
    val result = Parser.parseExpression("  1  +  2  ")
    assert(result.isRight)

  test("parse expression with no whitespace"):
    val result = Parser.parseExpression("1+2*3")
    assert(result.isRight)

  test("parse expression with newlines"):
    val result = Parser.parseExpression("1\n+\n2")
    assert(result.isRight)

  // ============ ERROR CASES ============

  test("fail on empty input"):
    val result = Parser.parseExpression("")
    assert(result.isLeft)

  test("fail on incomplete expression"):
    val result = Parser.parseExpression("1 +")
    assert(result.isLeft)

  test("fail on mismatched parentheses"):
    val result = Parser.parseExpression("(1 + 2")
    assert(result.isLeft)

  test("fail on invalid identifier starting with number"):
    // "123abc" should parse as 123 followed by unexpected "abc"
    val result = Parser.parseExpression("123abc")
    assert(result.isLeft)

  test("fail on unclosed string"):
    val result = Parser.parseExpression("\"hello")
    assert(result.isLeft)
