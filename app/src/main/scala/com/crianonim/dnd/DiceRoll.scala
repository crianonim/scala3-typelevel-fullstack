package com.crianonim.dnd

import cats.effect.*
import tyrian.*
import tyrian.Html.*
import cats.effect.std.Random
import cats.syntax.all.*

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

  def getRollDef(model: Model): RollDef =
    val dice = model.diceInput.toInt
    val faces = model.facesInput.toInt
    RollDef(DR(dice, faces), None)

  def rollCmd(model: Model):Cmd[IO,Msg] = {

    val d = for {
      given Random[IO] <- Random.scalaUtilRandom[IO]
      roll = Roll.forCats[IO]
      result <- roll.roll(getRollDef(model))
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
    div(cls:="border border-black p-2 rounded w-10 flex justify-center")(text(r.toString))

  def view(model:Model): Html[Msg] = {
    div(cls:="flex flex-col gap-2 p-10")(
      div(cls:="flex gap-2 text-storm-dust-700 items-center") (
        text("Dice"),
        div(cls:="w-16")(
        Input.interactive(model.diceInput,Msg.UpdateDiceInput.apply,"number")),
        text("Faces"),
        div(cls:="w-20")( Input.interactive(model.facesInput,Msg.UpdateFacesInput.apply,"number")),
        div(cls:="flex gap-2")(
          Button.interactive("d4", Msg.UpdateFacesInput("4")),
          Button.interactive("d6", Msg.UpdateFacesInput("6")),
          Button.interactive("d8", Msg.UpdateFacesInput("8")),
          Button.interactive("d10", Msg.UpdateFacesInput("10")),
          Button.interactive("d20", Msg.UpdateFacesInput("20")),
          Button.interactive("d100", Msg.UpdateFacesInput("100")),
        ),

      ),
      Button.interactive( (getRollDef(model).show)++" Roll ",Msg.ClickRoll),
      div(cls:="flex gap-2")(text("Result: "),text((model.result.rolls.sum+model.result.mod).toString)),
      div(cls:="flex gap-2") ( model.result.rolls.toList.map(viewDie) ),

    )
  }

  def subscriptions(model: Model): Sub[IO, Msg] =
    Sub.None
}

