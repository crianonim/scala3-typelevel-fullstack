package com.crianonim.ui

import tyrian.Html.*
import tyrian.*

object Tooltip {

  /** Wraps an HTML element with tooltip functionality that shows on hover.
    *
    * @param content
    *   The HTML content to display in the tooltip
    * @param child
    *   The HTML element that will trigger the tooltip on hover
    * @return
    *   A wrapped element with tooltip behavior
    */

  private def renderTooltip[A](content: Html[A], child: Html[A], expanded: Boolean = false): Html[A] =
    div(cls := s"relative inline-block group ${if (expanded) "h-full w-full" else ""}")(
      child,
      div(
        cls := "absolute hidden group-hover:block bg-gray-800 text-white text-sm rounded px-3 py-2 z-50 whitespace-nowrap bottom-full left-1/2 -translate-x-1/2 mb-2 pointer-events-none",
        attribute("role", "tooltip")
      )(
        content,
        // Tooltip arrow
        div(
          cls := "absolute top-full left-1/2 -translate-x-1/2 -mt-1 w-0 h-0 border-l-4 border-r-4 border-t-4 border-l-transparent border-r-transparent border-t-gray-800"
        )()
      )
    )

  def apply[A](content: Html[A], child: Html[A]): Html[A] =
    renderTooltip(content, child, false)

  def expanded[A](content: Html[A], child: Html[A]): Html[A] =
    renderTooltip(content, child, true)

  /** Creates a tooltip with simple text content.
    *
    * @param text
    *   The text to display in the tooltip
    * @param child
    *   The HTML element that will trigger the tooltip on hover
    * @return
    *   A wrapped element with tooltip behavior
    */
  def text[A](text: String, child: Html[A]): Html[A] =
    apply(Html.div()(Html.text(text)), child)

  /** Creates a tooltip with custom positioning.
    *
    * @param content
    *   The HTML content to display in the tooltip
    * @param child
    *   The HTML element that will trigger the tooltip on hover
    * @param position
    *   Position of the tooltip: "top" (default), "bottom", "left", "right"
    * @return
    *   A wrapped element with tooltip behavior
    */
  def withPosition[A](content: Html[A], child: Html[A], position: String = "top"): Html[A] = {
    val (tooltipClasses, arrowClasses) = position match {
      case "bottom" =>
        (
          "absolute hidden group-hover:block bg-gray-800 text-white text-sm rounded px-3 py-2 z-50 whitespace-nowrap top-full left-1/2 -translate-x-1/2 mt-2 pointer-events-none",
          "absolute bottom-full left-1/2 -translate-x-1/2 -mb-1 w-0 h-0 border-l-4 border-r-4 border-b-4 border-l-transparent border-r-transparent border-b-gray-800"
        )
      case "left" =>
        (
          "absolute hidden group-hover:block bg-gray-800 text-white text-sm rounded px-3 py-2 z-50 whitespace-nowrap right-full top-1/2 -translate-y-1/2 mr-2 pointer-events-none",
          "absolute left-full top-1/2 -translate-y-1/2 -ml-1 w-0 h-0 border-t-4 border-b-4 border-l-4 border-t-transparent border-b-transparent border-l-gray-800"
        )
      case "right" =>
        (
          "absolute hidden group-hover:block bg-gray-800 text-white text-sm rounded px-3 py-2 z-50 whitespace-nowrap left-full top-1/2 -translate-y-1/2 ml-2 pointer-events-none",
          "absolute right-full top-1/2 -translate-y-1/2 -mr-1 w-0 h-0 border-t-4 border-b-4 border-r-4 border-t-transparent border-b-transparent border-r-gray-800"
        )
      case _ => // "top" (default)
        (
          "absolute hidden group-hover:block bg-gray-800 text-white text-sm rounded px-3 py-2 z-50 whitespace-nowrap bottom-full left-1/2 -translate-x-1/2 mb-2 pointer-events-none",
          "absolute top-full left-1/2 -translate-x-1/2 -mt-1 w-0 h-0 border-l-4 border-r-4 border-t-4 border-l-transparent border-r-transparent border-t-gray-800"
        )
    }

    div(cls := "relative inline-block group")(
      child,
      div(
        cls := tooltipClasses,
        attribute("role", "tooltip")
      )(
        content,
        // Tooltip arrow
        div(cls := arrowClasses)()
      )
    )
  }
}
