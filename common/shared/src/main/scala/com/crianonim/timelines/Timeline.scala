package com.crianonim.timelines
import cats.Show
import cats.implicits.showInterpolator
import cats.syntax.all.*
import com.crianonim.timelines

import java.time.LocalDate



sealed trait Period

object Period {
  given Show[Period] = new Show[Period]:
    override def show(p: Period): String = p match
      case Point(point) => point.show
      case Closed(start, end) => show"${start} >> ${end}"
      case Started(start) => show"${start} >> ..."


  def minTimePointOfPeriod(p:Period): TimePoint = p match
    case Point(point) => point
    case Closed(start, end) => start
    case Started(start) => start

  def maxTimePointOfPeriod(p: Period): Option[TimePoint] = p match
    case Point(point) => point.some
    case Closed(start, end) => end.some
    case Started(start) => None

//  def startOfListOfPeriods(ps: List[Period]): TimePoint =

}

case class Point (point: TimePoint) extends  Period

case class Closed(start: TimePoint, end: TimePoint) extends  Period

case class Started(start: TimePoint) extends Period

sealed trait TimePoint

object TimePoint {
  given Show[TimePoint] = new Show[TimePoint]:
    override def show(t: TimePoint): String = t match
      case YearOnly(year) => year.toString
      case YearMonth(year, month) => year.toString ++ "-"++ month.toString.padTo(2,'0').reverse
      case YearMonthDay(year, month, day) => year.toString ++ "-" ++ month.toString.padTo(2,'0').reverse ++ "-" ++ day.toString.padTo(2,'0').reverse

  def timePointFloorDate(t:TimePoint): LocalDate =
    t match
      case YearOnly(year) => LocalDate.of(year,1,1)
      case YearMonth(year, month) => LocalDate.of(year,month,1)
      case YearMonthDay(year, month, day) => LocalDate.of(year,month,day)

  def  timePointCeilDate(t:TimePoint): LocalDate =
    t match
      case YearOnly(year) => LocalDate.of(year,12,31)
      case YearMonth(year, month) => LocalDate.of(year,month,1).plusMonths(1).minusDays(1)
      case YearMonthDay(year, month, day) => LocalDate.of(year,month,day)


}

case class YearOnly(year: Int) extends TimePoint

case class YearMonth(year: Int, month: Int) extends TimePoint

case class YearMonthDay(year: Int, month: Int, day: Int) extends TimePoint


case class Timeline(id: String, name: String, period: Period)

object Timeline {
  val examples = List(
    Timeline("001", "Jan's life", Started(YearMonthDay(1980,8,6))),
    Timeline("002", "Met Alex", Point(YearMonthDay(2024,7,8))),
    Timeline("003", "Uni time", Closed(YearMonth(1999,9),YearMonth(2005,6))),
    Timeline("004", "Tory's rule", Closed(YearOnly(2010),YearOnly(2024)))
  )
  def main(args: Array[String]): Unit = {
    examples.foreach(
      tl=> println(show"${tl.period}")
    )
    val yo=YearOnly(2010)
    println(TimePoint.timePointFloorDate(yo))
    println(TimePoint.timePointCeilDate(yo))
    val ym= YearMonth(2005,12)
    println(TimePoint.timePointFloorDate(ym))
    println(TimePoint.timePointCeilDate(ym))
    val ymd = YearMonthDay(1980,8,6)
    println(TimePoint.timePointFloorDate(ymd))
    println(TimePoint.timePointCeilDate(ymd))
    val mins = examples.map(x=>Period.minTimePointOfPeriod(x.period))
    println(mins.show)
    val floors = mins.map(TimePoint.timePointFloorDate)
    println(floors)
    println(floors.min)
    println(examples.flatMap(
      x=>Period.maxTimePointOfPeriod(x.period))
      .map(TimePoint.timePointCeilDate)
      .max

    )
  }
}


