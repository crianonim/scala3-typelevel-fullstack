package com.crianonim.shadcn

import cats.effect.IO
import tyrian.*
import tyrian.Html.*

/** Showcase tab for the ported shadcn/ui components — mirrors the reference `page.tsx` gallery in
  * `ui-test-shadcn`. Holds all demo state; includes a light/dark toggle that flips a `.dark` class
  * on the gallery root so the oklch tokens re-theme the whole subtree.
  */
object ShadcnShowcase {

  case class Model(
      dark: Boolean = false,
      inputValue: String = "",
      textareaValue: String = "",
      emailValue: String = "",
      nativeSelectValue: String = "apple",
      selectValue: String = "",
      selectOpen: Boolean = false,
      inputGroupValue: String = "",
      checkbox1: Boolean = true,
      checkbox2: Boolean = false,
      switchOn: Boolean = true,
      radioValue: String = "comfortable",
      sliderValue: Int = 40,
      toggleBold: Boolean = false,
      toggleGroupValue: String = "center",
      tab: String = "account",
      page: Int = 1,
      navOpen: Option[String] = None,
      menubarOpen: Option[String] = None,
      sidebarActive: String = "Dashboard",
      dialogOpen: Boolean = false,
      alertOpen: Boolean = false,
      sheetOpen: Boolean = false,
      drawerOpen: Boolean = false,
      popoverOpen: Boolean = false,
      dropdownOpen: Boolean = false,
      contextOpen: Boolean = false,
      accordionOpen: Set[String] = Set("item-1"),
      collapsibleOpen: Boolean = false,
      progressValue: Int = 60,
      lastAction: String = ""
  )

  enum Msg:
    case ToggleDark
    case SetInput(v: String)
    case SetTextarea(v: String)
    case SetEmail(v: String)
    case SetNativeSelect(v: String)
    case ToggleSelect
    case SetSelect(v: String)
    case SetInputGroup(v: String)
    case ToggleCheckbox1
    case ToggleCheckbox2
    case ToggleSwitch
    case SetRadio(v: String)
    case SetSlider(v: String)
    case ToggleBold
    case SetToggleGroup(v: String)
    case SetTab(id: String)
    case SetPage(p: Int)
    case ToggleNav(id: String)
    case ToggleMenubar(id: String)
    case MenubarItem(item: String)
    case SetSidebar(v: String)
    case OpenDialog, CloseDialog
    case OpenAlert, CloseAlert, ConfirmAlert
    case OpenSheet, CloseSheet
    case OpenDrawer, CloseDrawer
    case TogglePopover
    case ToggleDropdown
    case DropdownItem(item: String)
    case ToggleContext
    case ContextItem(item: String)
    case ToggleAccordion(id: String)
    case ToggleCollapsible
    case IncProgress, DecProgress
    case Noop

  def init: Model = Model()

