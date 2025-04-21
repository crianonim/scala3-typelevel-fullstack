//> using dep com.softwaremill.sttp.tapir::tapir-core:1.11.25
//> using dep com.softwaremill.sttp.tapir::tapir-http4s-server:1.11.25
//> using dep com.softwaremill.sttp.tapir::tapir-swagger-ui-bundle:1.11.25
//> using dep org.http4s::http4s-blaze-server:0.23.17
//> using dep com.softwaremill.sttp.tapir::tapir-json-circe:1.11.25

import cats.effect.{ExitCode, IO, IOApp}
import cats.syntax.all.*
import org.http4s.HttpRoutes
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.server.Router
import sttp.tapir.*
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.swagger.bundle.SwaggerInterpreter
import sttp.tapir.json.circe.*


object HelloWorldTapir extends IOApp:
  
  val helloWorldEndpoint = endpoint.get
    .in("hello" / "world")
    .in(query[String]("name"))
    .out(stringBody)
    .serverLogic[IO](name => IO
      .println(s"Saying hello to: $name")
      .flatMap(_ => IO.pure(Right(s"Hello, $name!"))))

  val calculationEndpoint = endpoint.get
    .in("calculate" / "sum")
    .in(query[Int]("a"))
    .in(query[Int]("b"))
    .out(jsonBody[Int])
    .serverLogic[IO] { (a, b) =>
      IO.pure(Right(a + b))
    }

  val helloWorldRoutes: HttpRoutes[IO] = Http4sServerInterpreter[IO]()
    .toRoutes(helloWorldEndpoint)

  val calculationRoutes: HttpRoutes[IO] = Http4sServerInterpreter[IO]()
    .toRoutes(calculationEndpoint)

    // This is the endpoint that will be used to generate the Swagger documentation
    // if fromServerEndpoints requires an effect type (or Identity)
    val swaggerEndpoints = SwaggerInterpreter()
    .fromServerEndpoints[IO](List(helloWorldEndpoint, calculationEndpoint), "My App", "1.0")

  val swaggerRoutes: HttpRoutes[IO] = Http4sServerInterpreter[IO]().toRoutes(swaggerEndpoints)

  val allRoutes: HttpRoutes[IO] = helloWorldRoutes <+> swaggerRoutes <+> calculationRoutes

  override def run(args: List[String]): IO[ExitCode] =
    BlazeServerBuilder[IO]
      .bindHttp(8080, "localhost")
      .withHttpApp(Router("/" -> allRoutes).orNotFound)
      .resource
      .useForever