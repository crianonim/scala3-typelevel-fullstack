package com.crianonim.ui

import cats.effect.*
import tyrian.*
import tyrian.Html.*
import com.crianonim.ui.Button
import com.crianonim.ui.Input

object Preview {

  case class Model(
    inputValue: String,
    buttonClickCount: Int
  )

  enum Msg {
    case Noop
    case InputChanged(value: String)
    case ButtonClicked
  }

  def init: Model = Model("", 0)

  def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = {
    case Msg.Noop => (model, Cmd.None)
    case Msg.InputChanged(value) => (model.copy(inputValue = value), Cmd.None)
    case Msg.ButtonClicked => (model.copy(buttonClickCount = model.buttonClickCount + 1), Cmd.None)
  }

  def view(model: Model): Html[Msg] = {
    div(cls := "flex flex-col gap-8 max-w-4xl")(
      h1(cls := "text-3xl font-bold")("UI Components Preview"),

      // Button Component
      componentRow(
        "Button",
        "Interactive button component with click handler",
        div(cls := "flex flex-col gap-2")(
          Button.interactive("Click Me", Msg.ButtonClicked),
          div(cls := "text-sm text-gray-600")(s"Clicked ${model.buttonClickCount} times")
        )
      ),

      // Input Component - Text
      componentRow(
        "Input (Text)",
        "Text input component with onChange handler",
        div(cls := "flex flex-col gap-2")(
          Input.interactive(model.inputValue, Msg.InputChanged.apply, "text"),
          div(cls := "text-sm text-gray-600")(s"Current value: '${model.inputValue}'")
        )
      ),

      // Input Component - Number
      componentRow(
        "Input (Number)",
        "Number input component",
        div(cls := "flex flex-col gap-2")(
          Input.interactive(model.inputValue, Msg.InputChanged.apply, "number"),
          div(cls := "text-sm text-gray-600")("Type: number")
        )
      )
    )
  }

  private def componentRow(name: String, description: String, component: Html[Msg]): Html[Msg] = {
    div(cls := "border border-gray-300 rounded-lg p-6")(
      div(cls := "flex flex-col gap-4")(
        div(cls := "flex flex-col gap-1")(
          h2(cls := "text-xl font-semibold")(name),
          p(cls := "text-gray-600")(description)
        ),
        div(cls := "bg-gray-50 p-4 rounded border border-gray-200")(
          component
        )
      )
    )
  }
}
