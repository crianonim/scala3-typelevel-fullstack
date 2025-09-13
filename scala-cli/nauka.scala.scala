//> using dep "org.typelevel::cats-effect::3.6.3"

import cats.effect._

object HelloWorld extends IOApp.Simple {
  val run: IO[Unit] = IO.println("Hello world")
}
