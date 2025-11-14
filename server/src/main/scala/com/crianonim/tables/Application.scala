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
import org.http4s.dsl.Http4sDsl
import org.http4s.StaticFile
import org.http4s.Response

object Application extends IOApp.Simple {
  def makePostgres: Resource[IO, HikariTransactor[IO]] = for {
    ec <- ExecutionContexts.fixedThreadPool[IO](32)
    transactor <- HikariTransactor.newHikariTransactor[IO](
      "org.postgresql.Driver",
      "jdbc:postgresql://localhost:5444/",
      "docker",
      "docker",
      ec
    )
  } yield transactor
  // Serve static files from the dist directory
  val staticFiles: HttpRoutes[IO] = fileService(FileService.Config("./app/dist"))

  // Fallback route to serve index.html for any unmatched routes (SPA routing)
  // This is useful for Single Page Applications where client-side routing handles the rest
  val fallbackRoute: HttpRoutes[IO] = HttpRoutes.of[IO] { case _ =>
    StaticFile
      .fromFile[IO](new java.io.File("./app/dist/index.html"))
      .getOrElse(Response.notFound[IO])
  }

  // Combine static file serving with fallback using <+> (orElse)
  // This will first try to serve static files, and if not found, serve index.html
  val web: HttpRoutes[IO] = staticFiles <+> fallbackRoute

  val corsPolicy: CORSPolicy = CORS.policy.withAllowOriginAll
    .withAllowCredentials(false)
  def makeServer = for {
    // postgres  <- makePostgres
    // tables    <- TablesLive.resource[IO](postgres)
    // tablesApi <- TablesRoutes.resource[IO](tables)

    server <- EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(corsPolicy((web).orNotFound))
      .build
  } yield server

  override def run: IO[Unit] =
    makeServer.use(_ =>
      IO.println("Crianonim Server ready. Test localhost:4041/tables.") *> IO.never
    )
}
