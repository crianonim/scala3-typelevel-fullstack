package com.crianonim.janscape

import munit.FunSuite

class ClockTest extends FunSuite:

  private val clock = ClockConfig(minutesPerTurn = 10, hoursOffset = 32)

  test("turn 0 opens Day 1 at the offset hour"):
    assertEquals(clock.at(0), DayTime(1, 8, 0))

  test("six turns add an hour, one turn adds minutes"):
    assertEquals(clock.at(6), DayTime(1, 9, 0))
    assertEquals(clock.at(1), DayTime(1, 8, 10))

  test("a full day of turns rolls Day 1 over to Day 2"):
    assertEquals(clock.at(143), DayTime(1, 7, 50))
    assertEquals(clock.at(144), DayTime(2, 8, 0))

  test("223 turns land on Day 2 at 21:10"):
    assertEquals(clock.at(223), DayTime(2, 21, 10))

  test("the clock wraps at midnight mid-day"):
    assertEquals(clock.at(96), DayTime(1, 0, 0))

  test("minutesPerTurn scales the day length"):
    val fast = ClockConfig(minutesPerTurn = 5, hoursOffset = 32)
    assertEquals(fast.at(12), DayTime(1, 9, 0))
    assertEquals(fast.at(288), DayTime(2, 8, 0))

  test("DayTime renders like a wall clock"):
    assertEquals(DayTime(2, 21, 10).toString, "Day 2, 21:10")
    assertEquals(DayTime(1, 8, 0).toString, "Day 1, 8:00")
    assertEquals(DayTime(3, 0, 5).toString, "Day 3, 0:05")