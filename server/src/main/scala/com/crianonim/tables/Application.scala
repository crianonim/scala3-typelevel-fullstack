package com.crianonim.tables

import cats.effect.*
import cats.implicits.*
import com.comcast.ip4s.*
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.middleware.{CORS, CORSPolicy}
import org.http4s.server.staticcontent.*
import com.crianonim.tables.core.*
import com.crianonim.tables.http.*
import org.http4s.HttpRoutes
import org.http4s.Response
import org.http4s.StaticFile

object Application extends IOApp.Simple {
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

  /** The JSON API, backed by Postgres.
    *
    * A database outage must not take the other nine tabs offline, so a failure to reach Postgres
    * is logged loudly and downgraded to "no API" rather than aborting startup.
    */
  val api: Resource[IO, HttpRoutes[IO]] =
    DbConfig.load()
      .toResource
      .evalMap(cfg => IO.println(s"Connecting to Postgres at ${cfg.describe}").as(cfg))
      .flatMap(cfg => Db.transactor(cfg).evalMap(tx => Db.check(tx).as(tx)))
      .flatMap { tx =>
        for
          tables <- TablesLive.resource[IO](tx)
          routes <- TablesRoutes.resource[IO](tables)
        yield routes.routes
      }
      .handleErrorWith { e =>
        val warn = IO.println(
          s"WARNING: Postgres unavailable, /tables disabled -> ${Db.describeCause(e)}"
        )
        Resource.eval(warn).as(HttpRoutes.empty[IO])
      }

  val corsPolicy: CORSPolicy = CORS.policy.withAllowOriginAll
    .withAllowCredentials(false)
  def makeServer = for
    api    <- api
    server <- EmberServerBuilder
      .default[IO]
      .withHost(host"0.0.0.0")
      .withPort(port"8080")
      // `api` must come first: `web`'s index.html fallback would otherwise swallow /tables.
      .withHttpApp(corsPolicy(api <+> web).orNotFound)
      .build
  yield server

  override def run: IO[Unit] =
    makeServer.use(_ =>
      IO.println("Crianonim Server ready. Test localhost:8080/tables.") *> IO.never
    )
}
