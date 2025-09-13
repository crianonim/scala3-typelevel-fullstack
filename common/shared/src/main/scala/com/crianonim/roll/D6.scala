package com.crianonim.roll

import cats.implicits._
import cats.Applicative
import cats.Id
class D6[F[_]: Applicative](roll: Roll[F]) {
  def d6: F[Int]  = roll.rollDie(6)
  def d8: F[Int]  = roll.rollDie(8)
  def d66: F[Int] = roll.parseAndRoll("2d6").map(x => x.rolls.head * 10 + x.rolls.last)
  def d666: F[Int] =
    roll.parseAndRoll("3d6").map(x => x.rolls.head * 100 + x.rolls.tail.head * 10 + x.rolls.last)
}

object D6 {}

object TestD6Unsafe extends App {
  private val impl = new D6[Id](Roll.forUnsafe[Id])
  //  val rollDefStats = rollDefStat(parseRollDef("2d5+4"))
  impl.d6.flatMap(println)
  impl.d66.flatMap(println)
  impl.d666.flatMap(println)

}
