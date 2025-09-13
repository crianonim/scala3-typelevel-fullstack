package com.crianonim.forbiddenlands
import com.crianonim.roll.D6
import com.crianonim.roll.RandomTable
import com.crianonim.roll.RollRow
import com.crianonim.roll.RollValueRange
import cats.Show
import cats.Id
import com.crianonim.roll.Roll
import cats.syntax.show._
case class Attributes(strength: Int, agility: Int, wits: Int, empathy: Int)
enum Proffesion {
  case Druid, Fighter, Hunter, Minstrel, Peddler, Rider, Rogue, Sorcerer
}
object Proffesion {
  val all = List(Druid, Fighter, Hunter, Minstrel, Peddler, Rider, Rogue, Sorcerer)
  val table =
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
    )
}
enum Kin {
  case AlderlanderHuman, AsleneHuman, AilanderHuman, HalfElf, Halfling, Goblin, Orc, Wolfkin
}
object Kin {
  val all =
    List(AlderlanderHuman, AsleneHuman, AilanderHuman, HalfElf, Halfling, Goblin, Orc, Wolfkin)
  val table =
    RandomTable(
      List(
        RollRow(RollValueRange(11, 14), AlderlanderHuman),
        RollRow(RollValueRange(15, 23), AsleneHuman),
        RollRow(RollValueRange(24, 33), AilanderHuman),
        RollRow(RollValueRange(34, 42), HalfElf),
        RollRow(RollValueRange(43, 46), Halfling),
        RollRow(RollValueRange(51, 54), Goblin),
        RollRow(RollValueRange(55, 62), Orc),
        RollRow(RollValueRange(63, 66), Wolfkin)
      )
    )
  given Show[Kin] = Show.show(kin =>
    kin match {
      case AlderlanderHuman => "Alderlander Human"
      case AsleneHuman      => "Aslene Human"
      case AilanderHuman    => "Ailander Human"
      case HalfElf          => "Half-Elf"
      case Halfling         => "Halfling"
      case Goblin           => "Goblin"
      case Orc              => "Orc"
      case Wolfkin          => "Wolfkin"
    }
  )
}

case class Character(
    name: String,
    kin: Kin,
    profession: Proffesion,
    attributes: Attributes,
    homeRegion: String
)

object CharacterTest extends App {
  val roll       = Roll.forUnsafe[Id]
  val d6         = D6(roll)
  val profession = RandomTable.findResult(Proffesion.table, d6.d66)
  val kin        = RandomTable.findResult(Kin.table, d6.d66)
  println(profession.show)
  println(kin.show)
}
