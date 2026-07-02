package com.crianonim.gentree

import com.crianonim.timelines.TimePoint
import io.circe.{Encoder, Decoder}
import io.circe.generic.semiauto.*

case class Person(
    id: String,
    name: String,
    description: Option[String],
    born: TimePoint,
    died: Option[TimePoint],
    motherId: Option[String],
    fatherId: Option[String]
)

object Person {
  implicit val encoder: Encoder[Person] = deriveEncoder
  implicit val decoder: Decoder[Person] = deriveDecoder
}