  def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = msg =>
    val m = msg match
      case Msg.ToggleDark          => model.copy(dark = !model.dark)
      case Msg.SetInput(v)         => model.copy(inputValue = v)
      case Msg.SetTextarea(v)      => model.copy(textareaValue = v)
      case Msg.SetEmail(v)         => model.copy(emailValue = v)
      case Msg.SetNativeSelect(v)  => model.copy(nativeSelectValue = v)
      case Msg.ToggleSelect        => model.copy(selectOpen = !model.selectOpen)
      case Msg.SetSelect(v)        => model.copy(selectValue = v, selectOpen = false)
      case Msg.SetInputGroup(v)    => model.copy(inputGroupValue = v)
      case Msg.ToggleCheckbox1     => model.copy(checkbox1 = !model.checkbox1)
      case Msg.ToggleCheckbox2     => model.copy(checkbox2 = !model.checkbox2)
      case Msg.ToggleSwitch        => model.copy(switchOn = !model.switchOn)
      case Msg.SetRadio(v)         => model.copy(radioValue = v)
      case Msg.SetSlider(v)        => model.copy(sliderValue = v.toIntOption.getOrElse(model.sliderValue))
      case Msg.ToggleBold          => model.copy(toggleBold = !model.toggleBold)
      case Msg.SetToggleGroup(v)   => model.copy(toggleGroupValue = v)
      case Msg.SetTab(id)          => model.copy(tab = id)
      case Msg.SetPage(p)          => model.copy(page = p)
      case Msg.ToggleNav(id)       => model.copy(navOpen = if model.navOpen.contains(id) then None else Some(id))
      case Msg.ToggleMenubar(id)   => model.copy(menubarOpen = if model.menubarOpen.contains(id) then None else Some(id))
      case Msg.MenubarItem(item)   => model.copy(menubarOpen = None, lastAction = s"Menubar: $item")
      case Msg.SetSidebar(v)       => model.copy(sidebarActive = v)
      case Msg.OpenDialog          => model.copy(dialogOpen = true)
      case Msg.CloseDialog         => model.copy(dialogOpen = false)
      case Msg.OpenAlert           => model.copy(alertOpen = true)
      case Msg.CloseAlert          => model.copy(alertOpen = false)
      case Msg.ConfirmAlert        => model.copy(alertOpen = false, lastAction = "Alert confirmed")
      case Msg.OpenSheet           => model.copy(sheetOpen = true)
      case Msg.CloseSheet          => model.copy(sheetOpen = false)
      case Msg.OpenDrawer          => model.copy(drawerOpen = true)
      case Msg.CloseDrawer         => model.copy(drawerOpen = false)
      case Msg.TogglePopover       => model.copy(popoverOpen = !model.popoverOpen)
      case Msg.ToggleDropdown      => model.copy(dropdownOpen = !model.dropdownOpen)
      case Msg.DropdownItem(item)  => model.copy(dropdownOpen = false, lastAction = s"Dropdown: $item")
      case Msg.ToggleContext       => model.copy(contextOpen = !model.contextOpen)
      case Msg.ContextItem(item)   => model.copy(contextOpen = false, lastAction = s"Context: $item")
      case Msg.ToggleAccordion(id) =>
        model.copy(accordionOpen =
          if model.accordionOpen.contains(id) then model.accordionOpen - id else model.accordionOpen + id
        )
      case Msg.ToggleCollapsible   => model.copy(collapsibleOpen = !model.collapsibleOpen)
      case Msg.IncProgress         => model.copy(progressValue = math.min(100, model.progressValue + 10))
      case Msg.DecProgress         => model.copy(progressValue = math.max(0, model.progressValue - 10))
      case Msg.Noop                => model
    (m, Cmd.None)

  // ---- layout helpers ----
  private def section(titleText: String)(rows: Html[Msg]*): Html[Msg] =
    div(cls := "flex flex-col gap-4")(
      div(cls := "text-sm font-semibold uppercase tracking-wide text-muted-foreground")(text(titleText)),
      div(cls := "grid grid-cols-1 md:grid-cols-2 gap-4")(rows*)
    )

  private def row(name: String, desc: String)(demo: Html[Msg]*): Html[Msg] =
    div(cls := "rounded-lg border bg-card text-card-foreground p-5 flex flex-col gap-3")(
      div(cls := "flex flex-col gap-0.5")(
        div(cls := "font-semibold text-sm")(text(name)),
        div(cls := "text-xs text-muted-foreground")(text(desc))
      ),
      div(cls := "flex flex-wrap items-center gap-3")(demo*)
    )

  def view(model: Model): Html[Msg] =
    val rootCls =
      (if model.dark then "dark " else "") +
        "bg-background text-foreground rounded-xl border p-6 flex flex-col gap-10"
    div(cls := rootCls)(
      // Header + dark toggle
      div(cls := "flex items-center justify-between")(
        div(cls := "flex flex-col gap-0.5")(
          div(cls := "text-2xl font-bold")(text("shadcn/ui components")),
          div(cls := "text-sm text-muted-foreground")(
            text("52 components ported to Tyrian. Toggle the theme to see the oklch tokens.")
          )
        ),
        button(
          cls := "inline-flex items-center gap-2 h-9 px-3 rounded-md border border-input bg-background text-sm hover:bg-accent hover:text-accent-foreground cursor-pointer",
          onClick(Msg.ToggleDark)
        )(
          if model.dark then Icons.sun[Msg]("size-4") else Icons.moon[Msg]("size-4"),
          span()(text(if model.dark then "Light" else "Dark"))
        )
      ),
      if model.lastAction.nonEmpty then
        div(cls := "text-xs text-muted-foreground")(text(s"Last action → ${model.lastAction}"))
      else span()(),
      buttonsSection(model),
      inputsSection(model),
      displaySection(model),
      navigationSection(model),
      overlaysSection(model),
      contentSection(model)
    )

