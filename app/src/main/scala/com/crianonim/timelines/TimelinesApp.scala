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
      viewportWidth: Float
  )

  enum Msg {
    case Noop
    case UpdateViewportWidth(value: String)
  }

  def init: Model = Model(
    timelines = Timeline.examples,
    viewport = Timeline.getViewportForTimelines(Timeline.examples),
    viewportWidth = 500
  )

  def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = {
    case Msg.Noop => (model, Cmd.None)
    case Msg.UpdateViewportWidth(value) =>
      value.toFloatOption match {
        case Some(width) if width > 0 => (model.copy(viewportWidth = width), Cmd.None)
        case _                        => (model, Cmd.None)
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
            value := model.viewportWidth.toString,
            onInput(value => Msg.UpdateViewportWidth(value)),
            cls := "border border-gray-300 rounded px-2 py-1 w-24"
          )
        )
      ),
      div(cls := "flex flex-col gap-1 p-2")(model.timelines.map(viewTimeline(model))),
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

    val bar = TimeLineBar.timelineToTimelineBar(model.viewport, model.viewportWidth, tl)
    div(cls := "flex gap-4 items-center")(
      div(cls := "w-[500px]", attribute("width", model.viewportWidth.toString ++ "px"))(
        viewBar(bar)
      )
    )

  }
}
