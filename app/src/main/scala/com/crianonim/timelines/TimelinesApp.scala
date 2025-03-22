package com.crianonim.timelines

import cats.effect.*
import tyrian.*
import tyrian.Html.*
//import cats.effect.std.Random
import cats.syntax.all.*

//import com.crianonim.ui.*
import com.crianonim.timelines
object TimelinesApp {
  case class Model(timelines: List[Timeline])

  enum Msg {
    case Noop
  }

  def init: Model = Model(Timeline.examples)

  def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = msg => (model,Cmd.None)

  def view(model: Model): Html[Msg] = {
    div(cls := "flex flex-col gap-2 p-10")(
      div(cls := "flex gap-2 text-storm-dust-700 items-center")(
        text("TimeLines"),
      ),
      div(cls:="flex flex-col gap-1 p-2")(model.timelines.map(viewTimeline)),
      div(cls:="flex flex-col gap-1 p-2")(model.timelines.map(viewTimelineAsText))
    )
  }
  def viewTimePoint(t:TimePoint) = text(t.show)

  def viewPeriod(p:Period)=div()(
    p match
      case Point(point) => div(cls:="flex gap-2")(viewTimePoint(point))
      case Closed(start, end) => div(cls:="flex gap-2")(viewTimePoint(start), text(" >> "), viewTimePoint(end))
      case Started(start) => div(cls:="flex gap-2")(viewTimePoint(start),text(" >> "))
  )
  def viewTimelineAsText(tl: Timeline) = div(cls:="flex gap-2 p-2")(
    div(cls:="")(text(tl.name)),viewPeriod(tl.period)
  )

  def viewBar(bar:TimeLineBar)=
    {
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
      div(styles(
        "margin-left" -> (startPoint.toString++"px"),
        "width"->(bar.length.toString++"px"),
        "border-right-style" -> (bar.timeline.period match
          case Started(_) => "dashed"
          case _ => "solid"),
        "border-left-style" ->  (if bar.start.nonEmpty  then "solid" else "dashed")

      ),
        title:=bar.timeline.name,
        cls:="bg-sky-500 h-4 border border-black" )()
    }
  def viewTimeline(tl:Timeline)={

    val bar = TimeLineBar.timelineToTimelineBar(Timeline.viewport,500,tl)
    div(cls:="flex gap-4 items-center")(
      div(cls:="w-[500px]")
        (
        viewBar(bar)
      )
    )

  }
}
