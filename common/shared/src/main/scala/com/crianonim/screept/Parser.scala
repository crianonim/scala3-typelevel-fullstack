package com.crianonim.screept

import fastparse._
import fastparse.NoWhitespace._

object Parser:
  // ============ WHITESPACE HANDLING ============
  private def ws[$: P]: P[Unit] = P(CharsWhileIn(" \t\r\n", 0))
  private def ws1[$: P]: P[Unit] = P(CharsWhileIn(" \t\r\n", 1))

  // ============ BASIC TOKENS ============
  private def number[$: P]: P[Double] =
    P(("-".? ~ CharsWhileIn("0-9", 1) ~ ("." ~ CharsWhileIn("0-9", 1)).?).!).map(_.toDouble)

  private def stringLiteral[$: P]: P[String] =
    P("\"" ~ CharsWhile(c => c != '"', 0).! ~ "\"")

  private def identifierName[$: P]: P[String] =
    P((CharIn("a-z_") ~ CharsWhileIn("a-zA-Z_0-9", 0)).!)

  // ============ IDENTIFIERS ============
  private def literalId[$: P]: P[LiteralId] =
    P(identifierName).map(LiteralId.apply)

  private def computedId[$: P]: P[ComputedId] =
    P("$[" ~ ws ~ conditionalExpr ~ ws ~ "]").map(ComputedId.apply)

  private def identifier[$: P]: P[Identifier] =
    P(computedId | literalId)

  // ============ VALUES ============
  private def numberValue[$: P]: P[Value] =
    P(number).map(NumberValue.apply)

  private def textValue[$: P]: P[Value] =
    P(stringLiteral).map(TextValue.apply)

  private def funcValue[$: P]: P[Value] =
    P("FUNC" ~ ws1 ~ conditionalExpr).map(FuncValue.apply)

  // ============ EXPRESSIONS ============

  // Atoms: literals, variables, function calls, parenthesized expressions
  private def atom[$: P]: P[Expression] =
    P(
      // Parenthesized expression (must be before function call)
      ("(" ~ ws ~ conditionalExpr ~ ws ~ ")").map(Parens.apply) |
        // FUNC literal
        funcValue.map(Literal.apply) |
        // String literal (must be before identifier)
        textValue.map(Literal.apply) |
        // Number literal
        numberValue.map(Literal.apply) |
        // Function call: identifier(args)
        (identifier ~ ws ~ "(" ~ ws ~ argList.? ~ ws ~ ")").map { case (id, args) =>
          FunCall(id, args.getOrElse(Nil))
        } |
        // Variable reference
        identifier.map(Var.apply)
    )

  private def argList[$: P]: P[List[Expression]] =
    P(conditionalExpr.rep(sep = ws ~ "," ~ ws)).map(_.toList)

  // Unary: +x, -x, !x
  private def unaryExpr[$: P]: P[Expression] =
    P(
      (CharIn("+\\-!").! ~ ws ~ unaryExpr).map { case (op, expr) =>
        val opType = op match
          case "+" => UnaryOperator.Plus
          case "-" => UnaryOperator.Minus
          case "!" => UnaryOperator.Not
        UnaryOp(opType, expr)
      } |
        atom
    )

  // Factor: *, /, //
  private def factorExpr[$: P]: P[Expression] =
    P(unaryExpr ~ (ws ~ ("//".! | "/".! | "*".!) ~ ws ~ unaryExpr).rep).map { case (first, rest) =>
      rest.foldLeft(first) { case (left, (op, right)) =>
        val opType = op match
          case "*"  => BinaryOperator.Mul
          case "/"  => BinaryOperator.Div
          case "//" => BinaryOperator.IntDiv
        BinaryOp(opType, left, right)
      }
    }

  // Term: +, -
  private def termExpr[$: P]: P[Expression] =
    P(factorExpr ~ (ws ~ ("+".! | "-".!) ~ ws ~ factorExpr).rep).map { case (first, rest) =>
      rest.foldLeft(first) { case (left, (op, right)) =>
        val opType = op match
          case "+" => BinaryOperator.Add
          case "-" => BinaryOperator.Sub
        BinaryOp(opType, left, right)
      }
    }

  // Comparison: ==, <, >
  private def comparisonExpr[$: P]: P[Expression] =
    P(termExpr ~ (ws ~ ("==".! | "<".! | ">".!) ~ ws ~ termExpr).rep).map { case (first, rest) =>
      rest.foldLeft(first) { case (left, (op, right)) =>
        val opType = op match
          case "==" => BinaryOperator.Eq
          case "<"  => BinaryOperator.Lt
          case ">"  => BinaryOperator.Gt
        BinaryOp(opType, left, right)
      }
    }

  // Conditional: cond ? onTrue : onFalse
  private def conditionalExpr[$: P]: P[Expression] =
    P(
      (comparisonExpr ~ ws ~ "?" ~ ws ~ conditionalExpr ~ ws ~ ":" ~ ws ~ conditionalExpr).map {
        case (cond, onTrue, onFalse) => Condition(cond, onTrue, onFalse)
      } |
        comparisonExpr
    )

  // ============ STATEMENTS ============

  private def printStmt[$: P]: P[Statement] =
    P("PRINT" ~ ws1 ~ conditionalExpr).map(Print.apply)

  private def emitStmt[$: P]: P[Statement] =
    P("EMIT" ~ ws1 ~ conditionalExpr).map(Emit.apply)

  private def bindStmt[$: P]: P[Statement] =
    P(identifier ~ ws ~ "=" ~ ws ~ conditionalExpr).map { case (id, expr) =>
      Bind(id, expr)
    }

  private def blockStmt[$: P]: P[Statement] =
    P("{" ~ ws ~ statement.rep(sep = ws ~ ";" ~ ws) ~ ws ~ ";".? ~ ws ~ "}").map { stmts =>
      Block(stmts.toList)
    }

  private def procDefStmt[$: P]: P[Statement] =
    P("PROC" ~ ws1 ~ identifier ~ ws1 ~ statement).map { case (id, stmt) =>
      ProcDef(id, stmt)
    }

  private def procRunStmt[$: P]: P[Statement] =
    P("RUN" ~ ws1 ~ identifier ~ ws ~ "(" ~ ws ~ argList.? ~ ws ~ ")").map { case (id, args) =>
      ProcRun(id, args.getOrElse(Nil))
    }

  private def randomStmt[$: P]: P[Statement] =
    P("RND" ~ ws1 ~ identifier ~ ws1 ~ conditionalExpr ~ ws1 ~ conditionalExpr).map {
      case (id, from, to) => RandomStmt(id, from, to)
    }

  private def ifStmt[$: P]: P[Statement] =
    P(
      "IF" ~ ws1 ~ conditionalExpr ~ ws1 ~ "THEN" ~ ws1 ~ statement ~
        (ws1 ~ "ELSE" ~ ws1 ~ statement).?
    ).map { case (cond, thenS, elseS) =>
      If(cond, thenS, elseS)
    }

  private def statement[$: P]: P[Statement] =
    P(
      printStmt |
        emitStmt |
        blockStmt |
        procDefStmt |
        procRunStmt |
        randomStmt |
        ifStmt |
        bindStmt
    )

  // Multiple statements separated by semicolons
  private def program[$: P]: P[Statement] =
    P(ws ~ statement.rep(sep = ws ~ ";" ~ ws, min = 1) ~ ws ~ ";".? ~ ws ~ End).map { stmts =>
      if stmts.size == 1 then stmts.head
      else Block(stmts.toList)
    }

  // ============ PUBLIC API ============

  private def expressionParser[$: P]: P[Expression] =
    P(ws ~ conditionalExpr ~ ws ~ End)

  def parseExpression(input: String): Either[String, Expression] =
    parse(input, expressionParser(using _)) match
      case Parsed.Success(expr, _) => Right(expr)
      case Parsed.Failure(label, idx, extra) =>
        Left(s"Parse error at position $idx: expected $label")

  def parseStatement(input: String): Either[String, Statement] =
    parse(input, program(using _)) match
      case Parsed.Success(stmt, _) => Right(stmt)
      case Parsed.Failure(label, idx, extra) =>
        Left(s"Parse error at position $idx: expected $label")
