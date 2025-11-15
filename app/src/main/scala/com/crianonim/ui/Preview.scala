package com.crianonim.ui

import cats.effect.*
import tyrian.*
import tyrian.Html.*
import com.crianonim.ui.Button
import com.crianonim.ui.Input
import com.crianonim.ui.Tooltip
import com.crianonim.ui.SectionTabs

object Preview {

  case class Model(
      inputValue: String,
      buttonClickCount: Int,
      activeTabId: String,
      activeExampleTabId: String
  )

  enum Msg {
    case Noop
    case InputChanged(value: String)
    case ButtonClicked
    case TabChanged(tabId: String)
    case ExampleTabChanged(tabId: String)
  }

  def init: Model = Model("", 0, "overview", "home")

  def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = {
    case Msg.Noop                => (model, Cmd.None)
    case Msg.InputChanged(value) => (model.copy(inputValue = value), Cmd.None)
    case Msg.ButtonClicked => (model.copy(buttonClickCount = model.buttonClickCount + 1), Cmd.None)
    case Msg.TabChanged(tabId) => (model.copy(activeTabId = tabId), Cmd.None)
    case Msg.ExampleTabChanged(tabId) => (model.copy(activeExampleTabId = tabId), Cmd.None)
  }

  def view(model: Model): Html[Msg] = {
    div(cls := "flex flex-col gap-8 max-w-4xl")(
      h1(cls := "text-3xl font-bold")("UI Components Preview"),

      // Button Component - Variants
      componentRow(
        "Button Variants",
        "Buttons come in Primary, Secondary, and Disabled variants",
        div(cls := "flex flex-col gap-4")(
          div(cls := "flex gap-3 items-center")(
            Button.primary("Primary", Msg.ButtonClicked),
            Button.secondary("Secondary", Msg.ButtonClicked),
            Button.disabledButton("Disabled")
          ),
          div(cls := "text-sm text-gray-600")(s"Clicked ${model.buttonClickCount} times")
        )
      ),

      // Button Component - Sizes
      componentRow(
        "Button Sizes",
        "Buttons are available in Small, Medium, and Large sizes",
        div(cls := "flex flex-col gap-4")(
          div(cls := "flex gap-3 items-center")(
            Button.primary("Small", Msg.ButtonClicked, Button.Size.Small),
            Button.primary("Medium", Msg.ButtonClicked, Button.Size.Medium),
            Button.primary("Large", Msg.ButtonClicked, Button.Size.Large)
          ),
          div(cls := "flex gap-3 items-center")(
            Button.secondary("Small", Msg.ButtonClicked, Button.Size.Small),
            Button.secondary("Medium", Msg.ButtonClicked, Button.Size.Medium),
            Button.secondary("Large", Msg.ButtonClicked, Button.Size.Large)
          )
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
            Button.primary("Hover me", Msg.Noop)
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
            Button.primary("Rich Content", Msg.Noop)
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
            Button.secondary("Top", Msg.Noop),
            "top"
          ),
          Tooltip.withPosition(
            div()(text("Tooltip on bottom")),
            Button.secondary("Bottom", Msg.Noop),
            "bottom"
          ),
          Tooltip.withPosition(
            div()(text("Tooltip on left")),
            Button.secondary("Left", Msg.Noop),
            "left"
          ),
          Tooltip.withPosition(
            div()(text("Tooltip on right")),
            Button.secondary("Right", Msg.Noop),
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
      ),

      // SectionTabs Component - Basic Usage
      componentRow(
        "SectionTabs (Basic)",
        "Horizontal tab navigation with active state management",
        div(cls := "flex flex-col gap-4")(
          SectionTabs(
            SectionTabs.Props(
              tabs = List(
                SectionTabs.TabItem("overview", "Overview"),
                SectionTabs.TabItem("details", "Details"),
                SectionTabs.TabItem("settings", "Settings"),
                SectionTabs.TabItem("about", "About")
              ),
              activeTabId = model.activeTabId,
              onTabClick = Msg.TabChanged.apply
            )
          ),
          div(cls := "mt-2")(
            SectionTabs.panel("overview", model.activeTabId == "overview")(
              div(cls := "p-4 bg-blue-50 rounded")(
                h3(cls := "text-lg font-semibold mb-2")("Overview"),
                p(cls := "text-gray-700")("This is the overview panel. It contains general information about the application.")
              )
            ),
            SectionTabs.panel("details", model.activeTabId == "details")(
              div(cls := "p-4 bg-green-50 rounded")(
                h3(cls := "text-lg font-semibold mb-2")("Details"),
                p(cls := "text-gray-700")("Here you can find detailed information and specifications."),
                ul(cls := "list-disc list-inside mt-2 text-gray-600")(
                  li()("Feature 1: Type-safe components"),
                  li()("Feature 2: TailwindCSS styling"),
                  li()("Feature 3: Accessible markup")
                )
              )
            ),
            SectionTabs.panel("settings", model.activeTabId == "settings")(
              div(cls := "p-4 bg-purple-50 rounded")(
                h3(cls := "text-lg font-semibold mb-2")("Settings"),
                p(cls := "text-gray-700")("Configure your preferences here."),
                div(cls := "mt-3 flex flex-col gap-2")(
                  Button.secondary("Save Settings", Msg.Noop, Button.Size.Small),
                  Button.secondary("Reset to Defaults", Msg.Noop, Button.Size.Small)
                )
              )
            ),
            SectionTabs.panel("about", model.activeTabId == "about")(
              div(cls := "p-4 bg-amber-50 rounded")(
                h3(cls := "text-lg font-semibold mb-2")("About"),
                p(cls := "text-gray-700")("Built with Tyrian and Scala.js."),
                p(cls := "text-sm text-gray-500 mt-2")("Version 1.0.0")
              )
            )
          )
        )
      ),

      // SectionTabs Component - From Labels
      componentRow(
        "SectionTabs (From Labels)",
        "Convenient method to create tabs from simple string labels",
        div(cls := "flex flex-col gap-4")(
          SectionTabs.fromLabels(
            labels = List("Home", "Products", "Services", "Contact"),
            activeTabId = model.activeExampleTabId,
            onTabClick = Msg.ExampleTabChanged.apply
          ),
          div(cls := "mt-2 p-4 bg-gray-50 rounded")(
            div(cls := "text-sm text-gray-600")(
              text(s"Active tab: "),
              strong()(text(model.activeExampleTabId))
            )
          )
        )
      ),

      // SectionTabs Component - Responsive
      componentRow(
        "SectionTabs (Many Tabs)",
        "Tabs with horizontal scroll on overflow",
        div(cls := "flex flex-col gap-4")(
          SectionTabs(
            SectionTabs.Props(
              tabs = List(
                SectionTabs.TabItem("tab1", "Dashboard"),
                SectionTabs.TabItem("tab2", "Analytics"),
                SectionTabs.TabItem("tab3", "Reports"),
                SectionTabs.TabItem("tab4", "Calendar"),
                SectionTabs.TabItem("tab5", "Documents"),
                SectionTabs.TabItem("tab6", "Settings"),
                SectionTabs.TabItem("tab7", "Help"),
                SectionTabs.TabItem("tab8", "Profile")
              ),
              activeTabId = model.activeTabId,
              onTabClick = Msg.TabChanged.apply
            )
          ),
          div(cls := "text-xs text-gray-500 italic")("Scroll horizontally if tabs overflow")
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
