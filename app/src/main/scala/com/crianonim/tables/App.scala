package com.crianonim.tables

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import cats.effect.*
import tyrian.*
import tyrian.Html.*
import tyrian.http.*
//import io.circe.syntax.*
import io.circe.parser.*
import io.circe.generic.auto.*
import com.crianonim.tables.domain.tables.TableColumns

enum Msg {
  case NoMsg
  case LoadJobs(jobs: List[TableColumns])
  case Error(e: String)
}

case class Model(jobs: List[TableColumns] = List())

@JSExportTopLevel("TablesApp")
object App extends TyrianIOApp[Msg, Model] {

  def backendCall: Cmd[IO, Msg] =
    Http.send(
      Request.get("http://localhost:4041/tables"),
      Decoder[Msg](
        resp =>
          parse(resp.body).flatMap(_.as[List[TableColumns]]) match {
            case Left(e)     => Msg.Error(e.getMessage())
            case Right(list) => Msg.LoadJobs(list)
          },
        err => Msg.Error(err.toString)
      )
    )

  override def router : Location => Msg =
     _ => Msg.NoMsg

  override def init(flags: Map[String, String]): (Model, Cmd[IO, Msg]) =
    (Model(), backendCall)

  def viewTableColumns(table: TableColumns): Html[Msg]=
    div(cls:="flex gap-1")(span()(table.columnName),span()(table.dataType))
  override def view(model: Model): Html[Msg] =
    div(`class` := "p-10")(
      p("Hello to Tables app"),
      div(`class` := "flex flex-col gap-3 ")(
        model.jobs.map(viewTableColumns)
      )
    )

  override def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = msg =>
    msg match {
      case Msg.NoMsg          => (model, Cmd.None)
      case Msg.Error(e)       => (model, Cmd.None)
      case Msg.LoadJobs(list) => (model.copy(jobs = model.jobs ++ list), Cmd.None)
    }

  override def subscriptions(model: Model): Sub[IO, Msg] =
    Sub.None
}
