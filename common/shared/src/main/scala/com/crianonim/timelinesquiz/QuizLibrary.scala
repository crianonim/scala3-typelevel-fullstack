package com.crianonim.timelinesquiz

/** Embedded copy of the original app's `data/uk_monarchs.csv` (also kept at `db/uk_monarchs.csv`).
  */
object MonarchsCsv:
  val raw: String =
    """name,start,end
William I,1066,1087
William II,1087,1100
Henry I,1100,1135
Stephen,1135,1154
Henry II,1154,1189
Richard I,1189,1199
John,1199,1216
Henry III,1216,1272
Edward I,1272,1307
Edward II,1307,1327
Edward III,1327,1377
Richard II,1377,1399
Henry IV,1399,1413
Henry V,1413,1422
Henry VI,1422,1461
Edward IV,1461,1483
Edward V,1483,1483
Richard III,1483,1485
Henry VII,1485,1509
Henry VIII,1509,1547
Edward VI,1547,1553
Mary I,1553,1558
Elizabeth I,1558,1603
James I,1603,1625
Charles I,1625,1649
Charles II,1660,1685
James II,1685,1688
William III & Mary II,1689,1702
Anne,1702,1714
George I,1714,1727
George II,1727,1760
George III,1760,1820
George IV,1820,1830
William IV,1830,1837
Victoria,1837,1901
Edward VII,1901,1910
George V,1910,1936
Edward VIII,1936,1936
George VI,1936,1952
Elizabeth II,1952,2022
"""

/** The timelines available to the quiz tab. Mirrors `getTimelines()` from the original app. */
object QuizLibrary:
  val timelines: List[QuizTimeline] =
    List(QuizTimeline("uk_monarchs", "uk_monarchs", Quiz.parseCsv(MonarchsCsv.raw)))
