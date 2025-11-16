package com.crianonim.timelines


object TimelinesFromJSON {
  def apply: List[Timeline] = List(
    Timeline("001", "Jan's life", Started(YearMonthDay(1980, 6, 6))),
    Timeline("002", "Met Alex", Point(YearMonthDay(2023, 8, 7))),
    Timeline("003", "Uni time", Closed(YearMonth(1999, 10), YearMonth(2005, 6))),
    Timeline("004", "Tory's rule", Closed(YearOnly(2010), YearOnly(2024))),
    Timeline("006", "Edward III life", Closed(YearMonthDay(1312, 11, 13), YearMonthDay(1377, 6, 20)))
  )
}
