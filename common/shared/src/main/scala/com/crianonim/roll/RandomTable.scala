package com.crianonim.roll

import cats.Id

trait RollValue
case class RollValueOne(value: Int)           extends RollValue
case class RollValueRange(min: Int, max: Int) extends RollValue

case class RollRow[A](value: RollValue, result: A)

case class RandomTable[A](rollValues: List[RollRow[A]])

case class RandomTableReady[A, F[_]](table: RandomTable[A], roll: () => F[Int])

object RandomTable {
  def flatten[A](table: RandomTable[A]): List[(Int, A)] = {
    table.rollValues.flatMap {
      case RollRow(RollValueOne(value), result)      => List((value, result))
      case RollRow(RollValueRange(min, max), result) => (min to max).map(i => (i, result))
    }
  }
  def findResult[A](table: RandomTable[A], roll: Int): A = {
    flatten(table)
      .find(_._1 == roll)
      .map(_._2)
      .getOrElse(throw new IllegalArgumentException(s"No result found for roll $roll"))
  }
  def isValidInt(roll: Int): Boolean = {
    roll >= 1 && roll <= 666 && roll % 10 <= 6 && roll % 10 >= 1
  }
  def isTableValid[A](table: RandomTable[A]): Boolean = {
    flatten(table).forall(x => isValidInt(x._1))
  }
}

object TestRandomTable extends App {
  import com.crianonim.roll._

  val roll = Roll.forUnsafe[Id]