  // ============ SECTIONS ============

  private def buttonsSection(model: Model): Html[Msg] =
    section("Buttons & controls")(
      row("Button", "Variants and sizes")(
        Button("Default", Msg.Noop),
        Button("Secondary", Msg.Noop, Button.Variant.Secondary),
        Button("Destructive", Msg.Noop, Button.Variant.Destructive),
        Button("Outline", Msg.Noop, Button.Variant.Outline),
        Button("Ghost", Msg.Noop, Button.Variant.Ghost),
        Button("Link", Msg.Noop, Button.Variant.Link),
        Button("Small", Msg.Noop, Button.Variant.Default, Button.Size.Sm),
        Button("Large", Msg.Noop, Button.Variant.Default, Button.Size.Lg)
      ),
      row("Button group", "Joined buttons")(
        ButtonGroup(
          Button("Left", Msg.Noop, Button.Variant.Outline),
          Button("Center", Msg.Noop, Button.Variant.Outline),
          Button("Right", Msg.Noop, Button.Variant.Outline)
        )
      ),
      row("Toggle", "Single on/off")(
        Toggle(model.toggleBold, Msg.ToggleBold, "Bold")
      ),
      row("Toggle group", "Text alignment")(
        ToggleGroup(
          List("left" -> "Left", "center" -> "Center", "right" -> "Right"),
          model.toggleGroupValue,
          Msg.SetToggleGroup(_)
        )
      ),
      row("Checkbox", "Toggle a value")(
        Checkbox(model.checkbox1, Msg.ToggleCheckbox1, "Accept terms"),
        Checkbox(model.checkbox2, Msg.ToggleCheckbox2, "Subscribe")
      ),
      row("Radio group", "Single choice")(
        RadioGroup(
          List("comfortable" -> "Comfortable", "compact" -> "Compact", "spacious" -> "Spacious"),
          model.radioValue,
          Msg.SetRadio(_)
        )
      ),
      row("Switch", "Boolean setting")(
        div(cls := "flex items-center gap-2")(
          Switch(model.switchOn, Msg.ToggleSwitch),
          span(cls := "text-sm")(text("Airplane mode"))
        )
      ),
      row("Slider", "Range value")(
        div(cls := "flex flex-col gap-2 w-64")(
          Slider(model.sliderValue, Msg.SetSlider(_)),
          div(cls := "text-sm text-muted-foreground")(text(s"Value: ${model.sliderValue}"))
        )
      )
    )

  private def inputsSection(model: Model): Html[Msg] =
    val fruits = List("apple" -> "Apple", "banana" -> "Banana", "cherry" -> "Cherry")
    section("Inputs & fields")(
      row("Input", "Text input")(
        div(cls := "flex flex-col gap-2 w-64")(
          Input(model.inputValue, Msg.SetInput(_), "Type something…"),
          div(cls := "text-sm text-muted-foreground")(text(s"You typed: ${model.inputValue}"))
        )
      ),
      row("Textarea", "Multi-line input")(
        div(cls := "w-64")(Textarea(model.textareaValue, Msg.SetTextarea(_), "Your message…"))
      ),
      row("Label + Field", "Labeled control with hint")(
        div(cls := "w-64")(
          Field("Email", Input(model.emailValue, Msg.SetEmail(_), "you@example.com", "email"), "We'll never share it.")
        )
      ),
      row("Native select", "Native dropdown")(
        div(cls := "w-56")(NativeSelect(model.nativeSelectValue, fruits, Msg.SetNativeSelect(_)))
      ),
      row("Select", "Custom dropdown")(
        Select(model.selectOpen, model.selectValue, "Select a fruit", fruits, Msg.ToggleSelect, Msg.SetSelect(_))
      ),
      row("Input group", "Prefix + suffix addons")(
        div(cls := "w-72")(
          InputGroup(Some("https://"), model.inputGroupValue, Msg.SetInputGroup(_), Some(Icons.search[Msg]("size-4")), "example.com")
        )
      )
    )

