package com.crianonim.shadcn

import tyrian.*
import tyrian.Html.*

/** shadcn/ui navigation components. Stateful ones (tabs, menubar, navigation-menu, pagination)
  * take current state + a selection message; state lives in the caller's model.
  */

// ============ BREADCRUMB ============
object Breadcrumb:
  /** `items` last element is rendered as the current page. */
  def apply[A](items: List[String]): Html[A] =
    val n = items.length
    div(cls := "flex items-center gap-1.5 text-sm text-muted-foreground")(
      items.zipWithIndex.flatMap { case (label, i) =>
        val isLast = i == n - 1
        val crumb =
          if isLast then span(cls := "text-foreground font-normal")(text(label))
          else span(cls := "hover:text-foreground cursor-pointer transition-colors")(text(label))
        if isLast then List(crumb)
        else List(crumb, Icons.chevronRight[A]("size-3.5"))
      }
    )

// ============ PAGINATION ============
object Pagination:
  def apply[A](current: Int, total: Int, onPage: Int => A): Html[A] =
    def pageBtn(p: Int): Html[A] =
      val activeCls =
        if p == current then "border border-input bg-background shadow-sm"
        else "hover:bg-accent hover:text-accent-foreground"
      button(
        cls := s"inline-flex items-center justify-center h-9 w-9 rounded-md text-sm cursor-pointer $activeCls",
        onClick(onPage(p))
      )(text(p.toString))
    val prev =
      button(
        cls := "inline-flex items-center gap-1 h-9 px-2.5 rounded-md text-sm hover:bg-accent hover:text-accent-foreground cursor-pointer",
        onClick(onPage(math.max(1, current - 1)))
      )(Icons.chevronLeft[A]("size-4"), span()(text("Prev")))
    val next =
      button(
        cls := "inline-flex items-center gap-1 h-9 px-2.5 rounded-md text-sm hover:bg-accent hover:text-accent-foreground cursor-pointer",
        onClick(onPage(math.min(total, current + 1)))
      )(span()(text("Next")), Icons.chevronRight[A]("size-4"))
    div(cls := "flex items-center gap-1")(
      (prev :: (1 to total).toList.map(pageBtn)) :+ next
    )

// ============ TABS ============
object Tabs:
  def apply[A](
      items: List[(String, String)],
      active: String,
      onSelect: String => A
  )(content: Html[A]): Html[A] =
    div(cls := "flex flex-col gap-2")(
      div(cls := "inline-flex h-9 w-fit items-center justify-center rounded-lg bg-muted p-1 text-muted-foreground")(
        items.map { case (id, lbl) =>
          val activeCls = if id == active then "bg-background text-foreground shadow" else ""
          button(
            cls := s"inline-flex items-center justify-center rounded-md px-3 py-1 text-sm font-medium transition-all cursor-pointer $activeCls",
            onClick(onSelect(id))
          )(text(lbl))
        }
      ),
      div(cls := "mt-2")(content)
    )

// ============ NAVIGATION MENU ============
object NavigationMenu:
  case class Entry(label: String, description: String)

  def apply[A](
      items: List[(String, String, List[Entry])],
      openId: Option[String],
      onToggle: String => A
  ): Html[A] =
    div(cls := "relative flex items-center gap-1 rounded-md border border-input p-1 w-fit")(
      items.map { case (id, lbl, entries) =>
        val isOpen = openId.contains(id)
        div(cls := "relative")(
          button(
            cls := s"inline-flex items-center gap-1 rounded-md px-3 py-1.5 text-sm font-medium hover:bg-accent hover:text-accent-foreground cursor-pointer ${if isOpen then "bg-accent text-accent-foreground" else ""}",
            onClick(onToggle(id))
          )(span()(text(lbl)), Icons.chevronDown[A]("size-3.5")),
          if isOpen then
            div(cls := "absolute left-0 top-full z-20 mt-1 w-64 rounded-md border bg-popover text-popover-foreground shadow-md p-2 flex flex-col gap-1")(
              entries.map(e =>
                div(cls := "rounded-md p-2 hover:bg-accent cursor-pointer")(
                  div(cls := "text-sm font-medium")(text(e.label)),
                  div(cls := "text-xs text-muted-foreground")(text(e.description))
                )
              )
            )
          else span()()
        )
      }
    )

// ============ MENUBAR ============
object Menubar:
  def apply[A](
      menus: List[(String, String, List[String])],
      openId: Option[String],
      onToggle: String => A,
      onItem: String => A
  ): Html[A] =
    div(cls := "flex items-center gap-1 rounded-md border border-input bg-background p-1 shadow-sm w-fit")(
      menus.map { case (id, lbl, entries) =>
        val isOpen = openId.contains(id)
        div(cls := "relative")(
          button(
            cls := s"rounded-sm px-3 py-1 text-sm font-medium hover:bg-accent hover:text-accent-foreground cursor-pointer ${if isOpen then "bg-accent text-accent-foreground" else ""}",
            onClick(onToggle(id))
          )(text(lbl)),
          if isOpen then
            div(cls := "absolute left-0 top-full z-20 mt-1 min-w-40 rounded-md border bg-popover text-popover-foreground shadow-md p-1 flex flex-col")(
              entries.map(e =>
                div(cls := "rounded-sm px-2 py-1.5 text-sm hover:bg-accent hover:text-accent-foreground cursor-pointer", onClick(onItem(e)))(text(e))
              )
            )
          else span()()
        )
      }
    )

// ============ SIDEBAR ============
object Sidebar:
  case class Item(label: String, icon: String)

  def apply[A](title: String, items: List[Item], active: String, onSelect: String => A): Html[A] =
    div(cls := "w-56 shrink-0 rounded-lg border bg-sidebar text-sidebar-foreground p-2 flex flex-col gap-1")(
      div(cls := "px-2 py-1.5 text-xs font-medium text-muted-foreground uppercase tracking-wide")(text(title)) ::
        items.map { it =>
          val activeCls =
            if it.label == active then "bg-sidebar-accent text-sidebar-accent-foreground"
            else "hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
          button(
            cls := s"flex items-center gap-2 rounded-md px-2 py-1.5 text-sm cursor-pointer text-left $activeCls",
            onClick(onSelect(it.label))
          )(iconFor(it.icon), span()(text(it.label)))
        }
    )

  private def iconFor[A](name: String): Html[A] = name match
    case "user" => Icons.user[A]("size-4")
    case "bell" => Icons.bell[A]("size-4")
    case "star" => Icons.star[A]("size-4")
    case _      => Icons.circleIcon[A]("size-4")
