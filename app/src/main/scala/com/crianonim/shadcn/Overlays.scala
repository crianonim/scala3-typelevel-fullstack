package com.crianonim.shadcn

import tyrian.*
import tyrian.Html.*

/** shadcn/ui overlay & disclosure components. All are state-driven: the caller passes an `open`
  * flag and the messages to toggle/close; positioning is CSS-based (no floating-UI library).
  */

// ============ DIALOG ============
object Dialog:
  def apply[A](open: Boolean, onClose: A, title: String, description: String = "")(
      content: Html[A]*
  ): Html[A] =
    if !open then span()()
    else
      div(cls := "fixed inset-0 z-50 flex items-center justify-center p-4")(
        div(cls := "absolute inset-0 bg-black/50", onClick(onClose))(),
        div(cls := "relative z-10 w-full max-w-lg rounded-lg border bg-background text-foreground p-6 shadow-lg flex flex-col gap-4")(
          button(cls := "absolute right-4 top-4 opacity-70 hover:opacity-100 cursor-pointer", onClick(onClose))(
            Icons.x[A]("size-4")
          ),
          div(cls := "flex flex-col gap-1.5")(
            div(cls := "text-lg font-semibold leading-none tracking-tight")(text(title)),
            if description.isEmpty then span()()
            else div(cls := "text-sm text-muted-foreground")(text(description))
          ),
          div(cls := "flex flex-col gap-2")(content*)
        )
      )

// ============ ALERT DIALOG ============
object AlertDialog:
  def apply[A](
      open: Boolean,
      title: String,
      description: String,
      onCancel: A,
      onConfirm: A,
      cancelLabel: String = "Cancel",
      confirmLabel: String = "Continue"
  ): Html[A] =
    if !open then span()()
    else
      div(cls := "fixed inset-0 z-50 flex items-center justify-center p-4")(
        div(cls := "absolute inset-0 bg-black/50", onClick(onCancel))(),
        div(cls := "relative z-10 w-full max-w-lg rounded-lg border bg-background text-foreground p-6 shadow-lg flex flex-col gap-4")(
          div(cls := "flex flex-col gap-2")(
            div(cls := "text-lg font-semibold")(text(title)),
            div(cls := "text-sm text-muted-foreground")(text(description))
          ),
          div(cls := "flex justify-end gap-2")(
            Button(cancelLabel, onCancel, Button.Variant.Outline),
            Button(confirmLabel, onConfirm, Button.Variant.Default)
          )
        )
      )

// ============ SHEET (side panel) ============
object Sheet:
  def apply[A](open: Boolean, onClose: A, title: String, description: String = "")(
      content: Html[A]*
  ): Html[A] =
    if !open then span()()
    else
      div(cls := "fixed inset-0 z-50")(
        div(cls := "absolute inset-0 bg-black/50", onClick(onClose))(),
        div(cls := "absolute right-0 top-0 h-full w-80 max-w-full border-l bg-background text-foreground p-6 shadow-lg flex flex-col gap-4")(
          button(cls := "absolute right-4 top-4 opacity-70 hover:opacity-100 cursor-pointer", onClick(onClose))(
            Icons.x[A]("size-4")
          ),
          div(cls := "flex flex-col gap-1.5")(
            div(cls := "text-lg font-semibold")(text(title)),
            if description.isEmpty then span()()
            else div(cls := "text-sm text-muted-foreground")(text(description))
          ),
          div(cls := "flex flex-col gap-2")(content*)
        )
      )

// ============ DRAWER (bottom panel) ============
object Drawer:
  def apply[A](open: Boolean, onClose: A, title: String)(content: Html[A]*): Html[A] =
    if !open then span()()
    else
      div(cls := "fixed inset-0 z-50")(
        div(cls := "absolute inset-0 bg-black/50", onClick(onClose))(),
        div(cls := "absolute inset-x-0 bottom-0 max-h-[80%] rounded-t-lg border-t bg-background text-foreground p-6 shadow-lg flex flex-col gap-4")(
          div(cls := "mx-auto h-1.5 w-12 rounded-full bg-muted")(),
          div(cls := "text-lg font-semibold")(text(title)),
          div(cls := "flex flex-col gap-2")(content*),
          div(cls := "flex justify-end")(Button("Close", onClose, Button.Variant.Outline))
        )
      )

// ============ POPOVER ============
object Popover:
  def apply[A](open: Boolean, triggerLabel: String, onToggle: A)(content: Html[A]*): Html[A] =
    div(cls := "relative inline-block")(
      button(
        cls := "inline-flex items-center justify-center h-9 px-4 py-2 rounded-md border border-input bg-background shadow-sm text-sm font-medium hover:bg-accent hover:text-accent-foreground cursor-pointer",
        onClick(onToggle)
      )(text(triggerLabel)),
      if open then
        div(cls := "absolute left-0 top-full z-20 mt-2 w-72 rounded-md border bg-popover text-popover-foreground p-4 shadow-md")(content*)
      else span()()
    )

// ============ HOVER CARD (CSS hover) ============
object HoverCard:
  def apply[A](triggerLabel: String)(content: Html[A]*): Html[A] =
    div(cls := "relative inline-flex group")(
      button(cls := "text-sm underline underline-offset-4 cursor-pointer")(text(triggerLabel)),
      div(cls := "absolute left-0 top-full z-30 mt-2 hidden w-64 rounded-md border bg-popover text-popover-foreground p-4 shadow-md group-hover:block")(
        content*
      )
    )

