package com.crianonim.tables

import cats.effect.*
import tyrian.*
import tyrian.Html.*
import tyrian.http.*
//import io.circe.syntax.*
import io.circe.parser.*
import io.circe.generic.auto.*
import com.crianonim.tables.domain.tables.TableColumns
import  com.crianonim.ui.*


object TablesApp  {
  enum Msg {
    case NoMsg
    case AskForBackend
    case LoadJobs(jobs: Either[String,List[TableColumns]])
//    case Error(e: String)
  }

  case class Model(jobs: Either[String,List[TableColumns] ]= Right(List()))
  def backendCall: Cmd[IO, Msg] =
    Http.send(
      Request.get("/tables"),
      Decoder[Msg](
        resp =>
          parse(resp.body).flatMap(_.as[List[TableColumns]]) match {
            case Left(e)     => Msg.LoadJobs(Left(e.getMessage))
            case Right(list) => Msg.LoadJobs(Right(list))
          },
        err => Msg.LoadJobs(Left(err.toString))
      )
    )


   def initEmpty : Model = Model()

   def init: (Model, Cmd[IO, Msg]) =
    (Model(), backendCall)

  private def viewError(err: String): Html[Msg] =
    div()(err)
  private def viewTableColumns(table: TableColumns): Html[Msg]=
    div(cls:="flex gap-1")(span()(table.columnName),span()(table.dataType))

  def view(model: Model): Html[Msg] =
    div(`class` := "p-10")(
      p("Hello to Tables app"),
      Button.interactive("Get Data",Msg.AskForBackend),
      model.jobs match {
        case Right(list) =>  div(`class` := "flex flex-col gap-3 ")( list.map(viewTableColumns) )
        case Left(err) => viewError(err)
      }

    )

   def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = {
     case Msg.NoMsg => (model, Cmd.None)
     case Msg.AskForBackend => (model, backendCall)
//     case Msg.Error(e) => (model, Cmd.None)
     case Msg.LoadJobs(list) => (model.copy(jobs = list), Cmd.None)
   }

   def subscriptions(model: Model): Sub[IO, Msg] =
    Sub.None
}
