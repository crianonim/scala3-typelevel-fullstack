package com.crianonim.timelines

import cats.effect.*
import tyrian.*
import tyrian.Html.*
//import cats.effect.std.Random
import cats.syntax.all.*

import java.time.LocalDate
import java.time.ZoneId
import com.crianonim.ui.*
import com.crianonim.timelines.{Timeline, Viewport}
object TimelinesApp {
  case class Model(
      timelines: List[Timeline],
      viewport: Viewport,
      selectedTimeline: Option[Timeline]
  )

  enum Msg {
    case Noop
    case ToggleTimelineSelection(timeline: Timeline)
    case UnselectTimeline
    case SetViewportStart
    case SetViewportEnd
    case SetViewportToTimeline
    case SetViewportEndToNow
    case ResetViewport
  }

  def init: Model = Model(
    timelines = Timeline.examples,
    viewport = Viewport.getViewportForTimelines(Timeline.examples),
    selectedTimeline = None
  )

  def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = {
    case Msg.Noop => (model, Cmd.None)
    case Msg.ToggleTimelineSelection(timeline) =>
      val newSelection = model.selectedTimeline match {
        case Some(selected) if selected == timeline => None
        case _                                      => Some(timeline)
      }
      (model.copy(selectedTimeline = newSelection), Cmd.None)
    case Msg.UnselectTimeline =>
      (model.copy(selectedTimeline = None), Cmd.None)
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
    case Msg.SetViewportEndToNow =>
      val today       = LocalDate.now(ZoneId.of("UTC"))
      val newViewport = model.viewport.copy(end = today)
      (model.copy(viewport = newViewport), Cmd.None)
    case Msg.ResetViewport =>
      val newViewport = Viewport.getViewportForTimelines(model.timelines)
      (model.copy(viewport = newViewport), Cmd.None)
  }

  def view(model: Model): Html[Msg] = {
    div(cls := "flex flex-col gap-2 p-10")(
      div(cls := "flex gap-2 text-storm-dust-700 items-center")(
        div()(text("TimeLines"))
      ),
      model.selectedTimeline match {
        case Some(_) =>
          div(cls := "flex gap-2 items-center")(
            Button.secondary("Unselect", Msg.UnselectTimeline, Button.Size.Small),
            Button.secondary("Start viewport", Msg.SetViewportStart, Button.Size.Small),
            Button.secondary("End viewport", Msg.SetViewportEnd, Button.Size.Small),
            Button.secondary("Set viewport", Msg.SetViewportToTimeline, Button.Size.Small)
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
        ),
        Button.secondary("Reset Viewport", Msg.ResetViewport, Button.Size.Small),
        Button.secondary("End now", Msg.SetViewportEndToNow, Button.Size.Small)
      ),
      Card.simple()(
        div(cls := "flex flex-col gap-1")(
          model.timelines
            .filter(Viewport.isTimelineInViewport(model.viewport, _))
            .map(viewTimeline(model))*
        )
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
    val startPercent = bar.start.getOrElse(0f)
    div(
      styles(
        "position"     -> "absolute",
        "left"         -> (startPercent.toString ++ "%"),
        "width"        -> (bar.length.toString ++ "%"),
        "border-right-style" -> (bar.timeline.period match {
          case Started(_) => "dashed"
          case _          => "solid"
        }),
        "border-left-style" -> (if bar.start.nonEmpty then "solid" else "dashed")
      ),
      title := bar.timeline.name,
      cls   := "bg-sky-500 h-4 border border-black"
    )()
  }
  def viewTimeline(model: Model)(tl: Timeline) = {
    val bar        = TimeLineBar.timelineToTimelineBar(model.viewport, tl)
    val isSelected = model.selectedTimeline.contains(tl)
    val containerClasses =
      if isSelected then "flex gap-4 items-center bg-blue-100 p-2 rounded cursor-pointer"
      else "flex gap-4 items-center p-2 hover:bg-gray-100 cursor-pointer"

    div(
      cls := containerClasses,
      onClick(Msg.ToggleTimelineSelection(tl))
    )(
      div(cls := "w-full relative h-4")(
        viewBar(bar)
      )
    )
  }
}