// ============ TOOLTIP (CSS hover) ============
object Tooltip:
  def apply[A](tip: String)(child: Html[A]): Html[A] =
    div(cls := "relative inline-flex group")(
      child,
      div(cls := "pointer-events-none absolute bottom-full left-1/2 z-30 mb-2 hidden -translate-x-1/2 whitespace-nowrap rounded-md bg-primary px-2 py-1 text-xs text-primary-foreground shadow group-hover:block")(
        text(tip)
      )
    )

// ============ DROPDOWN MENU ============
object DropdownMenu:
  def apply[A](open: Boolean, triggerLabel: String, onToggle: A)(items: Html[A]*): Html[A] =
    div(cls := "relative inline-block")(
      button(
        cls := "inline-flex items-center gap-2 justify-center h-9 px-4 py-2 rounded-md border border-input bg-background shadow-sm text-sm font-medium hover:bg-accent hover:text-accent-foreground cursor-pointer",
        onClick(onToggle)
      )(span()(text(triggerLabel)), Icons.chevronDown[A]("size-4")),
      if open then
        div(cls := "absolute left-0 top-full z-20 mt-1 min-w-48 rounded-md border bg-popover text-popover-foreground shadow-md p-1 flex flex-col")(
          items*
        )
      else span()()
    )

  def item[A](label_ : String, msg: A): Html[A] =
    div(
      cls := "rounded-sm px-2 py-1.5 text-sm hover:bg-accent hover:text-accent-foreground cursor-pointer",
      onClick(msg)
    )(text(label_))

  def label[A](t: String): Html[A] =
    div(cls := "px-2 py-1.5 text-xs font-medium text-muted-foreground")(text(t))

  def separator[A]: Html[A] = div(cls := "my-1 h-px bg-border")()

// ============ CONTEXT MENU (click-to-open target) ============
object ContextMenu:
  def apply[A](open: Boolean, onToggle: A)(items: Html[A]*): Html[A] =
    div(cls := "relative")(
      div(
        cls := "flex h-32 items-center justify-center rounded-md border border-dashed text-sm text-muted-foreground cursor-context-menu select-none",
        onClick(onToggle)
      )(text("Click here to open the menu")),
      if open then
        div(cls := "absolute left-4 top-12 z-20 min-w-40 rounded-md border bg-popover text-popover-foreground shadow-md p-1 flex flex-col")(
          items*
        )
      else span()()
    )

// ============ ACCORDION ============
object Accordion:
  def apply[A](
      items: List[(String, String, Html[A])],
      openIds: Set[String],
      onToggle: String => A
  ): Html[A] =
    div(cls := "w-full rounded-md border divide-y")(
      items.map { case (id, title, content) =>
        val isOpen = openIds.contains(id)
        div()(
          button(
            cls := "flex w-full items-center justify-between px-4 py-3 text-sm font-medium hover:underline cursor-pointer",
            onClick(onToggle(id))
          )(
            span()(text(title)),
            if isOpen then Icons.chevronUp[A]("size-4") else Icons.chevronDown[A]("size-4")
          ),
          if isOpen then div(cls := "px-4 pb-3 text-sm text-muted-foreground")(content) else span()()
        )
      }
    )

// ============ COLLAPSIBLE ============
object Collapsible:
  def apply[A](open: Boolean, onToggle: A, triggerLabel: String)(content: Html[A]*): Html[A] =
    div(cls := "w-full rounded-md border")(
      button(
        cls := "flex w-full items-center justify-between px-4 py-2 text-sm font-medium cursor-pointer",
        onClick(onToggle)
      )(
        span()(text(triggerLabel)),
        if open then Icons.chevronUp[A]("size-4") else Icons.chevronDown[A]("size-4")
      ),
      if open then div(cls := "px-4 pb-3 flex flex-col gap-2")(content*) else span()()
    )

// ============ SELECT (custom dropdown) ============
object Select:
  def apply[A](
      open: Boolean,
      value_ : String,
      placeholder_ : String,
      options: List[(String, String)],
      onToggle: A,
      onSelect: String => A
  ): Html[A] =
    val shown =
      if value_.isEmpty then placeholder_
      else options.find(_._1 == value_).map(_._2).getOrElse(value_)
    div(cls := "relative w-56")(
      button(
        cls := "flex h-9 w-full items-center justify-between rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm cursor-pointer",
        onClick(onToggle)
      )(
        span(cls := (if value_.isEmpty then "text-muted-foreground" else ""))(text(shown)),
        Icons.chevronDown[A]("size-4 opacity-50")
      ),
      if open then
        div(cls := "absolute left-0 top-full z-20 mt-1 w-full rounded-md border bg-popover text-popover-foreground shadow-md p-1")(
          options.map { case (v, lbl) =>
            div(
              cls := "flex items-center justify-between rounded-sm px-2 py-1.5 text-sm hover:bg-accent hover:text-accent-foreground cursor-pointer",
              onClick(onSelect(v))
            )(span()(text(lbl)), if v == value_ then Icons.check[A]("size-4") else span()())
          }
        )
      else span()()
    )
