package com.crianonim.ui

import cats.effect.*
import tyrian.*
import tyrian.Html.*
import com.crianonim.ui.Button
import com.crianonim.ui.Input
import com.crianonim.ui.Tooltip
import com.crianonim.ui.SectionTabs
import com.crianonim.ui.Card

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
      ),

      // Card Component - Basic with Header
      componentRow(
        "Card (Basic)",
        "Card with title and description header",
        div(cls := "flex flex-col gap-4")(
          Card.withHeader(
            "Card Title",
            "This is a card with a header containing a title and description"
          )(
            div(cls := "text-gray-700")(text("This is the main content area of the card. It can contain any HTML elements.")),
            div(cls := "mt-3 flex gap-2")(
              Button.primary("Action", Msg.Noop, Button.Size.Small),
              Button.secondary("Cancel", Msg.Noop, Button.Size.Small)
            )
          )
        )
      ),

      // Card Component - Variants
      componentRow(
        "Card (Variants)",
        "Cards come in Default, Elevated (with shadow), and Outlined (thicker border) variants",
        div(cls := "flex flex-col gap-4")(
          Card.withHeader(
            "Default Card",
            "Standard card with border"
          )(
            div(cls := "text-gray-700")(text("This is a default card with a simple border."))
          ),
          Card.elevated(
            "Elevated Card",
            "Card with shadow instead of border"
          )(
            div(cls := "text-gray-700")(text("This card has a shadow effect for elevation."))
          ),
          Card.outlined(
            "Outlined Card",
            "Card with thicker border"
          )(
            div(cls := "text-gray-700")(text("This card has a thicker, more prominent border."))
          )
        )
      ),

      // Card Component - Simple (No Header)
      componentRow(
        "Card (Simple)",
        "Cards without headers for content-only layouts",
        div(cls := "flex flex-col gap-4")(
          Card.simple()(
            div(cls := "text-gray-700")(text("This is a simple card without a header. Perfect for pure content areas."))
          ),
          Card.simple(Card.Variant.Elevated)(
            div(cls := "flex items-center gap-3")(
              span(cls := "text-4xl")(text("📊")),
              div()(
                div(cls := "font-semibold text-lg")(text("Analytics")),
                div(cls := "text-sm text-gray-600")(text("View your statistics"))
              )
            )
          )
        )
      ),

      // Card Component - Padding Options
      componentRow(
        "Card (Padding)",
        "Cards support Small, Medium, and Large padding sizes",
        div(cls := "flex flex-col gap-4")(
          Card.withHeader(
            "Small Padding",
            "Compact card with minimal spacing",
            Card.Variant.Default,
            Card.Padding.Small
          )(
            div(cls := "text-sm text-gray-700")(text("This card has small padding for compact layouts."))
          ),
          Card.withHeader(
            "Medium Padding",
            "Default padding for balanced spacing",
            Card.Variant.Default,
            Card.Padding.Medium
          )(
            div(cls := "text-gray-700")(text("This card has medium padding (default)."))
          ),
          Card.withHeader(
            "Large Padding",
            "Spacious card with generous padding",
            Card.Variant.Default,
            Card.Padding.Large
          )(
            div(cls := "text-gray-700")(text("This card has large padding for a spacious feel."))
          )
        )
      ),

      // Card Component - With Different Content
      componentRow(
        "Card (Content Examples)",
        "Cards can contain various types of content: forms, lists, buttons, etc.",
        div(cls := "flex flex-col gap-4")(
          Card.withHeader(
            "User Profile",
            "Update your personal information"
          )(
            div(cls := "flex flex-col gap-3")(
              div()(
                div(cls := "text-sm font-medium text-gray-700 mb-1")(text("Name")),
                Input.interactive(model.inputValue, Msg.InputChanged.apply, "text")
              ),
              div()(
                div(cls := "text-sm font-medium text-gray-700 mb-1")(text("Email")),
                Input.interactive("", _ => Msg.Noop, "email")
              ),
              div(cls := "flex gap-2 mt-2")(
                Button.primary("Save Changes", Msg.Noop, Button.Size.Small),
                Button.secondary("Reset", Msg.Noop, Button.Size.Small)
              )
            )
          ),
          Card.elevated(
            "Quick Stats",
            "Your activity at a glance"
          )(
            div(cls := "grid grid-cols-3 gap-4")(
              div(cls := "text-center")(
                div(cls := "text-2xl font-bold text-blue-600")(text(s"${model.buttonClickCount}")),
                div(cls := "text-xs text-gray-600")(text("Clicks"))
              ),
              div(cls := "text-center")(
                div(cls := "text-2xl font-bold text-green-600")(text("12")),
                div(cls := "text-xs text-gray-600")(text("Tasks"))
              ),
              div(cls := "text-center")(
                div(cls := "text-2xl font-bold text-purple-600")(text("5")),
                div(cls := "text-xs text-gray-600")(text("Projects"))
              )
            )
          ),
          Card.outlined(
            "Features",
            "Available functionality"
          )(
            ul(cls := "list-disc list-inside space-y-1 text-gray-700")(
              li()(text("Type-safe UI components")),
              li()(text("TailwindCSS styling")),
              li()(text("Accessible markup")),
              li()(text("Functional reactive programming"))
            )
          )
        )
      ),

      // Card Component - Nested Content with Content Section
      componentRow(
        "Card (Nested Content)",
        "Cards with content sections for visual hierarchy",
        div(cls := "flex flex-col gap-4")(
          Card.withHeader(
            "Component Preview",
            "Example of a card with nested content sections"
          )(
            Card.contentSection(
              div(cls := "flex gap-2")(
                Button.primary("Primary", Msg.Noop, Button.Size.Small),
                Button.secondary("Secondary", Msg.Noop, Button.Size.Small),
                Button.disabledButton("Disabled", Button.Size.Small)
              )
            ),
            Card.contentSection(
              div(cls := "text-sm text-gray-700")(
                div(cls := "font-semibold mb-2")(text("Input Example")),
                Input.interactive(model.inputValue, Msg.InputChanged.apply, "text")
              )
            )
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
