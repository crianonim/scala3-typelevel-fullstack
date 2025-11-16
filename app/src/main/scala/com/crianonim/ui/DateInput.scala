package com.crianonim.ui

import tyrian.Html.*
import tyrian.*
import com.crianonim.timelines.*

object DateInput {

  /** State for the date input component
    *
    * @param year
    *   Year value (required)
    * @param month
    *   Month value (1-12, optional)
    * @param day
    *   Day value (1-31, optional)
    */
  case class DateState(
      year: Option[Int] = None,
      month: Option[Int] = None,
      day: Option[Int] = None
  ) {

    /** Converts the date state to a TimePoint if valid
      */
    def toTimePoint: Option[TimePoint] = {
      year match {
        case None => None
        case Some(y) =>
          (month, day) match {
            case (None, _)          => Some(YearOnly(y))
            case (Some(m), None)    => Some(YearMonth(y, m))
            case (Some(m), Some(d)) => Some(YearMonthDay(y, m, d))
          }
      }
    }
  }

  object DateState {

    /** Creates a DateState from a TimePoint
      */
    def fromTimePoint(tp: TimePoint): DateState = tp match {
      case YearOnly(year)                 => DateState(Some(year), None, None)
      case YearMonth(year, month)         => DateState(Some(year), Some(month), None)
      case YearMonthDay(year, month, day) => DateState(Some(year), Some(month), Some(day))
    }
  }

  /** Renders a date input with flexible granularity (year/month/day)
    *
    * @param label
    *   Label for the date input group
    * @param state
    *   Current date state
    * @param onChange
    *   Message to send when the date state changes
    * @return
    *   HTML element representing the date input
    */
  def apply[A](
      label: String,
      state: DateState,
      onChange: DateState => A
  ): Html[A] = {
    div(cls := "flex flex-col gap-2")(
      div(cls := "font-medium text-sm text-gray-700")(text(label)),
      div(cls := "flex gap-2 items-center")(
        // Year input (required)
        div(cls := "flex flex-col gap-1 flex-1")(
          div(cls := "text-xs text-gray-600")(text("Year")),
          input(
            cls := "px-3 py-2 rounded border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none",
            `type` := "number",
            attribute("placeholder", "YYYY"),
            attribute("min", "1"),
            attribute("max", "9999"),
            value := state.year.map(_.toString).getOrElse(""),
            onInput(value =>
              onChange(
                state.copy(year =
                  if (value.isEmpty) None else value.toIntOption
                )
              )
            )
          )
        ),
        // Month input (optional)
        div(cls := "flex flex-col gap-1 flex-1")(
          div(cls := "text-xs text-gray-600")(text("Month (optional)")),
          input(
            cls := "px-3 py-2 rounded border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none",
            `type` := "number",
            attribute("placeholder", "MM"),
            attribute("min", "1"),
            attribute("max", "12"),
            value := state.month.map(_.toString).getOrElse(""),
            onInput(value =>
              onChange(
                state.copy(
                  month = if (value.isEmpty) None else value.toIntOption,
                  day = if (value.isEmpty) None else state.day // Clear day if month is cleared
                )
              )
            )
          )
        ),
        // Day input (optional, only enabled if month is set)
        div(cls := "flex flex-col gap-1 flex-1")(
          div(cls := "text-xs text-gray-600")(text("Day (optional)")),
          {
            val baseAttrs = List(
              cls := s"px-3 py-2 rounded border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none ${if (state.month.isEmpty) "bg-gray-100 cursor-not-allowed" else ""}",
              `type` := "number",
              attribute("placeholder", "DD"),
              attribute("min", "1"),
              attribute("max", "31"),
              value := state.day.map(_.toString).getOrElse(""),
              onInput(value =>
                onChange(
                  state.copy(day = if (value.isEmpty) None else value.toIntOption)
                )
              )
            )
            val attrs = if (state.month.isEmpty) {
              baseAttrs :+ attribute("disabled", "true")
            } else {
              baseAttrs
            }
            input(attrs*)
          }
        )
      ),
      // Display the resulting TimePoint
      state.toTimePoint match {
        case Some(tp) =>
          div(cls := "text-xs text-gray-500 italic")(
            text(s"Preview: ${cats.Show[TimePoint].show(tp)}")
          )
        case None =>
          div(cls := "text-xs text-red-500")(
            text("Enter at least a year")
          )
      }
    )
  }

  /** Simplified version that works with Option[TimePoint] directly
    *
    * @param label
    *   Label for the date input group
    * @param timePoint
    *   Current time point value
    * @param onChange
    *   Message to send when the time point changes
    * @return
    *   HTML element representing the date input
    */
  def simple[A](
      label: String,
      timePoint: Option[TimePoint],
      onChange: Option[TimePoint] => A
  ): Html[A] = {
    val state = timePoint.map(DateState.fromTimePoint).getOrElse(DateState())
    apply(
      label,
      state,
      (newState: DateState) => onChange(newState.toTimePoint)
    )
  }
}
