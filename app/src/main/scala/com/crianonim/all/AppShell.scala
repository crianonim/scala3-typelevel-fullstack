package com.crianonim.all

import tyrian.*
import tyrian.Html.*

import com.crianonim.shadcn.Button
import com.crianonim.shadcn.Icons

/** Shared full-screen chrome for every app.
  *
  * Two shapes:
  *   - [[landing]]: the main page, listing every app as a stacked full-width button.
  *   - [[appFrame]]: one app filling the viewport, under a thin top bar with a Back to Main button
  *     on the left and the app name + version centered.
  *
  * Styling uses the shadcn design tokens (see `app/index.css` / `app/tailwind.config.js`) so the
  * shell re-themes with the rest of the UI. Layout is mobile-first: single column, full-width
  * targets, and the version drops out on very small screens to keep the bar thin.
  */
object AppShell {

  /** Placeholder app version, shown in the top bar. Wire to a build value later. */
  val Version = "0.1"

  private def versionBadge[A]: Html[A] =
    span(cls := "hidden shrink-0 text-xs text-muted-foreground sm:inline")(
      text(s"Version $Version")
    )

  /** The main landing page: a centered column with a heading and one full-width button per app. */
  def landing[A](apps: List[(String, A)]): Html[A] =
    div(
      cls := "flex min-h-screen flex-col items-center bg-background px-4 py-8 text-foreground sm:py-12"
    )(
      div(cls := "mb-6 flex w-full max-w-md flex-col items-center gap-1")(
        h1(cls := "text-2xl font-semibold tracking-tight")(text("Apps")),
        versionBadge[A]
      ),
      div(cls := "flex w-full max-w-md flex-col gap-3")(
        apps.map { case (label, msg) =>
          Button.withContent(
            msg,
            Button.Variant.Outline,
            Button.Size.Lg,
            "h-12 w-full justify-between px-4 text-base"
          )(
            span()(text(label)),
            Icons.chevronRight[A]("size-4 opacity-60")
          )
        }
      )
    )

  /** One app filling the viewport, below the thin top bar. `content` is padded and scrollable. */
  def appFrame[A](title: String, backMsg: A)(content: Html[A]): Html[A] =
    div(cls := "flex h-screen flex-col bg-background text-foreground")(
      topBar(title, backMsg),
      div(cls := "min-h-0 flex-1 overflow-y-auto p-4 sm:p-6")(content)
    )

  private def topBar[A](title: String, backMsg: A): Html[A] =
    div(
      cls := "grid h-12 shrink-0 grid-cols-3 items-center border-b px-2 sm:px-4"
    )(
      div(cls := "justify-self-start")(
        Button.withContent(
          backMsg,
          Button.Variant.Ghost,
          Button.Size.Sm,
          "gap-1 whitespace-nowrap px-2"
        )(
          Icons.chevronLeft[A]("size-4"),
          span()(text("Back to Main"))
        )
      ),
      div(cls := "flex min-w-0 items-center justify-center gap-2 justify-self-center")(
        span(cls := "truncate text-base font-semibold")(text(title)),
        versionBadge[A]
      ),
      // Empty third column so the title is centered against the grid, not the free space.
      div()()
    )
}
