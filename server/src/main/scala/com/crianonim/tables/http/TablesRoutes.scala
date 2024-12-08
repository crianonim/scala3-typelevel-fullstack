package com.crianonim.tables.http


import cats.effect.*
import cats.*
import cats.syntax.all.*
import org.http4s.*
import org.http4s.dsl.Http4sDsl
import org.http4s.circe.CirceEntityCodec.*
import io.circe.generic.auto.*
import com.crianonim.tables.core.*
import com.crianonim.tables.domain.tables.TableColumns
import org.http4s.server.Router

class TablesRoutes[F[_]: Concurrent] private (tables: Tables[F]) extends Http4sDsl[F] {
  private val prefix = "/tables"


  // get /tabless
  private val getAllRoute: HttpRoutes[F] = HttpRoutes.of[F] { case GET -> Root =>
    tables.all.flatMap(jobs => Ok(jobs))
  }

  val routes: HttpRoutes[F] = Router(
    prefix -> ( getAllRoute)
  )
}

object TablesRoutes {
  def resource[F[_]: Concurrent](tables: Tables[F]): Resource[F, TablesRoutes[F]] =
    Resource.pure(new TablesRoutes[F](tables))
}
