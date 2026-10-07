package com.crianonim.janscape

import cats.Monad
import cats.effect.std.Random
import cats.syntax.all.*
import io.circe.{Decoder, DecodingFailure}

import scala.collection.immutable.ListMap

/** How much of a floor one resource is worth, keyed by item name.
  *
  * Weights are relative: they only have to describe the mix a designer wants. Insertion order is
  * the config's own, and it matters — generateFloor resolves split ties by it, and the UI shows
  * nodes in it, which a plain Map would not preserve.
  */
type Weights = ListMap[String, Double]

given [A: Decoder]: Decoder[ListMap[String, A]] = Decoder.instance { c =>
  c.value.asObject match
    case None =>
      Left(DecodingFailure("expected an object", c.history))
    case Some(obj) =>
      obj.toList.foldLeft[Either[DecodingFailure, ListMap[String, A]]](Right(ListMap.empty)) {
        case (acc, (key, value)) =>
          acc.flatMap(m => value.as[A].map(a => m + (key -> a)))
      }
}

/** One band of the depth schedule. The mix ramps from `from` to `to` across the band's levels, and
  * an omitted `to` means the band holds where it started.
  */
case class Band(from: Weights, to: Option[Weights]):
  def issues(path: String): List[Issue] =
    weightsIssues(from, s"$path.from") ++
      to.toList.flatMap(end => weightsIssues(end, s"$path.to")) ++
      Issue.when(s"$path.from", from.values.exists(_ > 0), "a band needs a weight above 0") ++
      (to match
        case None      => Nil
        case Some(end) =>
          Issue.when(s"$path.to", end.values.exists(_ > 0), "a band needs a weight above 0"))

  private def weightsIssues(weights: Weights, path: String): List[Issue] =
    weights.toList.flatMap { case (item, weight) =>
      Issue.when(path, item.nonEmpty, "keys must contain at least one character") ++
        Issue.when(path, weight >= 0, "must be >= 0")
    }

object Band:
  given Decoder[Band] = io.circe.generic.semiauto.deriveDecoder

/** How many nodes a floor holds, rolled fresh every time the character arrives at a level. A mine
  * floor and a forest glade are the same shape, so they share one description.
  */
case class NodeCount(min: Int, max: Int):
  def issues(path: String): List[Issue] =
    Issue.when(s"$path.min", min >= 1, "must be >= 1") ++
      Issue.when(s"$path.max", max >= 1, "must be >= 1") ++
      Issue.when(s"$path.max", max >= min, "max must be >= min")

object NodeCount:
  given Decoder[NodeCount] = io.circe.generic.semiauto.deriveDecoder

/** How a depth's floor is composed.
  *
  * bandLevels is how many levels a band covers. The first band is one level short, so that every
  * band after it begins on a multiple of bandLevels and a designer can line resource debuts up with
  * the fast-travel milestones. The last band covers every level below it, so a mine or forest can
  * be any depth without the schedule running out.
  */
case class Schedule(bandLevels: Int, minShare: Double, bands: List[Band]):
  def issues(path: String): List[Issue] =
    Issue.when(s"$path.bandLevels", bandLevels >= 2, "must be >= 2") ++
      Issue.when(
        s"$path.minShare",
        minShare >= 0 && minShare <= 1,
        "must be between 0 and 1"
      ) ++
      Issue.when(s"$path.bands", bands.nonEmpty, "must contain at least one band") ++
      bands.zipWithIndex.flatMap { case (band, index) =>
        band.issues(s"$path.bands.$index")
      }

object Schedule:
  given Decoder[Schedule] = io.circe.generic.semiauto.deriveDecoder

  /** Which band a level falls in.
    *
    * Clamped to the last band, which is what makes that band the floor of the schedule: a place
    * deeper than the last band described still gets a mix rather than nothing.
    */
  def bandOf(level: Int, schedule: Schedule): Int =
    val index = if level < schedule.bandLevels then 0 else level / schedule.bandLevels
    math.min(index, schedule.bands.length - 1)

  private def bandStart(band: Int, schedule: Schedule): Int =
    if band == 0 then 1 else band * schedule.bandLevels

  private def bandWidth(band: Int, schedule: Schedule): Int =
    if band == 0 then schedule.bandLevels - 1 else schedule.bandLevels

  /** The weights for a level, resolved from the schedule.
    *
    * Ramps linearly from the band's starting mix to its ending mix, then drops anything under
    * minShare — which is what keeps a resource fading out from claiming nodes alongside the one
    * fading in. Only depth feeds this, so what a floor is made of cannot drift with skill.
    */
  def weightsAtLevel(level: Int, schedule: Schedule): Weights =
    val band     = bandOf(level, schedule)
    val from     = schedule.bands(band).from
    val to       = schedule.bands(band).to.getOrElse(from)
    val width    = bandWidth(band, schedule)
    val progress =
      if width > 1 then (level - bandStart(band, schedule)).toDouble / (width - 1)
      else 0.0

    val order  = from.keys.toList ++ to.keys.filterNot(from.contains)
    val ramped = ListMap(order.map { item =>
      val start = from.getOrElse(item, 0.0)
      val end   = to.getOrElse(item, 0.0)
      item -> (start + (end - start) * progress)
    }*)

    val total = totalWeight(ramped)
    ListMap(
      ramped.iterator.filter { case (_, weight) => weight / total >= schedule.minShare }.toSeq*
    )

  def totalWeight(weights: Weights): Double =
    weights.valuesIterator.sum

  /** How many of one resource a floor holds, zero when it holds none. */
  def nodeCount(nodes: List[ItemStack], item: String): Int =
    nodes.find(_.item == item).map(_.count).getOrElse(0)

  /** Split a floor's nodes by weight rather than drawing them one at a time: proportions stay
    * visible on every floor, and entries that come out with no share are dropped rather than shown
    * as an empty button. Largest remainder with a stable sort, so ties keep config order and a
    * floor is fully determined by its count.
    */
  def generateFloor[F[_]: Monad](weights: Weights, count: NodeCount)(using
      random: Random[F]
  ): F[List[ItemStack]] =
    rollNodeCount(count).map { total =>
      val weightTotal = totalWeight(weights)
      val exact       = weights.valuesIterator.map(weight => (total * weight) / weightTotal).toList
      val taken       = exact.map(math.floor)
      val whole       = taken.sum

      val remainders = taken.indices
        .map(index => (index, exact(index) - taken(index)))
        .sortBy { case (_, fraction) => -fraction }
        .map(_._1)

      val counts   = taken.map(_.toInt).toArray
      var leftover = (total - whole).toInt
      var at       = 0
      while leftover > 0 do
        counts(remainders(at)) += 1
        at += 1
        leftover -= 1

      weights.keys
        .zip(counts)
        .map { case (item, amount) => ItemStack(item, amount) }
        .filter(_.count > 0)
        .toList
    }

  private def rollNodeCount[F[_]: Monad](count: NodeCount)(using random: Random[F]): F[Int] =
    val span = count.max - count.min + 1
    random.nextDouble.map(d => count.min + (d * span).toInt)
