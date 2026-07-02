package com.crianonim.shadcn

import tyrian.*
import tyrian.Html.*

/** shadcn/ui display components (stateless render functions). Styled with the oklch design tokens
  * defined in `app/index.css` and mapped in `app/tailwind.config.js`.
  */

// ============ ALERT ============
object Alert:
  enum Variant:
    case Default, Destructive

  def apply[A](
      title: String,
      description: String,
      variant: Variant = Variant.Default,
      icon: Option[Html[A]] = None
  ): Html[A] =
    val variantCls = variant match
      case Variant.Default     => "bg-card text-card-foreground"
      case Variant.Destructive => "text-destructive border-destructive/50 bg-card"
    div(
      cls := s"relative w-full rounded-lg border px-4 py-3 text-sm flex gap-3 $variantCls",
      attribute("role", "alert")
    )(
      icon.map(i => div(cls := "mt-0.5 shrink-0")(i)).getOrElse(div()()),
      div(cls := "flex flex-col gap-1")(
        div(cls := "font-medium leading-none tracking-tight")(text(title)),
        div(cls := "text-sm text-muted-foreground")(text(description))
      )
    )

// ============ BADGE ============
object Badge:
  enum Variant:
    case Default, Secondary, Destructive, Outline

  def apply[A](label: String, variant: Variant = Variant.Default): Html[A] =
    val v = variant match
      case Variant.Default     => "border-transparent bg-primary text-primary-foreground"
      case Variant.Secondary   => "border-transparent bg-secondary text-secondary-foreground"
      case Variant.Destructive => "border-transparent bg-destructive text-white"
      case Variant.Outline     => "text-foreground"
    span(
      cls := s"inline-flex items-center rounded-md border px-2 py-0.5 text-xs font-medium w-fit whitespace-nowrap $v"
    )(text(label))

// ============ AVATAR ============
object Avatar:
  def apply[A](fallback: String, src: Option[String] = None, sizeCls: String = "size-10"): Html[A] =
    val inner: Html[A] = src match
      case Some(url) => img(cls := "aspect-square h-full w-full object-cover", attribute("src", url))
      case None      => span(cls := "text-sm font-medium text-muted-foreground")(text(fallback))
    div(
      cls := s"relative flex $sizeCls shrink-0 overflow-hidden rounded-full bg-muted items-center justify-center"
    )(inner)

// ============ CARD ============
object Card:
  def apply[A](children: Html[A]*): Html[A] =
    div(cls := "rounded-xl border bg-card text-card-foreground shadow-sm")(children*)

  def header[A](children: Html[A]*): Html[A] =
    div(cls := "flex flex-col gap-1.5 p-6")(children*)

  def title[A](t: String): Html[A] =
    div(cls := "font-semibold leading-none tracking-tight")(text(t))

  def description[A](t: String): Html[A] =
    div(cls := "text-sm text-muted-foreground")(text(t))

  def content[A](children: Html[A]*): Html[A] =
    div(cls := "p-6 pt-0")(children*)

  def footer[A](children: Html[A]*): Html[A] =
    div(cls := "flex items-center p-6 pt-0 gap-2")(children*)

// ============ SEPARATOR ============
object Separator:
  def horizontal[A]: Html[A] = div(cls := "shrink-0 bg-border h-px w-full")()
  def vertical[A]: Html[A]   = div(cls := "shrink-0 bg-border w-px h-full")()

// ============ SKELETON ============
object Skeleton:
  def apply[A](sizeCls: String): Html[A] =
    div(cls := s"animate-pulse rounded-md bg-muted $sizeCls")()

// ============ SPINNER ============
object Spinner:
  def apply[A](sizeCls: String = "size-6"): Html[A] =
    div(
      cls := s"$sizeCls animate-spin rounded-full border-2 border-muted border-t-foreground",
      attribute("role", "status")
    )()

// ============ KBD ============
object Kbd:
  def apply[A](keys: String): Html[A] =
    span(
      cls := "inline-flex items-center gap-1 rounded border bg-muted px-1.5 py-0.5 font-mono text-xs font-medium text-muted-foreground"
    )(text(keys))

// ============ PROGRESS ============
object Progress:
  def apply[A](value: Int): Html[A] =
    val clamped = math.max(0, math.min(100, value))
    div(cls := "relative h-2 w-full overflow-hidden rounded-full bg-muted")(
      div(
        cls := "h-full bg-primary transition-all",
        attribute("style", s"width:$clamped%")
      )()
    )

// ============ ASPECT RATIO ============
object AspectRatio:
  def apply[A](ratioCls: String = "aspect-video")(child: Html[A]): Html[A] =
    div(cls := s"relative w-full $ratioCls overflow-hidden rounded-lg bg-muted")(child)

// ============ EMPTY ============
object Empty:
  def apply[A](
      title: String,
      description: String,
      icon: Option[Html[A]] = None,
      action: Option[Html[A]] = None
  ): Html[A] =
    div(cls := "flex flex-col items-center justify-center gap-2 rounded-lg border border-dashed p-8 text-center")(
      icon.map(i => div(cls := "text-muted-foreground mb-2")(i)).getOrElse(div()()),
      div(cls := "font-medium")(text(title)),
      div(cls := "text-sm text-muted-foreground")(text(description)),
      action.map(a => div(cls := "mt-2")(a)).getOrElse(div()())
    )

// ============ TABLE ============
object Table:
  def apply[A](children: Html[A]*): Html[A] =
    div(cls := "relative w-full overflow-auto")(
      table(cls := "w-full caption-bottom text-sm")(children*)
    )
  def head[A](children: Html[A]*): Html[A] = thead(cls := "[&_tr]:border-b")(children*)
  def body[A](children: Html[A]*): Html[A] = tbody()(children*)
  def row[A](children: Html[A]*): Html[A] =
    tr(cls := "border-b transition-colors hover:bg-muted")(children*)
  def th[A](t: String): Html[A] =
    Html.th(cls := "h-10 px-2 text-left align-middle font-medium text-muted-foreground")(text(t))
  def td[A](children: Elem[A]*): Html[A] =
    Html.td(cls := "p-2 align-middle")(children*)

// ============ SCROLL AREA ============
object ScrollArea:
  def apply[A](heightCls: String = "h-48")(children: Html[A]*): Html[A] =
    div(cls := s"relative $heightCls w-full overflow-auto rounded-md border p-3")(children*)
