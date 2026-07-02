package com.crianonim.screept

/** Renders a Screept AST back to source parseable by [[Parser]].
  *
  * Binary and conditional expressions are fully parenthesized so that round-trips are
  * precedence-safe (`parseExpr(expr(e))` yields a structurally equivalent tree).
  */
object ScreeptPrinter:

  def expr(e: Expression): String = e match
    case Literal(v)         => value(v)
    case Var(id)            => ident(id)
    case Parens(inner)      => s"(${expr(inner)})"
    case UnaryOp(op, x)     => s"${unary(op)}${atom(x)}"
    case BinaryOp(op, x, y) => s"(${expr(x)} ${binary(op)} ${expr(y)})"
    case Condition(c, t, f) => s"(${expr(c)} ? ${expr(t)} : ${expr(f)})"
    case FunCall(id, args)  => s"${ident(id)}(${args.map(expr).mkString(", ")})"

  // Wrap in parens unless already atomic, so unary operands stay well-formed.
  private def atom(e: Expression): String = e match
    case _: Literal | _: Var | _: FunCall | _: Parens => expr(e)
    case _                                            => s"(${expr(e)})"

  private def value(v: Value): String = v match
    case NumberValue(x) => if x == x.toLong then x.toLong.toString else x.toString
    case TextValue(s)   => "\"" + s + "\""
    case FuncValue(b)   => s"FUNC ${expr(b)}"

  private def ident(id: Identifier): String = id match
    case LiteralId(name) => name
    case ComputedId(e)   => s"$$[${expr(e)}]"

  private def unary(op: UnaryOperator): String = op match
    case UnaryOperator.Plus  => "+"
    case UnaryOperator.Minus => "-"
    case UnaryOperator.Not   => "!"

  private def binary(op: BinaryOperator): String = op match
    case BinaryOperator.Add    => "+"
    case BinaryOperator.Sub    => "-"
    case BinaryOperator.Mul    => "*"
    case BinaryOperator.Div    => "/"
    case BinaryOperator.IntDiv => "//"
    case BinaryOperator.Eq     => "=="
    case BinaryOperator.Lt     => "<"
    case BinaryOperator.Gt     => ">"

  def stmt(s: Statement): String = s match
    case Bind(id, v)           => s"${ident(id)} = ${expr(v)}"
    case Print(v)              => s"PRINT ${expr(v)}"
    case Emit(v)               => s"EMIT ${expr(v)}"
    case Block(stmts)          => s"{ ${stmts.map(stmt).mkString("; ")} }"
    case ProcDef(id, body)     => s"PROC ${ident(id)} ${stmt(body)}"
    case ProcRun(id, args)     => s"RUN ${ident(id)}(${args.map(expr).mkString(", ")})"
    case RandomStmt(id, f, to) => s"RND ${ident(id)} ${expr(f)} ${expr(to)}"
    case If(c, thenS, elseS) =>
      val base = s"IF ${expr(c)} THEN ${stmt(thenS)}"
      elseS.fold(base)(e => s"$base ELSE ${stmt(e)}")