  private def displaySection(model: Model): Html[Msg] =
    section("Data display")(
      row("Badge", "Status labels")(
        Badge("Default"),
        Badge("Secondary", Badge.Variant.Secondary),
        Badge("Destructive", Badge.Variant.Destructive),
        Badge("Outline", Badge.Variant.Outline)
      ),
      row("Avatar", "Image with fallback")(
        Avatar("JS"),
        Avatar("AB"),
        Avatar("CD", sizeCls = "size-12")
      ),
      row("Alert", "Callout messages")(
        div(cls := "flex flex-col gap-3 w-full")(
          Alert("Heads up!", "You can add components to your app.", Alert.Variant.Default, Some(Icons.info[Msg]("size-4"))),
          Alert("Error", "Your session has expired.", Alert.Variant.Destructive, Some(Icons.bell[Msg]("size-4")))
        )
      ),
      row("Card", "Container with sections")(
        div(cls := "w-72")(
          Card(
            Card.header(Card.title("Create project"), Card.description("Deploy in one click.")),
            Card.content(div(cls := "text-sm text-muted-foreground")(text("Card body content goes here."))),
            Card.footer(Button("Cancel", Msg.Noop, Button.Variant.Outline, Button.Size.Sm), Button("Deploy", Msg.Noop, Button.Variant.Default, Button.Size.Sm))
          )
        )
      ),
      row("Separator", "Divider")(
        div(cls := "flex flex-col gap-2 w-64")(
          div(cls := "text-sm")(text("Above")),
          Separator.horizontal,
          div(cls := "text-sm")(text("Below"))
        )
      ),
      row("Skeleton", "Loading placeholder")(
        div(cls := "flex items-center gap-3")(
          Skeleton("size-12 rounded-full"),
          div(cls := "flex flex-col gap-2")(Skeleton("h-4 w-40"), Skeleton("h-4 w-24"))
        )
      ),
      row("Spinner", "Loading indicator")(Spinner()),
      row("Progress", "Determinate progress")(
        div(cls := "flex flex-col gap-2 w-64")(
          Progress(model.progressValue),
          div(cls := "flex items-center gap-2")(
            Button("-", Msg.DecProgress, Button.Variant.Outline, Button.Size.Sm),
            span(cls := "text-sm text-muted-foreground")(text(s"${model.progressValue}%")),
            Button("+", Msg.IncProgress, Button.Variant.Outline, Button.Size.Sm)
          )
        )
      ),
      row("Kbd", "Keyboard shortcut")(Kbd("⌘"), Kbd("K"), span(cls := "text-sm text-muted-foreground")(text("to search"))),
      row("Aspect ratio", "16:9 container")(
        div(cls := "w-64")(
          AspectRatio("aspect-video")(div(cls := "flex h-full w-full items-center justify-center text-sm text-muted-foreground")(text("16 / 9")))
        )
      ),
      row("Empty", "Empty state")(
        div(cls := "w-full")(
          com.crianonim.shadcn.Empty("No results found", "Try adjusting your search.", Some(Icons.search[Msg]("size-6")), Some(Button("Clear filters", Msg.Noop, Button.Variant.Outline, Button.Size.Sm)))
        )
      ),
      row("Table", "Data table")(
        div(cls := "w-full")(
          Table(
            Table.head(Table.row(Table.th("Invoice"), Table.th("Status"), Table.td(div(cls := "text-right font-medium text-muted-foreground")(text("Amount"))))),
            Table.body(
              Table.row(Table.td(text("INV001")), Table.td(Badge("Paid", Badge.Variant.Secondary)), Table.td(div(cls := "text-right")(text("$250.00")))),
              Table.row(Table.td(text("INV002")), Table.td(Badge("Pending", Badge.Variant.Outline)), Table.td(div(cls := "text-right")(text("$150.00"))))
            )
          )
        )
      ),
      row("Scroll area", "Scrollable region")(
        div(cls := "w-64")(
          ScrollArea("h-32")(
            div(cls := "flex flex-col gap-2")(
              (1 to 12).toList.map(i => div(cls := "text-sm")(text(s"Item $i")))
            )
          )
        )
      )
    )

