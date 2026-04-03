package com.crianonim.screept

import cats.effect.*
import cats.effect.std.Random
import tyrian.*
import tyrian.Html.*

import com.crianonim.ui.Button

object ScreeptApp {

  case class Model(
      code: String,
      result: Option[Either[String, Environment]],
  )

  enum Msg {
    case UpdateCode(code: String)
    case Run
    case GotResult(result: Either[String, Environment])
  }

  def init: Model = Model("", None)

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

  def view(model: Model): Html[Msg] =
    div(cls := "flex gap-4 h-[calc(100vh-12rem)]")(
      // Left column: code editor + run button
      div(cls := "flex flex-col gap-2 w-1/2")(
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
