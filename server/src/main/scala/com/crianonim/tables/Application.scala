package com.crianonim.tables

import cats.effect.*
import doobie.util.ExecutionContexts
import doobie.hikari.HikariTransactor
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.{CORS, CORSPolicy}
import com.crianonim.tables.core.*
import com.crianonim.tables.http.*

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
      .withPort(port"4041")
      .withHttpApp(corsPolicy
        (tablesApi.routes.orNotFound))
      .build
  } yield server

  override def run: IO[Unit] =
    makeServer.use(_ => IO.println("Crianonim Server ready. Test localhost:4041/tables.") *> IO.never)
}