  private def navigationSection(model: Model): Html[Msg] =
    val tabContent = model.tab match
      case "account"  => div(cls := "text-sm text-muted-foreground")(text("Make changes to your account here."))
      case "password" => div(cls := "text-sm text-muted-foreground")(text("Change your password here."))
      case _          => div(cls := "text-sm text-muted-foreground")(text("Other settings."))
    section("Navigation")(
      row("Breadcrumb", "Page hierarchy")(
        Breadcrumb(List("Home", "Components", "Breadcrumb"))
      ),
      row("Pagination", "Page navigation")(
        Pagination(model.page, 5, Msg.SetPage(_))
      ),
      row("Tabs", "Switch views")(
        div(cls := "w-full")(
          Tabs(List("account" -> "Account", "password" -> "Password", "other" -> "Other"), model.tab, Msg.SetTab(_))(tabContent)
        )
      ),
      row("Navigation menu", "Menu with panels")(
        NavigationMenu(
          List(
            ("getting-started", "Getting started", List(NavigationMenu.Entry("Introduction", "Re-usable components."), NavigationMenu.Entry("Installation", "How to install dependencies."))),
            ("components", "Components", List(NavigationMenu.Entry("Alert Dialog", "A modal dialog."), NavigationMenu.Entry("Hover Card", "For sighted users to preview.")))
          ),
          model.navOpen,
          Msg.ToggleNav(_)
        )
      ),
      row("Menubar", "Application menu bar")(
        Menubar(
          List(
            ("file", "File", List("New Tab", "New Window", "Share", "Print")),
            ("edit", "Edit", List("Undo", "Redo", "Cut", "Copy", "Paste"))
          ),
          model.menubarOpen,
          Msg.ToggleMenubar(_),
          Msg.MenubarItem(_)
        )
      ),
      row("Sidebar", "Navigation sidebar")(
        Sidebar(
          "Platform",
          List(Sidebar.Item("Dashboard", "star"), Sidebar.Item("Users", "user"), Sidebar.Item("Notifications", "bell")),
          model.sidebarActive,
          Msg.SetSidebar(_)
        )
      )
    )

