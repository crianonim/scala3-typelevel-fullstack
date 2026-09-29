package com.crianonim.tables

import cats.effect.*
import doobie.hikari.HikariTransactor
import doobie.implicits.*
import doobie.util.ExecutionContexts
import doobie.util.transactor.Transactor

import java.nio.file.Files

/** Everything needed to reach Postgres, resolved from the environment.
  *
  * Lookup order, first hit wins:
  *   1. the process environment (so `PGHOST=... sbt "server / run"` works)
  *   2. `.env.local` in the working directory (git-ignored; see `.env.example`)
  *   3. the local `db/docker-compose.yml` defaults
  *
  * The variable names are the libpq standard ones, so the same `.env.local` works for `psql`,
  * `pg_dump` and other tooling.
  */
final case class DbConfig(
    host: String,
    port: Int,
    database: String,
    user: String,
    password: String,
    sslmode: Option[String],
    channelBinding: Option[String]
):

  private val extraParams: List[String] =
    sslmode.toList.map(m => s"sslmode=$m") ::: channelBinding.toList.map(c => s"channelBinding=$c")

  val url: String =
    s"jdbc:postgresql://$host:$port/$database" +
      (if extraParams.isEmpty then "" else "?" + extraParams.mkString("&"))

  /** Log-safe summary: never includes the password. */
  def describe: String = s"$host:$port/$database (user=$user)"

  /** Redacted so a stray `cfg` in a log statement cannot leak the password. */
  override def toString: String = describe

object DbConfig:
  // The standard Postgres port, used for any host other than the local Docker database.
  private val DefaultHost     = "localhost"
  private val DefaultPort     = 5432
  // db/docker-compose.yml publishes the container's 5432 as 5444 on the host.
  private val DockerPort      = 5444
  private val DefaultUser     = "docker"
  private val DefaultPassword = "docker"

  val EnvFileName = ".env.local"

  /** Parse a `.env`-style file: `KEY=value` lines, `#` comment lines, optional single or double
    * quotes around the value. Inline `#` comments are deliberately *not* stripped, so a password
    * containing `#` survives intact.
    */
  def parseEnvFile(contents: String): Map[String, String] =
    contents.linesIterator
      .map(_.trim)
      .filter(line => line.nonEmpty && !line.startsWith("#"))
      .flatMap { line =>
        line.indexOf('=') match
          case -1 => None
          case i =>
            val key   = line.substring(0, i).trim
            val value = unquote(line.substring(i + 1).trim)
            Option.when(key.nonEmpty)(key -> value)
      }
      .toMap

  private def unquote(value: String): String =
    val quoted =
      value.length >= 2 &&
        ((value.startsWith("\"") && value.endsWith("\"")) ||
          (value.startsWith("'") && value.endsWith("'")))
    if quoted then value.substring(1, value.length - 1) else value

  /** Merge the two sources. `env` is the process environment and wins over the file. */
  def fromEnv(env: Map[String, String], envFile: Map[String, String]): DbConfig =
    def get(key: String): Option[String] = env.get(key).orElse(envFile.get(key))

    val user     = get("PGUSER").getOrElse(DefaultUser)
    // libpq and the JDBC driver both default the database name to the user
    val database = get("PGDATABASE").getOrElse(user)
    val host     = get("PGHOST").getOrElse(DefaultHost)
    DbConfig(
      host           = host,
      // This repo's compose file publishes the container's 5432 as 5444, so localhost without an
      // explicit PGPORT means the Docker database. Anything else is a real Postgres server, so it
      // gets the standard port unless the user says otherwise.
      port           = get("PGPORT").flatMap(_.toIntOption).getOrElse(defaultPortFor(host)),
      database       = database,
      user           = user,
      password       = get("PGPASSWORD").getOrElse(DefaultPassword),
      sslmode        = get("PGSSLMODE"),
      channelBinding = get("PGCHANNELBINDING")
    )

  private val LocalHosts = Set("localhost", "127.0.0.1", "::1")

  private def defaultPortFor(host: String): Int =
    if LocalHosts.contains(host) then DockerPort else DefaultPort

  /** Read the process environment plus `.env.local` from the working directory.
    *
    * Note the explicit `()` at every call site: because `dir` has a default, Scala 3 eta-expands
    * `DbConfig.load` to a function value inside a select chain instead of applying it.
    */
  def load(dir: java.io.File = new java.io.File(".")): IO[DbConfig] =
    IO.blocking {
      val file     = new java.io.File(dir, EnvFileName)
      val contents =
        if file.isFile then Files.readString(file.toPath) else ""
      fromEnv(sys.env, parseEnvFile(contents))
    }

/** The Doobie side: the connection pool and a reachability probe. */
object Db:

  /** A HikariCP pool plus Doobie's blocking compute EC, scoped to the caller's lifetime.
    *
    * The EC is passed as a `Resource` so Doobie's shutdown also closes the pool and the threads.
    * Doobie must never run its blocking JDBC work on the Cats Effect CPU pool — this is the
    * 32-thread pool that keeps that promise.
    */
  def transactor(cfg: DbConfig): Resource[IO, HikariTransactor[IO]] =
    ExecutionContexts.fixedThreadPool[IO](32).flatMap { ec =>
      HikariTransactor.newHikariTransactor[IO](
        "org.postgresql.Driver",
        cfg.url,
        cfg.user,
        cfg.password,
        ec
      )
    }

  /** Hikari connects lazily, so without this a bad host only surfaces on the first request.
    * Round-trips one trivial query to find out at startup instead.
    */
  def check(tx: Transactor[IO]): IO[Unit] =
    sql"select 1".query[Int].unique.transact(tx).void

  /** Unwrap a JDBC failure into something actionable.
    *
    * `PSQLException.getMessage` is routinely just "The connection attempt failed", with the real
    * reason (DNS, TCP timeout, TLS handshake, auth rejection) buried in the `getCause` chain. Walk
    * it and keep the innermost message, which is the one that tells you what to fix.
    */
  def describeCause(t: Throwable): String =
    @annotation.tailrec
    def loop(current: Throwable, acc: List[String]): List[String] =
      if current == null then acc
      else
        val msg = Option(current.getMessage).map(_.trim).filter(_.nonEmpty)
        val next = Option(current.getCause).filter(_ ne current)
        val entries = msg.toList ++ next.map(c => s"(${c.getClass.getSimpleName})").toList
        loop(next.orNull, acc ++ entries)

    val chain = loop(t, Nil)
    val root  = chain.lastOption.getOrElse(t.getClass.getName)
    val head  = chain.headOption.getOrElse(root)
    // Collapse the driver's generic wrapper; if it adds nothing, show only the root cause.
    if chain.size <= 2 then root else s"$head -> ${chain.drop(1).mkString(" -> ")}"
