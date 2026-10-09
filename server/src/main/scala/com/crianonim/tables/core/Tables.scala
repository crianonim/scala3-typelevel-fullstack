package com.crianonim.tables.core

import cats.effect
import cats.effect.*
import cats.syntax.all.*
import com.crianonim.tables.Db
import com.crianonim.tables.DbConfig
import com.crianonim.tables.domain.tables.TableColumns
import doobie.implicits.*
import doobie.util.transactor.Transactor

trait Tables[F[_]] {
  def all: F[List[TableColumns]]
}

class TablesLive[F[_]: Concurrent] private (transactor: Transactor[F]) extends Tables[F] {
  // Lists every non-system schema, so user data in schemas other than `public` (e.g.
  // `timelines`) shows up too. The pg_catalog / information_schema / pg_toast filters are the
  // standard way to exclude Postgres' own bookkeeping from an information_schema listing.
  override def all: F[List[TableColumns]] =
    sql"""
      Select table_name,column_name,data_type
        from information_schema.columns
       where table_schema not in ('pg_catalog', 'information_schema')
         and table_schema not like 'pg_toast%'
       order by table_schema, table_name, ordinal_position
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

  // Uses the same .env.local / environment resolution as the server, so it is a faithful
  // check that the configured database is reachable and queryable.
  def program(tx: Transactor[IO]) =
    for {
      jobs <- TablesLive.make[IO](tx)
      list <- jobs.all
      _ <- IO.println(list)
    } yield ()

  override def run: IO[Unit] =
    for
      cfg <- DbConfig.load()
      _   <- IO.println(s"Querying ${cfg.describe}")
      _   <- Db.transactor(cfg).use(tx => Db.check(tx) *> program(tx))
    yield ()

}
