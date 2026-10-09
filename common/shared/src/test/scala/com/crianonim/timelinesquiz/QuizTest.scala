package com.crianonim.timelinesquiz

import munit.FunSuite

import scala.util.Random

class QuizTest extends FunSuite:

  private val monarchs = QuizLibrary.timelines.head

  test("embedded monarch CSV parses to 40 entries"):
    assertEquals(monarchs.entries.length, 40)
    assertEquals(monarchs.minYear, 1066)
    assertEquals(monarchs.maxYear, 2022)

  test("CSV parsing handles a reordered header and quoted fields"):
    val csv = "start,end,name\n1625,1649,Charles I\n1603,1625,\"James, I\"\n"
    assertEquals(
      Quiz.parseCsv(csv),
      List(QuizEntry("Charles I", 1625, 1649), QuizEntry("James, I", 1603, 1625))
    )

  test("CSV parsing skips blank and malformed rows"):
    val csv =
      "name,start,end\n\nGood,1000,1100\nMissing end,1000,\nNot a year,abc,2000\nBackwards,2000,1000\n"
    assertEquals(Quiz.parseCsv(csv), List(QuizEntry("Good", 1000, 1100)))

  test("matchesForYear is inclusive on both boundaries"):
    val williams = Quiz.matchesForYear(monarchs.entries, 1087).map(_.name)
    assertEquals(williams, List("William I", "William II"))
    assertEquals(Quiz.matchesForYear(monarchs.entries, 1066).map(_.name), List("William I"))
    assertEquals(Quiz.matchesForYear(monarchs.entries, 1650), Nil)

  test("questionForYear returns None on a gap and both correct entries on an overlap"):
    assertEquals(Quiz.questionForYear(monarchs.entries, 1650, new Random(1)), None)
    val question = Quiz.questionForYear(monarchs.entries, 1087, new Random(1)).get
    assertEquals(question.year, 1087)
    assertEquals(question.correct.map(_.name), List("William I", "William II"))
    assert(question.correct.forall(question.options.contains))
    assert(question.options.length <= Quiz.CorrectSide * 2 + 1)

  test("pickQuestion never lands on a gap and always includes every correct option"):
    val random = new Random(42)
    (1 to 2000).foreach { _ =>
      val question = Quiz.pickQuestion(monarchs.entries, random).get
      assert(Quiz.matchesForYear(monarchs.entries, question.year).nonEmpty)
      assertEquals(question.correct, Quiz.matchesForYear(monarchs.entries, question.year))
      assert(question.correct.forall(question.options.contains))
    }

  test("isExactMatch requires the full set and no extras"):
    val correct = Quiz.matchesForYear(monarchs.entries, 1087)
    assert(Quiz.isExactMatch(correct, correct.map(Quiz.entryKey).toSet))
    assert(!Quiz.isExactMatch(correct, Set(Quiz.entryKey(correct.head))))
    assert(!Quiz.isExactMatch(correct, correct.map(Quiz.entryKey).toSet + "extra|1|2"))