  val d6: D6[[A] =>> A] = D6(roll)
  val kinTable = RandomTable(
    List(
      RollRow(RollValueRange(11, 22), "Alderlander Human"),
      RollRow(RollValueRange(23, 31), "Aslene Human"),
      RollRow(RollValueRange(32, 34), "Ailander Human"),
      RollRow(RollValueRange(35, 41), "Half-Elf"),
      RollRow(RollValueRange(42, 44), "Halfling"),
      RollRow(RollValueRange(45, 52), "Goblin"),
      RollRow(RollValueRange(53, 56), "Orc"),
      RollRow(RollValueRange(61, 62), "Wolfkin"),
      RollRow(RollValueRange(63, 64), "Dwarf"),
      RollRow(RollValueRange(65, 66), "Elf")
    )
  )
  // The shores of Lake Varda
  val kin = RandomTable.findResult(kinTable, d6.d66)
  println(kin)
  val homeRegion = kin match {
    case "Alderlander Human" =>
      RandomTable.findResult(
        RandomTable(
          List(
            RollRow(RollValueOne(1), "The edge of Arina Forest"),
            RollRow(RollValueRange(2, 3), "The plains of Moldena"),
            RollRow(RollValueOne(4), "The shores of Lake Varda"),
            RollRow(RollValueOne(5), "The fields of Margelda"),
            RollRow(RollValueOne(6), "The Harga Wastes")
          )
        ),
        d6.d6
      )
    case "Aslene Human" =>
      RandomTable.findResult(
        RandomTable(
          List(
            RollRow(RollValueOne(1), "The edge of Arina Forest"),
            RollRow(RollValueRange(2, 3), "The plains of Moldena"),
            RollRow(RollValueOne(4), "The shores of Lake Varda"),
            RollRow(RollValueOne(5), "The fields of Margelda"),
            RollRow(RollValueOne(6), "The Harga Wastes")
          )
        ),
        d6.d6
      )
    case "Ailander Human" =>
      RandomTable.findResult(
        RandomTable(
          List(
            RollRow(RollValueOne(1), "The edge of Arina Forest"),
            RollRow(RollValueRange(2, 3), "The plains of Moldena"),
            RollRow(RollValueOne(4), "The shores of Lake Varda"),
            RollRow(RollValueOne(5), "The fields of Margelda"),
            RollRow(RollValueOne(6), "The Harga Wastes")
          )
        ),
        d6.d6
      )
    case "Half-Elf" =>
      RandomTable.findResult(
        RandomTable(
          List(
            RollRow(RollValueOne(1), "The edge of Arina Forest"),
            RollRow(RollValueRange(2, 3), "The plains of Moldena"),
            RollRow(RollValueOne(4), "The shores of Lake Varda"),
            RollRow(RollValueOne(5), "The fields of Margelda"),
            RollRow(RollValueOne(6), "The Harga Wastes")
          )
        ),
        d6.d6
      )
    case "Halfling" =>
      RandomTable.findResult(
        RandomTable(
          List(
            RollRow(RollValueOne(1), "The edge of Arina Forest"),
            RollRow(RollValueRange(2, 3), "The plains of Moldena"),
            RollRow(RollValueOne(4), "The shores of Lake Varda"),
            RollRow(RollValueOne(5), "The fields of Margelda"),
            RollRow(RollValueOne(6), "The Harga Wastes")
          )
        ),
        d6.d6
      )
    case "Goblin" =>
      RandomTable.findResult(
        RandomTable(
          List(
            RollRow(RollValueOne(1), "The edge of Arina Forest"),
            RollRow(RollValueRange(2, 3), "The plains of Moldena"),
            RollRow(RollValueOne(4), "The shores of Lake Varda"),
            RollRow(RollValueOne(5), "The fields of Margelda"),
            RollRow(RollValueOne(6), "The Harga Wastes")
          )
        ),
        d6.d6
      )
    case "Orc" =>
      RandomTable.findResult(
        RandomTable(
          List(
            RollRow(RollValueOne(1), "The edge of Arina Forest"),
            RollRow(RollValueRange(2, 3), "The plains of Moldena"),
            RollRow(RollValueOne(4), "The shores of Lake Varda"),
            RollRow(RollValueOne(5), "The fields of Margelda"),
            RollRow(RollValueOne(6), "The Harga Wastes")
          )
        ),
        d6.d6
      )
    case "Wolfkin" =>
      RandomTable.findResult(
        RandomTable(
          List(
            RollRow(RollValueOne(1), "The edge of Arina Forest"),
            RollRow(RollValueRange(2, 3), "The plains of Moldena"),
            RollRow(RollValueOne(4), "The shores of Lake Varda"),
            RollRow(RollValueOne(5), "The fields of Margelda"),
            RollRow(RollValueOne(6), "The Harga Wastes")
          )
        ),
        d6.d6
      )
    case "Dwarf" =>
      RandomTable.findResult(
        RandomTable(
          List(
            RollRow(RollValueOne(1), "The edge of Arina Forest"),
            RollRow(RollValueRange(2, 3), "The plains of Moldena"),
            RollRow(RollValueOne(4), "The shores of Lake Varda"),
            RollRow(RollValueOne(5), "The fields of Margelda"),
            RollRow(RollValueOne(6), "The Harga Wastes")
          )
        ),
        d6.d6
      )
    case "Elf" =>
      RandomTable.findResult(
        RandomTable(
          List(
            RollRow(RollValueOne(1), "The edge of Arina Forest"),
            RollRow(RollValueRange(2, 3), "The plains of Moldena"),
            RollRow(RollValueOne(4), "The shores of Lake Varda"),
            RollRow(RollValueOne(5), "The fields of Margelda"),
            RollRow(RollValueOne(6), "The Harga Wastes")
          )
        ),
        d6.d6
      )
  }
  println(homeRegion)
  val profession = RandomTable.findResult(
    RandomTable(
      List(
        RollRow(RollValueRange(11, 14), "Druid"),
        RollRow(RollValueRange(15, 23), "Fighter"),
        RollRow(RollValueRange(24, 33), "Hunter"),
        RollRow(RollValueRange(34, 42), "Minstrel"),
        RollRow(RollValueRange(43, 46), "Peddler"),
        RollRow(RollValueRange(51, 54), "Rider"),
        RollRow(RollValueRange(55, 62), "Rogue"),
        RollRow(RollValueRange(63, 66), "Sorcerer")
      )
    ),
    d6.d66
  )
  println(profession)

  val xxx           = d6.d66 _
  val kinTableReady = RandomTableReady(kinTable, d6.d66 _)
  println(kinTableReady)

}
