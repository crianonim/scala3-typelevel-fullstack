package com.crianonim.shadcn

import tyrian.*
import tyrian.Html.*

/** shadcn/ui content/list components (item, attachment, chat bubble/message, timeline marker). */

// ============ ITEM ============
object Item:
  def apply[A](
      title: String,
      description: String,
      media: Option[Html[A]] = None,
      action: Option[Html[A]] = None
  ): Html[A] =
    div(cls := "flex items-center gap-3 rounded-md border p-3")(
      media.map(m => div(cls := "shrink-0")(m)).getOrElse(span()()),
      div(cls := "flex flex-col flex-1 min-w-0")(
        div(cls := "text-sm font-medium truncate")(text(title)),
        div(cls := "text-xs text-muted-foreground truncate")(text(description))
      ),
      action.map(a => div(cls := "shrink-0")(a)).getOrElse(span()())
    )

// ============ ATTACHMENT ============
object Attachment:
  def apply[A](name: String, meta: String, icon: Option[Html[A]] = None): Html[A] =
    div(cls := "flex items-center gap-3 rounded-md border p-3 w-64")(
      div(cls := "flex size-9 items-center justify-center rounded-md bg-muted text-muted-foreground shrink-0")(
        icon.getOrElse(Icons.info[A]("size-4"))
      ),
      div(cls := "flex flex-col min-w-0")(
        div(cls := "text-sm font-medium truncate")(text(name)),
        div(cls := "text-xs text-muted-foreground truncate")(text(meta))
      )
    )

// ============ BUBBLE ============
object Bubble:
  enum Variant:
    case Sent, Received

  def apply[A](content: String, variant: Variant = Variant.Received): Html[A] =
    val v = variant match
      case Variant.Sent     => "ml-auto bg-primary text-primary-foreground"
      case Variant.Received => "bg-muted text-foreground"
    div(cls := s"max-w-xs w-fit rounded-lg px-3 py-2 text-sm $v")(text(content))

// ============ MARKER ============
object Marker:
  enum Variant:
    case Default, Success, Warning, Destructive

  def apply[A](label: String, variant: Variant = Variant.Default): Html[A] =
    val dot = variant match
      case Variant.Default     => "bg-primary"
      case Variant.Success     => "bg-emerald-500"
      case Variant.Warning     => "bg-amber-500"
      case Variant.Destructive => "bg-destructive"
    span(cls := "inline-flex items-center gap-2 rounded-full border px-3 py-1 text-xs font-medium")(
      span(cls := s"size-2 rounded-full $dot")(),
      text(label)
    )

// ============ MESSAGE ============
object Message:
  def apply[A](content: String, fromUser: Boolean): Html[A] =
    val alignment = if fromUser then "flex-row-reverse" else "flex-row"
    div(cls := s"flex items-start gap-2 $alignment")(
      Avatar[A](if fromUser then "Me" else "AI", sizeCls = "size-8"),
      Bubble[A](content, if fromUser then Bubble.Variant.Sent else Bubble.Variant.Received)
    )

// ============ MESSAGE SCROLLER ============
object MessageScroller:
  def apply[A](messages: Html[A]*): Html[A] =
    div(cls := "flex flex-col gap-3 h-64 overflow-auto rounded-md border p-3 bg-background")(messages*)
