package com.crianonim.tables.core

import cats.effect
import cats.effect.*
import cats.syntax.all.*
import com.crianonim.tables.domain.tables.TableColumns
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor

trait Tables[F[_]] {
  def all: F[List[TableColumns]]
}

class TablesLive[F[_]: Concurrent] private (transactor: Transactor[F]) extends Tables[F] {
  override def all: F[List[TableColumns]] =
    sql"""
      Select table_name,column_name,data_type from information_schema.columns
      WHERE table_schema = 'public'
    """.query[TableColumns]
      .stream.transact(transactor).compile.toList
}

object TablesLive {
  def make[F[_]: Concurrent](postgres: Transactor[F]): F[TablesLive[F]] =
    new TablesLive[F](postgres).pure[F]

  def resource[F[_]: Concurrent](postgres: Transactor[F]): Resource[F, TablesLive[F]] =
    Resource.pure(new TablesLive[F](postgres))
}

object TablesPlayground extends effect.IOApp.Simple {

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

  def program(postgres: Transactor[IO]) =
    for {
      jobs <- TablesLive.make[IO](postgres)
      list <- jobs.all
      _ <- IO.println(list)
    } yield ()

  override def run: IO[Unit] =
    makePostgres.use(program)

}
