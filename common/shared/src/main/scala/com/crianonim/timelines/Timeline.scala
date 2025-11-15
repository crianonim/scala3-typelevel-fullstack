package com.crianonim.timelines
import cats.Show
// import cats.implicits.showInterpolator
import cats.syntax.all.*
import com.crianonim.timelines

import java.time.LocalDate
import java.time.temporal.ChronoUnit

sealed trait Period

object Period {
  given Show[Period] = new Show[Period]:
    override def show(p: Period): String = p match
      case Point(point)       => point.show
      case Closed(start, end) => show"${start} >> ${end}"
      case Started(start)     => show"${start} >> ..."

//  // create an Ordering instance for Period
//    given Ordering[Period] = new Ordering[Period] {
//        override def compare(x: Period, y: Period): Int = (x, y) match
//        case (Point(x), Point(y)) => x.toString.compare(y.toString)
//        case (Closed(x1, x2), Closed(y1, y2)) => (x1, x2).compare(y1, y2)
//        case (Started(x), Started(y)) => x.compare(y)
//        case (Point(_), _) => -1
//        case (_, Point(_)) => 1
//        case (Closed(_, _), _) => -1
//        case (_, Closed(_, _)) => 1
//    }
  def minTimePointOfPeriod(p: Period): TimePoint = p match
    case Point(point)       => point
    case Closed(start, end) => start
    case Started(start)     => start

  def maxTimePointOfPeriod(p: Period): Option[TimePoint] = p match
    case Point(point)       => point.some
    case Closed(start, end) => end.some
    case Started(start)     => None

//  def isPeriodFinished(p:Period) : Boolean = p match
//    case Point(point) => true
//    case Closed(start, end) => true
//    case Started(start) => false
  //  def startOfListOfPeriods(ps: List[Period]): TimePoint =

}

case class Point(point: TimePoint) extends Period

case class Closed(start: TimePoint, end: TimePoint) extends Period

case class Started(start: TimePoint) extends Period

sealed trait TimePoint

object TimePoint {
  given Show[TimePoint] = new Show[TimePoint]:
    override def show(t: TimePoint): String = t match
      case YearOnly(year)         => year.toString
      case YearMonth(year, month) => year.toString ++ "-" ++ month.toString.padTo(2, '0').reverse
      case YearMonthDay(year, month, day) =>
        year.toString ++ "-" ++ month.toString.padTo(2, '0').reverse ++ "-" ++ day.toString
          .padTo(2, '0')
          .reverse

  def timePointFloorDate(t: TimePoint): LocalDate =
    t match
      case YearOnly(year)                 => LocalDate.of(year, 1, 1)
      case YearMonth(year, month)         => LocalDate.of(year, month, 1)
      case YearMonthDay(year, month, day) => LocalDate.of(year, month, day)

  def timePointCeilDate(t: TimePoint): LocalDate =
    t match
      case YearOnly(year)                 => LocalDate.of(year, 12, 31)
      case YearMonth(year, month)         => LocalDate.of(year, month, 1).plusMonths(1).minusDays(1)
      case YearMonthDay(year, month, day) => LocalDate.of(year, month, day)

}

case class YearOnly(year: Int) extends TimePoint

case class YearMonth(year: Int, month: Int) extends TimePoint

case class YearMonthDay(year: Int, month: Int, day: Int) extends TimePoint

case class Viewport(start: LocalDate, end: LocalDate)

object Viewport {

  def getViewportForTimelines(tls: List[Timeline]): Viewport = {
    val start = tls
      .map(x => Period.minTimePointOfPeriod(x.period))
      .map(TimePoint.timePointFloorDate)
      .min
    val end = tls
      .flatMap(x => Period.maxTimePointOfPeriod(x.period))
      .map(TimePoint.timePointCeilDate)
      .max
    Viewport(start, end)
  }
  def isTimelineInViewport(vp: Viewport, tl: Timeline): Boolean = {
    val timelineStart = TimePoint.timePointFloorDate(Period.minTimePointOfPeriod(tl.period))
    val timelineEnd = Period
      .maxTimePointOfPeriod(tl.period)
      .map(TimePoint.timePointCeilDate)
      .getOrElse(vp.end) // For Started periods, use viewport end

    // Timeline overlaps with viewport if:
    // timeline end is on or after viewport start AND timeline start is on or before viewport end
    !timelineEnd.isBefore(vp.start) && !timelineStart.isAfter(vp.end)
  }
}

