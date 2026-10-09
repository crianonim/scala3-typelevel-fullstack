package com.crianonim.shadcn

import tyrian.*
import tyrian.Html.*

/** shadcn/ui form + control components. Interactive ones take current state + a message value or
  * constructor; state lives in the caller's model (same convention as
  * `com.crianonim.ui.SectionTabs`).
  */

// ============ BUTTON ============
object Button:
  enum Variant:
    case Default, Secondary, Destructive, Outline, Ghost, Link
  enum Size:
    case Sm, Default, Lg, Icon

  private def base =
    "inline-flex items-center justify-center gap-2 whitespace-nowrap rounded-md text-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring cursor-pointer select-none"

  private def variantCls(v: Variant): String = v match
    case Variant.Default     => "bg-primary text-primary-foreground shadow hover:opacity-90"
    case Variant.Secondary   => "bg-secondary text-secondary-foreground hover:opacity-80"
    case Variant.Destructive => "bg-destructive text-white shadow-sm hover:opacity-90"
    case Variant.Outline     =>
      "border border-input bg-background shadow-sm hover:bg-accent hover:text-accent-foreground"
    case Variant.Ghost => "hover:bg-accent hover:text-accent-foreground"
    case Variant.Link  => "text-primary underline-offset-4 hover:underline"

  private def sizeCls(s: Size): String = s match
    case Size.Sm      => "h-8 rounded-md px-3 text-xs"
    case Size.Default => "h-9 px-4 py-2"
    case Size.Lg      => "h-10 rounded-md px-6"
    case Size.Icon    => "h-9 w-9"

  def apply[A](
      label: String,
      msg: A,
      variant: Variant = Variant.Default,
      size: Size = Size.Default,
      className: String = ""
  ): Html[A] =
    button(cls := s"$base ${variantCls(variant)} ${sizeCls(size)} $className", onClick(msg))(
      text(label)
    )

  def withContent[A](
      msg: A,
      variant: Variant = Variant.Default,
      size: Size = Size.Default,
      className: String = ""
  )(children: Elem[A]*): Html[A] =
    button(cls := s"$base ${variantCls(variant)} ${sizeCls(size)} $className", onClick(msg))(
      children*
    )

// ============ BUTTON GROUP ============
object ButtonGroup:
  def apply[A](children: Html[A]*): Html[A] =
    div(
      cls := "inline-flex items-center [&>*]:rounded-none [&>*:first-child]:rounded-l-md [&>*:last-child]:rounded-r-md -space-x-px"
    )(children*)

// ============ LABEL ============
object Label:
  def apply[A](t: String): Html[A] =
    label(cls := "text-sm font-medium leading-none")(text(t))

// ============ INPUT ============
object Input:
  private def base =
    "flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"

  def apply[A](
      value_ : String,
      onInputMsg: String => A,
      placeholder_ : String = "",
      type_ : String = "text",
      className: String = ""
  ): Html[A] =
    input(
      cls := s"$base $className",
      attribute("type", type_),
      attribute("placeholder", placeholder_),
      value := value_,
      onInput(onInputMsg)
    )

// ============ TEXTAREA ============
object Textarea:
  def apply[A](
      value_ : String,
      onInputMsg: String => A,
      placeholder_ : String = "",
      rows_ : Int = 3
  ): Html[A] =
    textarea(
      cls := "flex min-h-16 w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring",
      attribute("placeholder", placeholder_),
      attribute("rows", rows_.toString),
      onInput(onInputMsg),
      value := value_
    )()

// ============ NATIVE SELECT ============
object NativeSelect:
  def apply[A](
      value_ : String,
      options: List[(String, String)],
      onChangeMsg: String => A
  ): Html[A] =
    select(
      cls := "flex h-9 w-full rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring",
      onChange(onChangeMsg)
    )(
      options.map { case (v, lbl) =>
        if v == value_ then option(value := v, selected := true)(text(lbl))
        else option(value := v)(text(lbl))
      }
    )

