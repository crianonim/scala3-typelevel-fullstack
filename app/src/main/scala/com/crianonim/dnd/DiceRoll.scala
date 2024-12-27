package com.crianonim.dnd

import cats.effect.*
import tyrian.*
import tyrian.Html.*
import cats.effect.std.Random
import com.crianonim.ui.*
import com.crianonim.roll.{Roll,RollDef, RollResult,DiceRoll as DR}

object DiceRoll {
  case class Model(result: RollResult,diceInput: String, facesInput: String )

  enum Msg {
    case ClickRoll
    case GotResult(r:RollResult)
    case UpdateFacesInput(s:String)
    case UpdateDiceInput(s: String)
  }
  def init : Model = Model( RollResult(List.empty,0),"3","6" )

  def rollCmd(model: Model):Cmd[IO,Msg] = {
    val dice = model.diceInput.toInt
    val faces = model.facesInput.toInt
    val d = for {
      given Random[IO] <- Random.scalaUtilRandom[IO]
      roll = Roll.forCats[IO]
      result <- roll.roll(RollDef(DR(dice,faces),None))
    } yield result
    Cmd.Run(d)(Msg.GotResult.apply)
  }
  def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = {

    case Msg.ClickRoll => (model, rollCmd(model))
    case Msg.GotResult(result:RollResult) =>
      (model.copy(result =result ), Cmd.None)
    case Msg.UpdateDiceInput(s:String) =>
      (model.copy(diceInput = s),Cmd.None)
    case Msg.UpdateFacesInput(s: String) =>
      (model.copy(facesInput = s), Cmd.None)
  }

  def viewDie(r:Int): Html[Msg] =
    div(cls:="border border-black p-2 rounded")(text(r.toString))

  def view(model:Model): Html[Msg] = {
    div(cls:="flex flex-col gap-2 p-10")(
      div(cls:="flex gap-2") ( model.result.rolls.toList.map(viewDie) ),
      div(cls:="flex gap-2 border border-black") (
        input(onChange(Msg.UpdateDiceInput.apply),value:=model.diceInput),
        input(onChange(Msg.UpdateFacesInput.apply),value:=model.facesInput)
      ),
      Button.interactive("Roll",Msg.ClickRoll)

    )
  }

  def subscriptions(model: Model): Sub[IO, Msg] =
    Sub.None
}