case class Timeline(id: String, name: String, period: Period)

object Timeline {
  val examples = List(
    Timeline("001", "Jan's life", Started(YearMonthDay(1980, 6, 6))),
    Timeline("002", "Met Alex", Point(YearMonthDay(2024, 7, 8))),
    Timeline("003", "Uni time", Closed(YearMonth(1999, 9), YearMonth(2005, 6))),
    Timeline("004", "Tory's rule", Closed(YearOnly(2010), YearOnly(2024))),
    Timeline(
      "006",
      "Edward III life",
      Closed(YearMonthDay(1312, 11, 13), YearMonthDay(1377, 6, 20))
    )
  )

  val viewport = Viewport.getViewportForTimelines(examples)

  def main(args: Array[String]): Unit = {
    examples.foreach(tl => println(show"${tl.period}"))
    val yo = YearOnly(2010)
    println(TimePoint.timePointFloorDate(yo))
    println(TimePoint.timePointCeilDate(yo))
    val ym = YearMonth(2005, 12)
    println(TimePoint.timePointFloorDate(ym))
    println(TimePoint.timePointCeilDate(ym))
    val ymd = YearMonthDay(1980, 8, 6)
    println(TimePoint.timePointFloorDate(ymd))
    println(TimePoint.timePointCeilDate(ymd))
    val mins = examples.map(x => Period.minTimePointOfPeriod(x.period))
    println(mins.show)
    val floors = mins.map(TimePoint.timePointFloorDate)
    println(floors)
    println(floors.min)
    println(
      examples
        .flatMap(x => Period.maxTimePointOfPeriod(x.period))
        .map(TimePoint.timePointCeilDate)
        .max
    )

    val vp = Viewport.getViewportForTimelines(examples)
    println(vp)
    val bars = examples.map(
      TimeLineBar.timelineToTimelineBar(vp, 500, _)
    )
    println(bars)
  }
}

case class TimeLineBar(start: Option[Float], length: Float, timeline: Timeline)

object TimeLineBar {
  def timelineToTimelineBar(viewport: Viewport, width: Float, tl: Timeline): TimeLineBar = {
    val viewportStart = viewport.start
    val viewportEnd   = viewport.end
    val (dateStart, dateEnd) = (
      TimePoint.timePointFloorDate(Period.minTimePointOfPeriod(tl.period)),
      (Period
        .maxTimePointOfPeriod(tl.period)
        .map(TimePoint.timePointCeilDate)
        .getOrElse(viewport.end))
    )
    println(s"dateStart: $dateStart, dateEnd: $dateEnd")
    val viewportDays = ChronoUnit.DAYS.between(viewportStart, viewportEnd)
    val scale        = width / viewportDays
    TimeLineBar(
      start =
        if viewportStart.isAfter(dateStart) then None
        else (ChronoUnit.DAYS.between(viewportStart, dateStart) * scale).some,
      length = ChronoUnit.DAYS.between(List(viewportStart, dateStart).max, dateEnd) * scale,
      timeline = tl
    )
  }
//  timelineToTimelineBar {
//    start
//    , end
//  } width ({
//    period
//    , name
//  } as tl) =
//    let
//    viewportStart =
//      Timeline.API.timePointToStartDate start
//
//    viewportEnd =
//      Timeline.API.timePointToEndDate end
//
//    (dateStart, dateEnd) =
//      case period of
//        Point tp ->
//        (Timeline.API.timePointToStartDate tp, Timeline.API.timePointToEndDate tp)
//
//        Closed y1 y2 ->
//        (Timeline.API.timePointToStartDate y1, Date.min(Timeline.API.timePointToEndDate y2) viewportEnd)
//
//        Started tp ->
//        (Timeline.API.timePointToStartDate tp, viewportEnd)
//
//        Finished tp ->
//        (viewportStart, Date.min(Timeline.API.timePointToEndDate tp) viewportEnd)
//
//        viewPortDays =
//      Date.diff Date
//    .Days viewportStart viewportEnd
//
//    scale =
//      width
//        / toFloat viewPortDays
//        in
//    {
//      start =
//        if Date.compare viewportStart dateStart == GT || isPeriodFinished period then
//          Nothing
//
//        else
//          Just(toFloat(Date.diff Date.Days viewportStart dateStart) * scale
//      )
//      , length = toFloat(Date.diff Date.Days(Date.max viewportStart dateStart) dateEnd
//      ) * scale
//      , timeline = tl
//    }
}
