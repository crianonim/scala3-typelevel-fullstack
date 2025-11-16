#!/usr/bin/env -S scala-cli shebang

//> using scala 3.6.4
//> using dep io.circe::circe-core:0.14.0
//> using dep io.circe::circe-generic:0.14.0
//> using dep io.circe::circe-parser:0.14.0
//> using dep com.lihaoyi::os-lib:0.11.3

import io.circe.*
import io.circe.generic.semiauto.*
import io.circe.parser.*
import os.*

// Domain models (copied from Timeline.scala)
enum TimePoint:
  case YearOnly(year: Int)
  case YearMonth(year: Int, month: Int)
  case YearMonthDay(year: Int, month: Int, day: Int)

object TimePoint:
  given Encoder[TimePoint] = deriveEncoder
  given Decoder[TimePoint] = deriveDecoder

enum Period:
  case Point(point: TimePoint)
  case Closed(start: TimePoint, end: TimePoint)
  case Started(start: TimePoint)

object Period:
  given Encoder[Period] = deriveEncoder
  given Decoder[Period] = deriveDecoder

case class Timeline(id: String, name: String, period: Period)

object Timeline:
  given Encoder[Timeline] = deriveEncoder
  given Decoder[Timeline] = deriveDecoder

// Script logic
@main def generateTimelinesFromJSON(): Unit =
  val scriptDir = os.pwd / "scripts"
  val jsonFile = os.pwd / "db" / "timelines.json"
  val targetFile = os.pwd / "common" / "shared" / "src" / "main" / "scala" / "com" / "crianonim" / "timelines" / "TimelinesFromJSON.scala"

  println(s"Reading JSON from: $jsonFile")
  val jsonContent = os.read(jsonFile)

  println("Parsing JSON...")
  val timelines = decode[List[Timeline]](jsonContent) match
    case Right(tl) => tl
    case Left(error) =>
      println(s"ERROR: Failed to decode JSON: $error")
      sys.exit(1)

  println(s"Successfully parsed ${timelines.size} timelines")

  println("Generating Scala code...")
  val scalaCode = generateScalaCode(timelines)

  println(s"Writing to: $targetFile")
  os.write.over(targetFile, scalaCode)

  println("✓ Successfully generated TimelinesFromJSON.scala")

def generateScalaCode(timelines: List[Timeline]): String =
  val timelinesCode = timelines.map(generateTimelineCode).mkString(",\n    ")

  s"""package com.crianonim.timelines
     |
     |
     |object TimelinesFromJSON {
     |  def apply: List[Timeline] = List(
     |    $timelinesCode
     |  )
     |}
     |""".stripMargin

def generateTimelineCode(timeline: Timeline): String =
  val periodCode = generatePeriodCode(timeline.period)
  s"""Timeline("${timeline.id}", "${timeline.name}", $periodCode)"""

def generatePeriodCode(period: Period): String = period match
  case Period.Point(point) =>
    s"Point(${generateTimePointCode(point)})"
  case Period.Closed(start, end) =>
    s"Closed(${generateTimePointCode(start)}, ${generateTimePointCode(end)})"
  case Period.Started(start) =>
    s"Started(${generateTimePointCode(start)})"

def generateTimePointCode(timePoint: TimePoint): String = timePoint match
  case TimePoint.YearOnly(year) =>
    s"YearOnly($year)"
  case TimePoint.YearMonth(year, month) =>
    s"YearMonth($year, $month)"
  case TimePoint.YearMonthDay(year, month, day) =>
    s"YearMonthDay($year, $month, $day)"