  private def overlaysSection(model: Model): Html[Msg] =
    section("Overlays & disclosure")(
      row("Dialog", "Modal dialog")(
        Button("Open dialog", Msg.OpenDialog, Button.Variant.Outline),
        Dialog(model.dialogOpen, Msg.CloseDialog, "Edit profile", "Make changes to your profile here.")(
          Field("Name", Input("Jan", _ => Msg.Noop, "")),
          div(cls := "flex justify-end")(Button("Save changes", Msg.CloseDialog))
        )
      ),
      row("Alert dialog", "Confirm destructive action")(
        Button("Delete account", Msg.OpenAlert, Button.Variant.Destructive),
        AlertDialog(model.alertOpen, "Are you absolutely sure?", "This action cannot be undone.", Msg.CloseAlert, Msg.ConfirmAlert, "Cancel", "Delete")
      ),
      row("Sheet", "Side panel")(
        Button("Open sheet", Msg.OpenSheet, Button.Variant.Outline),
        Sheet(model.sheetOpen, Msg.CloseSheet, "Edit profile", "Update your details.")(
          div(cls := "text-sm text-muted-foreground")(text("Sheet body content."))
        )
      ),
      row("Drawer", "Bottom drawer")(
        Button("Open drawer", Msg.OpenDrawer, Button.Variant.Outline),
        Drawer(model.drawerOpen, Msg.CloseDrawer, "Move goal")(
          div(cls := "text-sm text-muted-foreground")(text("Set your daily activity goal."))
        )
      ),
      row("Popover", "Floating content")(
        Popover(model.popoverOpen, "Open popover", Msg.TogglePopover)(
          div(cls := "flex flex-col gap-2")(
            div(cls := "font-medium text-sm")(text("Dimensions")),
            div(cls := "text-sm text-muted-foreground")(text("Set the dimensions for the layer."))
          )
        )
      ),
      row("Hover card", "Preview on hover")(
        HoverCard("@crianonim")(
          div(cls := "flex flex-col gap-1")(
            div(cls := "text-sm font-semibold")(text("crianonim")),
            div(cls := "text-sm text-muted-foreground")(text("Full-stack tinkerer. Scala, Elm, TS."))
          )
        )
      ),
      row("Tooltip", "Hover hint")(
        Tooltip("Add to library")(Button("Hover me", Msg.Noop, Button.Variant.Outline))
      ),
      row("Dropdown menu", "Action menu")(
        DropdownMenu(model.dropdownOpen, "Options", Msg.ToggleDropdown)(
          DropdownMenu.label("My account"),
          DropdownMenu.item("Profile", Msg.DropdownItem("Profile")),
          DropdownMenu.item("Billing", Msg.DropdownItem("Billing")),
          DropdownMenu.separator,
          DropdownMenu.item("Log out", Msg.DropdownItem("Log out"))
        )
      ),
      row("Context menu", "Menu on a target")(
        ContextMenu(model.contextOpen, Msg.ToggleContext)(
          DropdownMenu.item("Back", Msg.ContextItem("Back")),
          DropdownMenu.item("Reload", Msg.ContextItem("Reload")),
          DropdownMenu.separator,
          DropdownMenu.item("Save as…", Msg.ContextItem("Save as"))
        )
      ),
      row("Accordion", "Expandable sections")(
        div(cls := "w-full")(
          Accordion(
            List(
              ("item-1", "Is it accessible?", div()(text("Yes. It follows the WAI-ARIA pattern."))),
              ("item-2", "Is it styled?", div()(text("Yes, with the shadcn tokens."))),
              ("item-3", "Is it animated?", div()(text("It toggles open/closed.")))
            ),
            model.accordionOpen,
            Msg.ToggleAccordion(_)
          )
        )
      ),
      row("Collapsible", "Show/hide content")(
        div(cls := "w-full")(
          Collapsible(model.collapsibleOpen, Msg.ToggleCollapsible, "@crianonim starred 3 repositories")(
            div(cls := "text-sm text-muted-foreground")(text("@radix-ui/primitives")),
            div(cls := "text-sm text-muted-foreground")(text("@stitches/react"))
          )
        )
      )
    )

  private def contentSection(model: Model): Html[Msg] =
    section("Content")(
      row("Item", "List item")(
        div(cls := "w-72")(
          Item("Documentation", "Learn how to use the components.", Some(Avatar("D", sizeCls = "size-9")), Some(Button("View", Msg.Noop, Button.Variant.Ghost, Button.Size.Sm)))
        )
      ),
      row("Attachment", "File card")(
        Attachment("proposal.pdf", "2.4 MB")
      ),
      row("Bubble", "Chat bubbles")(
        div(cls := "flex flex-col gap-2 w-full")(
          Bubble("Hey, how are you?", Bubble.Variant.Received),
          Bubble("Doing great, thanks!", Bubble.Variant.Sent)
        )
      ),
      row("Marker", "Timeline markers")(
        Marker("Default"),
        Marker("Success", Marker.Variant.Success),
        Marker("Warning", Marker.Variant.Warning),
        Marker("Error", Marker.Variant.Destructive)
      ),
      row("Message", "Chat message with avatar")(
        div(cls := "flex flex-col gap-3 w-full")(
          Message("Can you help me with Tyrian?", fromUser = true),
          Message("Of course — what do you need?", fromUser = false)
        )
      ),
      row("Message scroller", "Scrollable message list")(
        div(cls := "w-full")(
          MessageScroller(
            Message("Hi!", fromUser = false),
            Message("Hello there.", fromUser = true),
            Message("How can I help you today?", fromUser = false),
            Message("Show me all the components.", fromUser = true),
            Message("Scroll up — there are 52 of them.", fromUser = false)
          )
        )
      )
    )

  def subscriptions(model: Model): Sub[IO, Msg] = Sub.None
}
