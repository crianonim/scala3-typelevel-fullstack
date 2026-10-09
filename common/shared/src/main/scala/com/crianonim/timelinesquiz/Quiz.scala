package com.crianonim.timelinesquiz

import scala.collection.mutable
import scala.util.Random

/** A single entry of a quiz timeline: a name and the inclusive year range it covers. */
case class QuizEntry(name: String, start: Int, end: Int)

/** A named set of entries. `label` mirrors the source file name (without `.csv`). */
case class QuizTimeline(id: String, label: String, entries: List[QuizEntry]):
  def minYear: Int = entries.map(_.start).min
  def maxYear: Int = entries.map(_.end).max

/** One question: a year, every entry that covers it, and the shuffled answer options. */
case class Question(year: Int, correct: List[QuizEntry], options: List[QuizEntry])

/** Pure quiz mechanics, a port of the original `lib/timeline.ts` and `lib/quiz.ts`. */
object Quiz:

  val MaxYearAttempts = 1000
  val CorrectSide     = 4
  val DistractorCount = 4

  private val yearPattern = "-?\\d+".r

  private val byStart: Ordering[QuizEntry] =
    Ordering.by(e => (e.start, e.end, e.name))

  def entryKey(entry: QuizEntry): String = s"${entry.name}|${entry.start}|${entry.end}"

  /** Stable identity of a question option, used to track selections in the model. */
  def matchesForYear(entries: List[QuizEntry], year: Int): List[QuizEntry] =
    entries.filter(e => e.start <= year && year <= e.end).sorted(byStart)

  def questionForYear(
      entries: List[QuizEntry],
      year: Int,
      random: Random = new Random
  ): Option[Question] =
    if entries.isEmpty then None
    else
      val correct = matchesForYear(entries, year)
      if correct.isEmpty then None
      else
        val sorted      = entries.sorted(byStart)
        val indices     = correct.map(sorted.indexOf)
        val from        = math.max(0, indices.min - CorrectSide)
        val to          = math.min(sorted.length - 1, indices.max + CorrectSide)
        val pool        = sorted.slice(from, to + 1).filterNot(correct.contains)
        val distractors = shuffle(pool, random).take(DistractorCount)
        Some(Question(year, correct, shuffle(correct ++ distractors, random)))

  def pickQuestion(entries: List[QuizEntry], random: Random = new Random): Option[Question] =
    if entries.isEmpty then None
    else
      val sorted  = entries.sorted(byStart)
      val minYear = sorted.map(_.start).min
      val maxYear = sorted.map(_.end).max
      Iterator
        .range(0, MaxYearAttempts)
        .map(_ => randInt(minYear, maxYear, random))
        .flatMap(year => questionForYear(entries, year, random))
        .nextOption()

  def isExactMatch(correct: List[QuizEntry], chosen: Set[String]): Boolean =
    val correctKeys = correct.map(entryKey).toSet
    chosen.size == correctKeys.size && correctKeys.subsetOf(chosen)

  private def randInt(min: Int, max: Int, random: Random): Int =
    if max <= min then min else min + random.nextInt(max - min + 1)

  private def shuffle[A: scala.reflect.ClassTag](items: List[A], random: Random): List[A] =
    val arr = items.toArray
    var i   = arr.length - 1
    while i > 0 do
      val j = random.nextInt(i + 1)
      val t = arr(i)
      arr(i) = arr(j)
      arr(j) = t
      i -= 1
    arr.toList

  /** Parse a `name,start,end` CSV, skipping blank and malformed rows. */
  def parseCsv(raw: String): List[QuizEntry] =
    val rows = parseCsvRows(raw).filter(_.exists(_.trim.nonEmpty))
    if rows.isEmpty then Nil
    else
      val header   = rows.head.map(_.trim.toLowerCase)
      val nameIdx  = header.indexOf("name")
      val startIdx = header.indexOf("start")
      val endIdx   = header.indexOf("end")
      if nameIdx == -1 || startIdx == -1 || endIdx == -1 then
        throw new IllegalArgumentException(
          "CSV header must contain \"name\", \"start\" and \"end\" columns"
        )
      rows.tail.flatMap { row =>
        val name  = row.lift(nameIdx).getOrElse("").trim
        val start = parseYear(row.lift(startIdx).getOrElse(""))
        val end   = parseYear(row.lift(endIdx).getOrElse(""))
        (name, start, end) match
          case (n, Some(s), Some(e)) if n.nonEmpty && s <= e => Some(QuizEntry(n, s, e))
          case _                                             => None
      }

  private def parseYear(value: String): Option[Int] =
    val trimmed = value.trim
    if yearPattern.matches(trimmed) then trimmed.toIntOption else None

  private def parseCsvRows(contents: String): List[List[String]] =
    val rows   = mutable.ListBuffer.empty[List[String]]
    val row    = mutable.ListBuffer.empty[String]
    val field  = new StringBuilder
    var quoted = false
    var i      = 0
    while i < contents.length do
      val ch = contents.charAt(i)
      if quoted then
        if ch == '"' then
          if i + 1 < contents.length && contents.charAt(i + 1) == '"' then
            field += '"'
            i += 1
          else quoted = false
        else field += ch
      else if ch == '"' then quoted = true
      else if ch == ',' then
        row += field.toString
        field.clear()
      else if ch == '\n' || ch == '\r' then
        if ch == '\r' && i + 1 < contents.length && contents.charAt(i + 1) == '\n' then i += 1
        row += field.toString
        field.clear()
        rows += row.toList
        row.clear()
      else field += ch
      i += 1
    if field.nonEmpty || row.nonEmpty then
      row += field.toString
      rows += row.toList
    rows.toList
