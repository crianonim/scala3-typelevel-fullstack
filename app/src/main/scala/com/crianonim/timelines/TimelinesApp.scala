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
      div(cls:="flex flex-col gap-1 p-2")(model.timelines.map(viewTimeline))
    )
  }
  def viewTimePoint(t:TimePoint) = text(t.show)
  def viewPeriod(p:Period)=div()(
    p match
      case Point(point) => div(cls:="flex gap-2")(viewTimePoint(point))
      case Closed(start, end) => div(cls:="flex gap-2")(viewTimePoint(start), text(" >> "), viewTimePoint(end))
      case Started(start) => div(cls:="flex gap-2")(viewTimePoint(start),text(" >> "))
  )
  def viewTimeline(tl: Timeline) = div(cls:="flex gap-2 p-2")(
    div(cls:="")(text(tl.name)),viewPeriod(tl.period)
  )
}
