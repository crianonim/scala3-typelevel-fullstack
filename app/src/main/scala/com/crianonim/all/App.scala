package com.crianonim.all

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import cats.effect.*
import com.crianonim.all.Msg.UpdateTablesApp
import tyrian.*
import tyrian.Html.*
//import io.circe.syntax.*
import com.crianonim.tables.TablesApp
enum Msg {
  case NoMsg
  case NavigateTo(nav:Page)
  case UpdateTablesApp(tMsg: TablesApp.Msg)
}

case class Model(
                  page:Page,
                  tables: TablesApp.Model
                )

@JSExportTopLevel("AllApp")
object App extends TyrianIOApp[Msg, Model] {

  override def router : Location => Msg =
    case loc: Location.Internal =>
      loc.pathName match
        case "/" => Msg.NavigateTo(Page.MainPage)
        case "/tables" => Msg.NavigateTo(Page.TablesPage)
        case _ => Msg.NoMsg
    case loc: Location.External =>
      Msg.NoMsg

  override def init(flags: Map[String, String]): (Model, Cmd[IO, Msg]) =
    val tablesModel = TablesApp.initEmpty
    (Model(Page.MainPage,tablesModel), Cmd.None)

  override def view(model:Model): Html[Msg]=
    div(cls:="flex flex-col gap-2 p-10")(
      div(cls:="flex gap-4")(
        a(href:="/")("Main"),
        a(href:="/tables")("Tables"),
      ), model.page match {
    case Page.MainPage => div()("APP")
    case Page.TablesPage => TablesApp.view(model.tables).map(UpdateTablesApp.apply)
  })


  override def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = {
    case Msg.NoMsg => (model, Cmd.None)
    case Msg.UpdateTablesApp(tMsg) =>
      val (tablesModel, tablesCmd) = TablesApp.update(model.tables)(tMsg)
      (model.copy(tables = tablesModel), tablesCmd.map(Msg.UpdateTablesApp.apply))
    case Msg.NavigateTo(page) => 
      (model.copy(page=page),Cmd.None)
  }

  override def subscriptions(model: Model): Sub[IO, Msg] =
    Sub.None
}

enum Page:
  case MainPage, TablesPage