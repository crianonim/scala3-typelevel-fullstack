package com.crianonim.tables

import cats.effect.*
import cats.implicits.*
import doobie.util.ExecutionContexts
import doobie.hikari.HikariTransactor
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.{CORS, CORSPolicy}
import org.http4s.server.staticcontent.*
import com.crianonim.tables.core.*
import com.crianonim.tables.http.*
import org.http4s.HttpRoutes

object Application extends IOApp.Simple {
  def makePostgres = for {
    ec <- ExecutionContexts.fixedThreadPool[IO](32)
    transactor <- HikariTransactor.newHikariTransactor[IO](
      "org.postgresql.Driver",
      "jdbc:postgresql://localhost:5444/",
      "docker",
      "docker",
      ec
    )
  } yield transactor
  val web: HttpRoutes[IO] = fileService(FileService.Config("./app/dist"))
  val corsPolicy: CORSPolicy =CORS.policy
    .withAllowOriginAll
    .withAllowCredentials(false)
  def makeServer = for {
    postgres <- makePostgres
    tables     <- TablesLive.resource[IO](postgres)
    tablesApi   <- TablesRoutes.resource[IO](tables)
    
    server <- EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(corsPolicy
        ((tablesApi.routes <+> web).orNotFound))
      .build
  } yield server

  override def run: IO[Unit] =
    makeServer.use(_ => IO.println("Crianonim Server ready. Test localhost:4041/tables.") *> IO.never)
}