// ============ CHECKBOX ============
object Checkbox:
  def apply[A](checked: Boolean, onToggle: A, labelText: String = ""): Html[A] =
    val box =
      val stateCls =
        if checked then "bg-primary text-primary-foreground border-primary"
        else "bg-transparent border-input"
      div(
        cls := s"size-4 shrink-0 rounded-sm border shadow flex items-center justify-center cursor-pointer $stateCls",
        onClick(onToggle)
      )(if checked then Icons.check[A]("size-3.5") else span()())
    if labelText.isEmpty then box
    else
      label(cls := "flex items-center gap-2 text-sm cursor-pointer", onClick(onToggle))(
        box,
        span()(text(labelText))
      )

// ============ RADIO GROUP ============
object RadioGroup:
  def apply[A](
      options: List[(String, String)],
      selected: String,
      onSelect: String => A
  ): Html[A] =
    div(cls := "flex flex-col gap-2")(
      options.map { case (v, lbl) =>
        val isSel = v == selected
        label(cls := "flex items-center gap-2 text-sm cursor-pointer", onClick(onSelect(v)))(
          div(
            cls := "aspect-square size-4 rounded-full border border-primary flex items-center justify-center"
          )(
            if isSel then div(cls := "size-2 rounded-full bg-primary")() else span()()
          ),
          span()(text(lbl))
        )
      }
    )

// ============ SWITCH ============
object Switch:
  def apply[A](checked: Boolean, onToggle: A): Html[A] =
    val trackCls = if checked then "bg-primary" else "bg-input"
    val thumbPos = if checked then "translate-x-4" else "translate-x-0"
    button(
      cls := s"inline-flex h-5 w-9 shrink-0 items-center rounded-full border-2 border-transparent transition-colors cursor-pointer $trackCls",
      onClick(onToggle),
      attribute("role", "switch")
    )(
      span(
        cls := s"pointer-events-none block size-4 rounded-full bg-background shadow-lg transition-transform $thumbPos"
      )()
    )

// ============ SLIDER ============
object Slider:
  def apply[A](
      value_ : Int,
      onInputMsg: String => A,
      min_ : Int = 0,
      max_ : Int = 100
  ): Html[A] =
    input(
      cls := "w-full accent-primary cursor-pointer",
      attribute("type", "range"),
      attribute("min", min_.toString),
      attribute("max", max_.toString),
      value := value_.toString,
      onInput(onInputMsg)
    )

// ============ TOGGLE ============
object Toggle:
  def apply[A](pressed: Boolean, onToggle: A, labelText: String): Html[A] =
    val stateCls =
      if pressed then "bg-accent text-accent-foreground" else "bg-transparent hover:bg-muted"
    button(
      cls := s"inline-flex items-center justify-center rounded-md text-sm font-medium h-9 px-2.5 transition-colors cursor-pointer $stateCls",
      onClick(onToggle)
    )(text(labelText))

// ============ TOGGLE GROUP ============
object ToggleGroup:
  def apply[A](
      items: List[(String, String)],
      selected: String,
      onSelect: String => A
  ): Html[A] =
    div(cls := "inline-flex items-center gap-1 rounded-md border border-input p-1")(
      items.map { case (v, lbl) =>
        Toggle(v == selected, onSelect(v), lbl)
      }
    )

// ============ INPUT GROUP ============
object InputGroup:
  def apply[A](
      prefix: Option[String],
      value_ : String,
      onInputMsg: String => A,
      suffix: Option[Html[A]] = None,
      placeholder_ : String = ""
  ): Html[A] =
    div(
      cls := "flex items-center rounded-md border border-input bg-transparent shadow-sm focus-within:ring-1 focus-within:ring-ring overflow-hidden"
    )(
      prefix
        .map(p =>
          span(
            cls := "px-3 text-sm text-muted-foreground border-r border-input bg-muted h-9 flex items-center"
          )(text(p))
        )
        .getOrElse(span()()),
      input(
        cls := "flex-1 h-9 bg-transparent px-3 py-1 text-sm placeholder:text-muted-foreground focus-visible:outline-none",
        attribute("type", "text"),
        attribute("placeholder", placeholder_),
        value := value_,
        onInput(onInputMsg)
      ),
      suffix.map(s => div(cls := "px-2 flex items-center")(s)).getOrElse(span()())
    )

// ============ FIELD ============
object Field:
  def apply[A](labelText: String, control: Html[A], description: String = ""): Html[A] =
    div(cls := "flex flex-col gap-2")(
      Label[A](labelText),
      control,
      if description.isEmpty then span()()
      else div(cls := "text-sm text-muted-foreground")(text(description))
    )
