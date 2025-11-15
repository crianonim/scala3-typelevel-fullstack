package com.crianonim.ui

import cats.effect.*
import tyrian.*
import tyrian.Html.*
import com.crianonim.ui.Button
import com.crianonim.ui.Input
import com.crianonim.ui.Tooltip

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
    case Msg.Noop                => (model, Cmd.None)
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
      ),

      // Tooltip Component - Simple Text
      componentRow(
        "Tooltip (Text)",
        "Simple text tooltip that appears on hover",
        div(cls := "flex flex-wrap gap-4")(
          Tooltip.text(
            "This is a helpful tooltip!",
            button(cls := "p-2 bg-blue-500 text-white rounded")("Hover me")
          ),
          Tooltip.text(
            "Another tooltip with more information",
            span(cls := "px-3 py-1 bg-gray-200 rounded cursor-help")("?")
          )
        )
      ),

      // Tooltip Component - HTML Content
      componentRow(
        "Tooltip (HTML Content)",
        "Tooltip with rich HTML content",
        div(cls := "flex flex-wrap gap-4")(
          Tooltip(
            div(cls := "flex flex-col gap-1")(
              strong()("Bold Title"),
              text("This tooltip has HTML content with "),
              em()("emphasis"),
              text(" and formatting.")
            ),
            button(cls := "p-2 bg-green-500 text-white rounded")("Rich Content")
          ),
          Tooltip(
            div()(
              div(cls := "font-bold mb-1")("Feature:"),
              ul(cls := "list-disc list-inside text-xs")(
                li()("Point 1"),
                li()("Point 2"),
                li()("Point 3")
              )
            ),
            span(cls := "px-3 py-1 bg-purple-500 text-white rounded cursor-pointer")("Info")
          )
        )
      ),

      // Tooltip Component - Positioning
      componentRow(
        "Tooltip (Positions)",
        "Tooltips can appear in different positions: top, bottom, left, right",
        div(cls := "flex flex-wrap gap-6 items-center justify-center p-8")(
          Tooltip.withPosition(
            div()(text("Tooltip on top")),
            button(cls := "px-4 py-2 bg-indigo-500 text-white rounded")("Top"),
            "top"
          ),
          Tooltip.withPosition(
            div()(text("Tooltip on bottom")),
            button(cls := "px-4 py-2 bg-indigo-500 text-white rounded")("Bottom"),
            "bottom"
          ),
          Tooltip.withPosition(
            div()(text("Tooltip on left")),
            button(cls := "px-4 py-2 bg-indigo-500 text-white rounded")("Left"),
            "left"
          ),
          Tooltip.withPosition(
            div()(text("Tooltip on right")),
            button(cls := "px-4 py-2 bg-indigo-500 text-white rounded")("Right"),
            "right"
          )
        )
      ),

      // Tooltip Component - Combined with other components
      componentRow(
        "Tooltip (Combined)",
        "Tooltips can wrap other UI components",
        div(cls := "flex flex-wrap gap-4")(
          Tooltip.text(
            "Click this button to increment the counter",
            Button.interactive("Increment", Msg.ButtonClicked)
          ),
          Tooltip(
            div()(
              text("Enter text here. Current value: "),
              strong()(if (model.inputValue.isEmpty) "(empty)" else model.inputValue)
            ),
            Input.interactive(model.inputValue, Msg.InputChanged.apply, "text")
          )
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
