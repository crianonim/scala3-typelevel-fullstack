package com.crianonim.screept

import cats.effect.*
import cats.effect.std.Random
import tyrian.*
import tyrian.Html.*

import com.crianonim.ui.Button
import com.crianonim.ui.Modal

object ScreeptApp {

  case class Model(
      code: String,
      result: Option[Either[String, Environment]],
      showHelp: Boolean
  )

  enum Msg {
    case UpdateCode(code: String)
    case Run
    case GotResult(result: Either[String, Environment])
    case OpenHelp
    case CloseHelp
  }

  def init: Model = Model("", None, showHelp = false)

  def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = {
    case Msg.UpdateCode(code) =>
      (model.copy(code = code), Cmd.None)

    case Msg.Run =>
      val runCmd = Cmd.Run {
        Random.scalaUtilRandom[IO].flatMap { implicit r =>
          given Random[IO] = r
          Screept.execute[IO](model.code)
        }
      }(Msg.GotResult.apply)
      (model, runCmd)

    case Msg.GotResult(result) =>
      (model.copy(result = Some(result)), Cmd.None)

    case Msg.OpenHelp =>
      (model.copy(showHelp = true), Cmd.None)

    case Msg.CloseHelp =>
      (model.copy(showHelp = false), Cmd.None)
  }

  private def viewBindings(env: Environment): Html[Msg] =
    val varRows = env.vars.toList.sortBy(_._1).map { case (name, value) =>
      tr()(
        td(cls := "px-2 py-1 font-mono text-sm border-b border-gray-200")(text(name)),
        td(cls := "px-2 py-1 font-mono text-sm border-b border-gray-200")(
          text(Evaluator.getStringValue(value))
        ),
        td(cls := "px-2 py-1 text-xs text-gray-500 border-b border-gray-200")(
          text(value match {
            case _: NumberValue => "number"
            case _: TextValue   => "text"
            case _: FuncValue   => "func"
          })
        )
      )
    }
    val procRows = env.procedures.keys.toList.sorted.map { name =>
      tr()(
        td(cls := "px-2 py-1 font-mono text-sm border-b border-gray-200")(text(name)),
        td(cls := "px-2 py-1 font-mono text-sm border-b border-gray-200")(text("...")),
        td(cls := "px-2 py-1 text-xs text-gray-500 border-b border-gray-200")(text("proc"))
      )
    }
    val allRows = varRows ++ procRows
    if allRows.isEmpty then div(cls := "text-gray-400 text-sm italic")(text("No bindings"))
    else
      table(cls := "w-full")(
        thead()(
          tr()(
            th(cls := "px-2 py-1 text-left text-xs font-semibold text-gray-600 border-b border-gray-300")(text("Name")),
            th(cls := "px-2 py-1 text-left text-xs font-semibold text-gray-600 border-b border-gray-300")(text("Value")),
            th(cls := "px-2 py-1 text-left text-xs font-semibold text-gray-600 border-b border-gray-300")(text("Type"))
          )
        ),
        tbody()(allRows)
      )

  private def syntaxHelp: List[Html[Msg]] =
    def section(title: String, content: List[Html[Msg]]): Html[Msg] =
      div(cls := "mb-4")(
        h3(cls := "font-semibold text-gray-800 mb-1")(text(title)) :: content
      )
    def code(s: String): Html[Msg] =
      tag("pre")(cls := "bg-gray-50 rounded px-3 py-2 font-mono text-sm text-gray-700 overflow-x-auto")(text(s))
    def row(syntax: String, desc: String): Html[Msg] =
      tr()(
        td(cls := "pr-4 py-0.5 font-mono text-sm whitespace-nowrap")(text(syntax)),
        td(cls := "py-0.5 text-sm text-gray-600")(text(desc))
      )
    List(
      section("Types", List(
        table(cls := "mb-2")(
          row("42, 3.14", "number"),
          row("\"hello\"", "text"),
          row("FUNC expr", "function value")
        )
      )),
      section("Variables", List(
        code("x = 10\nname = \"Alice\"")
      )),
      section("Operators", List(
        table()(
          row("+ - * / //", "arithmetic (// = integer div)"),
          row("+", "string concatenation"),
          row("== < >", "comparison (returns 1 or 0)"),
          row("! -", "unary not, negate"),
          row("c ? a : b", "conditional expression")
        )
      )),
      section("Output", List(
        code("PRINT \"hello \" + name")
      )),
      section("Conditionals", List(
        code("IF x > 5 THEN PRINT \"big\" ELSE PRINT \"small\"")
      )),
      section("Blocks", List(
        code("{\n  x = 10\n  PRINT x\n}")
      )),
      section("Functions", List(
        code("add = FUNC _0 + _1\nPRINT add(3, 4)")
      )),
      section("Procedures", List(
        code("PROC greet {\n  PRINT \"Hello \"\n  PRINT _0\n}\nRUN greet(\"World\")")
      )),
      section("Random", List(
        code("RND x 1 6\nPRINT x")
      )),
      section("Separators", List(
        div(cls := "text-sm text-gray-600")(text("Statements separated by ; or newlines. Args: _0, _1, ..."))
      ))
    )

  def view(model: Model): Html[Msg] =
    div(cls := "flex gap-4 h-[calc(100vh-12rem)]")(
      Modal.withTitle(model.showHelp, Msg.CloseHelp, "Screept Syntax", Modal.Size.Large)(
        syntaxHelp*
      ),
      // Left column: code editor + run button
      div(cls := "flex flex-col gap-2 w-1/2")(
        div(cls := "flex justify-end")(
          Button.secondary("? Syntax", Msg.OpenHelp, Button.Size.Small)
        ),
        textarea(
          cls := "flex-1 font-mono text-sm p-3 border border-gray-300 rounded-md resize-none focus:outline-none focus:ring-2 focus:ring-blue-500",
          placeholder := "Enter screept code...",
          onInput(Msg.UpdateCode.apply),
          value := model.code
        )(),
        Button.primary("Run", Msg.Run)
      ),
      // Right column: result + bindings
      div(cls := "flex flex-col gap-4 w-1/2")(
        // Result pane
        div(cls := "flex flex-col flex-1 border border-gray-300 rounded-md overflow-hidden")(
          div(cls := "px-3 py-1.5 bg-gray-100 text-xs font-semibold text-gray-600 border-b border-gray-300")(
            text("Output")
          ),
          div(cls := "flex-1 overflow-auto p-3 font-mono text-sm bg-white")(
            model.result match {
              case None => div(cls := "text-gray-400 italic")(text("Run code to see output"))
              case Some(Left(err)) =>
                div(cls := "text-red-600")(text(err))
              case Some(Right(env)) =>
                div()(
                  env.output.map(line => div()(text(line.value)))
                )
            }
          )
        ),
        // Bindings pane
        div(cls := "flex flex-col flex-1 border border-gray-300 rounded-md overflow-hidden")(
          div(cls := "px-3 py-1.5 bg-gray-100 text-xs font-semibold text-gray-600 border-b border-gray-300")(
            text("Bindings")
          ),
          div(cls := "flex-1 overflow-auto p-3 bg-white")(
            model.result match {
              case None => div(cls := "text-gray-400 text-sm italic")(text("No bindings"))
              case Some(Left(_)) => div(cls := "text-gray-400 text-sm italic")(text("No bindings"))
              case Some(Right(env)) => viewBindings(env)
            }
          )
        )
      )
    )

  def subscriptions(model: Model): Sub[IO, Msg] =
    Sub.None
}
