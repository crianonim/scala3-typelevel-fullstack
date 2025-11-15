package com.crianonim.timelines

import cats.effect.*
import tyrian.*
import tyrian.Html.*
//import cats.effect.std.Random
import cats.syntax.all.*

//import com.crianonim.ui.*
import com.crianonim.timelines.{Timeline, Viewport}
object TimelinesApp {
  case class Model(
      timelines: List[Timeline],
      viewport: Viewport,
      viewportWidth: Float,
      selectedTimeline: Option[Timeline]
  )

  enum Msg {
    case Noop
    case UpdateViewportWidth(value: String)
    case ToggleTimelineSelection(timeline: Timeline)
    case SetViewportStart
    case SetViewportEnd
    case SetViewportToTimeline
  }

  def init: Model = Model(
    timelines = Timeline.examples,
    viewport = Viewport.getViewportForTimelines(Timeline.examples),
    viewportWidth = 500,
    selectedTimeline = None
  )

  def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = {
    case Msg.Noop => (model, Cmd.None)
    case Msg.UpdateViewportWidth(value) =>
      value.toFloatOption match {
        case Some(width) if width > 0 => (model.copy(viewportWidth = width), Cmd.None)
        case _                        => (model, Cmd.None)
      }
    case Msg.ToggleTimelineSelection(timeline) =>
      val newSelection = model.selectedTimeline match {
        case Some(selected) if selected == timeline => None
        case _                                      => Some(timeline)
      }
      (model.copy(selectedTimeline = newSelection), Cmd.None)
    case Msg.SetViewportStart =>
      model.selectedTimeline match {
        case Some(timeline) =>
          val startTimePoint = Period.minTimePointOfPeriod(timeline.period)
          val startDate      = TimePoint.timePointFloorDate(startTimePoint)
          val newViewport    = model.viewport.copy(start = startDate)
          (model.copy(viewport = newViewport), Cmd.None)
        case None => (model, Cmd.None)
      }
    case Msg.SetViewportEnd =>
      model.selectedTimeline match {
        case Some(timeline) =>
          Period.maxTimePointOfPeriod(timeline.period) match {
            case Some(endTimePoint) =>
              val endDate     = TimePoint.timePointCeilDate(endTimePoint)
              val newViewport = model.viewport.copy(end = endDate)
              (model.copy(viewport = newViewport), Cmd.None)
            case None => (model, Cmd.None) // Started period has no end
          }
        case None => (model, Cmd.None)
      }
    case Msg.SetViewportToTimeline =>
      model.selectedTimeline match {
        case Some(timeline) =>
          val startTimePoint = Period.minTimePointOfPeriod(timeline.period)
          val startDate      = TimePoint.timePointFloorDate(startTimePoint)
          Period.maxTimePointOfPeriod(timeline.period) match {
            case Some(endTimePoint) =>
              val endDate     = TimePoint.timePointCeilDate(endTimePoint)
              val newViewport = Viewport(start = startDate, end = endDate)
              (model.copy(viewport = newViewport), Cmd.None)
            case None => // Started period has no end, only set start
              val newViewport = model.viewport.copy(start = startDate)
              (model.copy(viewport = newViewport), Cmd.None)
          }
        case None => (model, Cmd.None)
      }
  }

  def view(model: Model): Html[Msg] = {
    div(cls := "flex flex-col gap-2 p-10")(
      div(cls := "flex gap-2 text-storm-dust-700 items-center")(
        div()(text("TimeLines")),
        div(cls := "flex gap-2 items-center")(
          div()(text("Viewport Width:")),
          input(
            `type` := "number",
            value  := model.viewportWidth.toString,
            onInput(value => Msg.UpdateViewportWidth(value)),
            cls := "border border-gray-300 rounded px-2 py-1 w-24"
          )
        )
      ),
      model.selectedTimeline match {
        case Some(_) =>
          div(cls := "flex gap-2 items-center")(
            button(
              onClick(Msg.Noop),
              cls := "bg-gray-500 hover:bg-gray-600 text-white px-4 py-2 rounded"
            )(text("Unselect")),
            button(
              onClick(Msg.SetViewportStart),
              cls := "bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded"
            )(text("Start viewport")),
            button(
              onClick(Msg.SetViewportEnd),
              cls := "bg-blue-500 hover:bg-blue-600 text-white px-4 py-2 rounded"
            )(text("End viewport")),
            button(
              onClick(Msg.SetViewportToTimeline),
              cls := "bg-green-500 hover:bg-green-600 text-white px-4 py-2 rounded"
            )(text("Set viewport"))
          )
        case None => div()()
      },
      div(cls := "flex gap-4 items-center text-gray-700")(
        div(cls := "flex gap-2")(
          div(cls := "font-semibold")(text("Viewport Start:")),
          div()(text(model.viewport.start.toString))
        ),
        div(cls := "flex gap-2")(
          div(cls := "font-semibold")(text("Viewport End:")),
          div()(text(model.viewport.end.toString))
        )
      ),
      div(cls := "flex flex-col gap-1 p-2")(
        model.timelines
          .filter(Viewport.isTimelineInViewport(model.viewport, _))
          .map(viewTimeline(model))
      ),
      div(cls := "flex flex-col gap-1 p-2")(model.timelines.map(viewTimelineAsText))
    )
  }
  def viewTimePoint(t: TimePoint) = text(t.show)

  def viewPeriod(p: Period) = div()(
    p match
      case Point(point) => div(cls := "flex gap-2")(viewTimePoint(point))
      case Closed(start, end) =>
        div(cls := "flex gap-2")(viewTimePoint(start), text(" ->> "), viewTimePoint(end))
      case Started(start) => div(cls := "flex gap-2")(viewTimePoint(start), text(" -->> "))
  )
  def viewTimelineAsText(tl: Timeline) = div(cls := "flex gap-2 p-2")(
    div(cls := "")(text(tl.name)),
    viewPeriod(tl.period)
  )

  def viewBar(bar: TimeLineBar) = {
    val startPoint = bar.start.getOrElse(0)
//      div
//        [style
//      "margin-left"(String.fromFloat startPoint ++
//      "px"
//      )
//      , style
//      "width"(String.fromFloat length ++
//      "px"
//      )
//      , Attrs.
//      class "bg-sky-500 h-4 border border-black"
//      , endStyle
//      , startStyle
//      , title timeline
//    .name
//      ][]
    div(
      styles(
        "margin-left" -> (startPoint.toString ++ "px"),
        "width"       -> (bar.length.toString ++ "px"),
        "border-right-style" -> (bar.timeline.period match
          case Started(_) => "dashed"
          case _          => "solid"
        ),
        "border-left-style" -> (if bar.start.nonEmpty then "solid" else "dashed")
      ),
      title := bar.timeline.name,
      cls   := "bg-sky-500 h-4 border border-black"
    )()
  }
  def viewTimeline(model: Model)(tl: Timeline) = {
    val bar        = TimeLineBar.timelineToTimelineBar(model.viewport, model.viewportWidth, tl)
    val isSelected = model.selectedTimeline.contains(tl)
    val containerClasses =
      if isSelected then "flex gap-4 items-center bg-blue-100 p-2 rounded cursor-pointer"
      else "flex gap-4 items-center p-2 hover:bg-gray-100 cursor-pointer"

    div(
      cls := containerClasses,
      onClick(Msg.ToggleTimelineSelection(tl))
    )(
      div(cls := "w-[500px]", attribute("width", model.viewportWidth.toString ++ "px"))(
        viewBar(bar)
      )
    )
  }
}
