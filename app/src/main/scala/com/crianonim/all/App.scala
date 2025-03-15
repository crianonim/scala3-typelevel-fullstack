package com.crianonim.all

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import cats.effect.*
import tyrian.*
import tyrian.Html.*
//import io.circe.syntax.*
import com.crianonim.tables.TablesApp
import com.crianonim.dnd.DiceRoll
import com.crianonim.timelines.TimelinesApp
enum Msg {
  case NoMsg
  case NavigateTo(nav:Page)
  case UpdateTablesApp(tMsg: TablesApp.Msg)
  case UpdateDiceRollApp(tMsg: DiceRoll.Msg)
  case UpdateTimelines(tMsg: TimelinesApp.Msg)
}

case class Model(
                  page:Page,
                  tables: TablesApp.Model,
                  diceRoll: DiceRoll.Model,
                  timelines: TimelinesApp.Model
                )

@JSExportTopLevel("AllApp")
object App extends TyrianIOApp[Msg, Model] {

  override def router : Location => Msg =
    case loc: Location.Internal =>
      loc.pathName match
        case "/" => Msg.NavigateTo(Page.MainPage)
        case "/tables" => Msg.NavigateTo(Page.TablesPage)
        case "/roll" => Msg.NavigateTo(Page.DiceRollPage)
        case "/timelines" => Msg.NavigateTo(Page.TimelinesPage)
        case _ => Msg.NoMsg
    case loc: Location.External =>
      Msg.NoMsg

  override def init(flags: Map[String, String]): (Model, Cmd[IO, Msg]) =
    val tablesModel = TablesApp.initEmpty
    val diceRollModel = DiceRoll.init
    val timelinesModel = TimelinesApp.init
    (Model(Page.MainPage,tablesModel,diceRollModel, timelinesModel),Cmd.None)

  override def view(model:Model): Html[Msg]=
    div(cls:="flex flex-col gap-2 p-10")(
      div(cls:="flex gap-4")(
        a(href:="/")("Mains"),
        a(href:="/tables")("Tables"),
        a(href:="/roll")("Roll"),
        a(href:="/timelines")("Timelines"),
      ), model.page match {
    case Page.MainPage => div()("APP")
    case Page.TablesPage => TablesApp.view(model.tables).map(Msg.UpdateTablesApp.apply)
    case Page.DiceRollPage => DiceRoll.view(model.diceRoll).map(Msg.UpdateDiceRollApp.apply)
    case Page.TimelinesPage => TimelinesApp.view(model.timelines).map(Msg.UpdateTimelines.apply)
  })


  override def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = {
    case Msg.NoMsg => (model, Cmd.None)
    case Msg.UpdateTablesApp(tMsg) =>
      val (tablesModel, tablesCmd) = TablesApp.update(model.tables)(tMsg)
      (model.copy(tables = tablesModel), tablesCmd.map(Msg.UpdateTablesApp.apply))
    case Msg.NavigateTo(page) => 
      (model.copy(page=page),Cmd.None)
    case Msg.UpdateDiceRollApp(drMsg) =>
      val (drModel,drCmd) = DiceRoll.update(model.diceRoll)(drMsg)
      (model.copy(diceRoll = drModel), drCmd.map(Msg.UpdateDiceRollApp.apply))
    case com.crianonim.all.Msg.UpdateTimelines(tMsg) =>
      val (tmModel,tlCmd) = TimelinesApp.update(model.timelines)(tMsg)
      (model.copy(timelines= tmModel), tlCmd.map(Msg.UpdateTimelines.apply))
  }

  override def subscriptions(model: Model): Sub[IO, Msg] =
    Sub.None
}

enum Page:
  case MainPage, TablesPage, DiceRollPage, TimelinesPage