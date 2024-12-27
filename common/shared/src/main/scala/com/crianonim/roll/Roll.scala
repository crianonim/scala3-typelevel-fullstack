package com.crianonim.roll

import cats.effect.std.Random
import cats.effect.{IO, IOApp}
import cats.implicits._
import cats._
import com.crianonim.roll.Roll.parsedRollDef
import fastparse.NoWhitespace._
import fastparse._
import io.circe.{Decoder, Encoder, Json}

import scala.util.Random.between


trait Roll[F[_]] {
  def roll(rollDef: RollDef): F[RollResult]


  def eval(rollResult: RollResult): Int = rollResult.rolls.sum + rollResult.mod

  def parseAndEval(s: String): F[Int]

  def rollDie(diceFaces: Int): F[Int]
}

case class DiceRoll(diceCount: Int, diceFaces: Int)

object DiceRoll {
  implicit val showImpl: Show[DiceRoll] = (t: DiceRoll) => s"""${if (t.diceCount == 0) "" else t.diceCount.toString}d${t.diceFaces.toString}"""
}


case class RollDef(dice: DiceRoll, mod: Option[Int])

object RollDef {
  implicit val decoder: Decoder[RollDef] = Decoder.decodeString.emap(i => parsedRollDef(i).fold((_, _, _) => Left("Failed"), (r, _) => Right(r)))
  implicit val encoder: Encoder[RollDef] = new Encoder[RollDef] {
    final def apply(a: RollDef): Json = Json.fromString(showImpl.show(a))
  }
  implicit val showImpl: Show[RollDef] = (t: RollDef) => s"""${t.dice.show}${t.mod.map(mod => if (mod > 0) "+" + mod.toString else mod.toString).getOrElse("")}"""
}

case class RollResult(rolls: Seq[Int], mod: Int)

object Roll {
  
  
  def forCats[F[_] : Applicative : Random]: Roll[F] = new Roll[F] {
    override def parseAndEval(s: String): F[Int] = roll(parseRollDef(s)).map(eval)


    override def rollDie(diceFaces: Int): F[Int] = Random[F].betweenInt(1, diceFaces)

    override def roll(rollDef: RollDef): F[RollResult] = (List.fill(rollDef.dice.diceCount)(rollDef.dice.diceFaces).traverse(rollDie),
      rollDef.mod.getOrElse(0).pure[F])
      .mapN((l, v) => RollResult(l, v))

  }

  private def parseRollDef(s: String): RollDef = {

    val Parsed.Success(value, _) = parse(s, x=> rollDef(using x)) :  @unchecked
    value
  }

  private def rollDef[$: P]: P[RollDef] = P(dice ~ modifier.? ~ End).map {
    case (roll, mod) => RollDef(roll, mod)
  }

  private def modifier[$: P]: P[Int] = (CharIn("+\\-").! ~ number).map {
    case ("+", n) => n
    case ("-", n) => -n
  }

  private def dice[$: P]: P[DiceRoll] = P(number.? ~ "d" ~ number).map {
    case (count, faces) => DiceRoll(count.getOrElse(1), faces)
  }

  private def number[$: P]: P[Int] = P(CharIn("0-9").rep(1).!.map(_.toInt))

  def parsedRollDef(s: String): Parsed[RollDef] = parse(s, rollDef(using _)) :  @unchecked

  def forUnsafe[F[_] : Applicative]: Roll[F] = new Roll[F] {
    override def parseAndEval(s: String): F[Int] =
      roll(parseRollDef(s)).map(eval)


    override def rollDie(diceFaces: Int): F[Int] = between(1, diceFaces).pure[F]

    override def roll(rollDef: RollDef): F[RollResult] = (List.fill(rollDef.dice.diceCount)(rollDef.dice.diceFaces).traverse(rollDie),
      rollDef.mod.getOrElse(0).pure[F])
      .mapN((l, v) => RollResult(l, v))

  }

  def rollDefStat(rollDef: RollDef): (Int, Int, Int) = {
    val mod: Int = rollDef.mod.getOrElse(0)
    val minValue = rollDef.dice.diceCount + mod
    val maxValue = rollDef.dice.diceCount * rollDef.dice.diceFaces + mod
    val avgValue = rollDef.dice.diceCount * Math.round((rollDef.dice.diceFaces + 1) / 2.0).toInt + mod
    (minValue, maxValue, avgValue)
  }

}

object TestRoll extends IOApp.Simple {

  override def run: IO[Unit] = Random.scalaUtilRandom[IO].flatMap(implicit rand => {
    val impl = Roll.forCats[IO]
    impl.parseAndEval("2d5+4").flatMap(IO.println)
  }

  ).void

}

object TestRollUnsafe extends App {
  private val impl = Roll.forUnsafe[Id]
  //  val rollDefStats = rollDefStat(parseRollDef("2d5+4"))
  impl.parseAndEval("2d5+4").flatMap(println)

}