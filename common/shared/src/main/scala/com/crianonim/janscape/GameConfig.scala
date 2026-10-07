package com.crianonim.janscape

import io.circe.{Decoder, Json}
import io.circe.generic.semiauto.*

import scala.collection.immutable.ListMap

private def fmt(value: Double): String =
  if value.isWhole then value.toLong.toString else value.toString

private def atLeast(path: String, value: Double, min: Double): List[Issue] =
  Issue.when(path, value >= min, s"must be >= ${fmt(min)}")

private def atMost(path: String, value: Double, max: Double): List[Issue] =
  Issue.when(path, value <= max, s"must be <= ${fmt(max)}")

private def greaterThan(path: String, value: Double, min: Double): List[Issue] =
  Issue.when(path, value > min, s"must be > ${fmt(min)}")

private def withinUnit(path: String, value: Double): List[Issue] =
  Issue.when(path, value >= 0 && value <= 1, "must be between 0 and 1")

private def nonEmptyKeys(path: String, keys: Iterable[String]): List[Issue] =
  Issue.when(path, keys.forall(_.nonEmpty), "keys must contain at least one character")

private def nonNegativeValues(path: String, values: ListMap[String, Int]): List[Issue] =
  values.toList.flatMap { case (_, value) =>
    Issue.when(path, value >= 0, "must be >= 0")
  }

private def positiveValues(path: String, values: ListMap[String, Int]): List[Issue] =
  values.toList.flatMap { case (_, value) =>
    Issue.when(path, value >= 1, "must be >= 1")
  }

case class FatigueConfig(max: Int, rollMax: Int, exponent: Int):
  def issues(path: String): List[Issue] =
    atLeast(s"$path.max", max, 1) ++
      atLeast(s"$path.rollMax", rollMax, 1) ++
      atLeast(s"$path.exponent", exponent, 1) ++
      Issue.when(
        s"$path.max",
        math.pow(max, exponent) <= rollMax,
        "max^exponent must be <= rollMax, otherwise fatigue at max can never be overcome"
      )

object FatigueConfig:
  given Decoder[FatigueConfig] = deriveDecoder

case class RestConfig(turns: Int):
  def issues(path: String): List[Issue] =
    atLeast(s"$path.turns", turns, 0)

object RestConfig:
  given Decoder[RestConfig] = deriveDecoder

case class PlayConfig(fatigue: FatigueConfig, rest: RestConfig):
  def issues(path: String): List[Issue] =
    fatigue.issues(s"$path.fatigue") ++ rest.issues(s"$path.rest")

object PlayConfig:
  given Decoder[PlayConfig] = deriveDecoder

case class SwingConfig(
    baseTurns: Double,
    turnsPerTier: Double,
    turnsPerPower: Double,
    minTurns: Int,
    xpPerMine: Double,
    fatigue: Int
):
  def issues(path: String): List[Issue] =
    atLeast(s"$path.baseTurns", baseTurns, 0) ++
      atLeast(s"$path.turnsPerTier", turnsPerTier, 0) ++
      atLeast(s"$path.turnsPerPower", turnsPerPower, 0) ++
      atLeast(s"$path.minTurns", minTurns, 1) ++
      atLeast(s"$path.xpPerMine", xpPerMine, 0) ++
      atLeast(s"$path.fatigue", fatigue, 0)

object SwingConfig:
  given Decoder[SwingConfig] = deriveDecoder

case class LadderConfig(chance: Double, descendTurns: Int, descendFatigue: Int):
  def issues(path: String): List[Issue] =
    withinUnit(s"$path.chance", chance) ++
      atLeast(s"$path.descendTurns", descendTurns, 0) ++
      atLeast(s"$path.descendFatigue", descendFatigue, 0)

object LadderConfig:
  given Decoder[LadderConfig] = deriveDecoder

case class FastTravelConfig(everyLevels: Int):
  def issues(path: String): List[Issue] =
    atLeast(s"$path.everyLevels", everyLevels, 1)

object FastTravelConfig:
  given Decoder[FastTravelConfig] = deriveDecoder

case class DepthFactorConfig(levelsPerPoint: Double, max: Int):
  def issues(path: String): List[Issue] =
    greaterThan(s"$path.levelsPerPoint", levelsPerPoint, 0) ++
      atLeast(s"$path.max", max, 1)

object DepthFactorConfig:
  given Decoder[DepthFactorConfig] = deriveDecoder

case class MiningFatigueChanceConfig(
    base: Double,
    perDepthFactorPoint: Double,
    perTier: Double,
    perPower: Double,
    minFactor: Double
):
  def issues(path: String): List[Issue] =
    withinUnit(s"$path.base", base) ++
      atLeast(s"$path.perDepthFactorPoint", perDepthFactorPoint, 0) ++
      atLeast(s"$path.perTier", perTier, 0) ++
      atLeast(s"$path.perPower", perPower, 0) ++
      greaterThan(s"$path.minFactor", minFactor, 0) ++
      atMost(s"$path.minFactor", minFactor, 1)

object MiningFatigueChanceConfig:
  given Decoder[MiningFatigueChanceConfig] = deriveDecoder

/** Which materials a floor is made of, and how that changes with depth: the shared schedule plus
  * the power it takes to break each material. The power table is declared for the whole schedule
  * rather than per band, so a material has one cost everywhere it appears.
  */
case class Materials(
    bandLevels: Int,
    minShare: Double,
    bands: List[Band],
    power: ListMap[String, Int]
):
  def schedule: Schedule = Schedule(bandLevels, minShare, bands)

  def issues(path: String): List[Issue] =
    schedule.issues(path) ++
      nonEmptyKeys(s"$path.power", power.keys) ++
      nonNegativeValues(s"$path.power", power) ++
      Issue.when(
        s"$path.power",
        unpricedMaterials.isEmpty,
        "every material in a band needs a power"
      )

  private def unpricedMaterials: List[String] =
    val names = bands
      .flatMap(band => band.from.keys ++ band.to.toList.flatMap(_.keys))
      .distinct
    names.filterNot(power.contains).sorted

object Materials:
  given Decoder[Materials] = deriveDecoder

case class MiningConfig(
    swing: SwingConfig,
    ladder: LadderConfig,
    fastTravel: FastTravelConfig,
    depthFactor: DepthFactorConfig,
    fatigueChance: MiningFatigueChanceConfig,
    nodes: NodeCount,
    materials: Materials
):
  def issues(path: String): List[Issue] =
    swing.issues(s"$path.swing") ++
      ladder.issues(s"$path.ladder") ++
      fastTravel.issues(s"$path.fastTravel") ++
      depthFactor.issues(s"$path.depthFactor") ++
      fatigueChance.issues(s"$path.fatigueChance") ++
      nodes.issues(s"$path.nodes") ++
      materials.issues(s"$path.materials")

object MiningConfig:
  given Decoder[MiningConfig] = deriveDecoder

/** A crafting recipe: the Smithing level it opens at, the materials it consumes, and the turns one
  * item costs. Turns are per item, so a batch of N costs turns * N and grants the same in XP.
  */
case class Recipe(level: Int, inputs: ListMap[String, Int], turns: Int):
  def issues(path: String): List[Issue] =
    atLeast(s"$path.level", level, 1) ++
      atLeast(s"$path.turns", turns, 1) ++
      Issue.when(s"$path.inputs", inputs.nonEmpty, "a recipe needs at least one input") ++
      nonEmptyKeys(s"$path.inputs", inputs.keys) ++
      positiveValues(s"$path.inputs", inputs)

object Recipe:
  given Decoder[Recipe] = deriveDecoder

case class CraftingConfig(recipes: ListMap[String, Recipe]):
  def issues(path: String): List[Issue] =
    nonEmptyKeys(s"$path.recipes", recipes.keys) ++
      recipes.toList.flatMap { case (item, recipe) =>
        recipe.issues(s"$path.recipes.$item")
      }

object CraftingConfig:
  given Decoder[CraftingConfig] = deriveDecoder

/** The equipment slots, keyed by slot name and listed in the order swaps should be offered. The
  * starting loadout is checked against the same map so a fresh character cannot begin holding
  * something no slot accepts.
  */
case class EquipmentConfig(slots: ListMap[String, ListMap[String, Int]]):
  def issues(path: String): List[Issue] =
    nonEmptyKeys(s"$path.slots", slots.keys) ++
      slots.toList.flatMap { case (slot, items) =>
        Issue.when(s"$path.slots.$slot", items.nonEmpty, "a slot needs at least one item") ++
          nonEmptyKeys(s"$path.slots.$slot", items.keys) ++
          nonNegativeValues(s"$path.slots.$slot", items)
      } ++
      Issue.when(
        s"$path.slots",
        !hasDuplicateSlotItems,
        "an item can only belong to one slot"
      ) ++
      Issue.when(
        s"$path.slots",
        startingLoadoutFits,
        "every starting item needs a slot that accepts it"
      )

  private def hasDuplicateSlotItems: Boolean =
    slots.toList
      .foldLeft(Map.empty[String, String] -> Set.empty[String]) {
        case ((owner, duplicates), (slot, items)) =>
          items.keys.foldLeft(owner -> duplicates) { case ((owner, duplicates), item) =>
            owner.get(item) match
              case Some(first) if first != slot => owner                    -> (duplicates + item)
              case _                            => (owner + (item -> slot)) -> duplicates
          }
      }
      ._2
      .nonEmpty

  private def startingLoadoutFits: Boolean =
    Character.StartingEquipment.forall { case (slot, item) =>
      slots.get(slot).exists(_.contains(item))
    }

object EquipmentConfig:
  given Decoder[EquipmentConfig] = deriveDecoder

/** A buildable: the materials it consumes and the turns it costs. Unlike a recipe there is no level
  * gate and no XP, because building is a one-off and only one of each type can ever exist.
  */
case class Buildable(inputs: ListMap[String, Int], turns: Int):
  def issues(path: String): List[Issue] =
    atLeast(s"$path.turns", turns, 1) ++
      Issue.when(s"$path.inputs", inputs.nonEmpty, "a buildable needs at least one input") ++
      nonEmptyKeys(s"$path.inputs", inputs.keys) ++
      positiveValues(s"$path.inputs", inputs)

object Buildable:
  given Decoder[Buildable] = deriveDecoder

case class BuildingConfig(buildables: ListMap[String, Buildable]):
  def issues(path: String): List[Issue] =
    nonEmptyKeys(s"$path.buildables", buildables.keys) ++
      buildables.toList.flatMap { case (name, buildable) =>
        buildable.issues(s"$path.buildables.$name")
      }

object BuildingConfig:
  given Decoder[BuildingConfig] = deriveDecoder

/** How much one act of gathering brings back. Zero is allowed: a resource that can come up empty is
  * a real outcome, unlike a node count, which cannot.
  */
case class YieldConfig(min: Int, max: Int):
  def issues(path: String): List[Issue] =
    atLeast(s"$path.min", min, 0) ++
      atLeast(s"$path.max", max, 0) ++
      Issue.when(s"$path.max", max >= min, "max must be >= min")

object YieldConfig:
  given Decoder[YieldConfig] = deriveDecoder

/** One thing a glade holds. Tiring marks the resources that do the working on the character:
  * chopping an ash tree is an effort, while picking berries or a fallen stick is not.
  */
case class ForestResourceConfig(tier: Int, turns: Int, `yield`: YieldConfig, tiring: Boolean):
  def issues(path: String): List[Issue] =
    atLeast(s"$path.tier", tier, 0) ++
      atLeast(s"$path.turns", turns, 1) ++
      `yield`.issues(s"$path.yield")

object ForestResourceConfig:
  given Decoder[ForestResourceConfig] = deriveDecoder

case class ForestPathConfig(chance: Double, deeperTurns: Int, deeperFatigue: Int):
  def issues(path: String): List[Issue] =
    withinUnit(s"$path.chance", chance) ++
      atLeast(s"$path.deeperTurns", deeperTurns, 0) ++
      atLeast(s"$path.deeperFatigue", deeperFatigue, 0)

object ForestPathConfig:
  given Decoder[ForestPathConfig] = deriveDecoder

case class YieldFactorConfig(perDepthFactorPoint: Double, min: Int):
  def issues(path: String): List[Issue] =
    atLeast(s"$path.perDepthFactorPoint", perDepthFactorPoint, 0) ++
      atLeast(s"$path.min", min, 1)

object YieldFactorConfig:
  given Decoder[YieldFactorConfig] = deriveDecoder

case class PathChanceConfig(base: Double, perDepthFactorPoint: Double):
  def issues(path: String): List[Issue] =
    withinUnit(s"$path.base", base) ++
      atLeast(s"$path.perDepthFactorPoint", perDepthFactorPoint, 0)

object PathChanceConfig:
  given Decoder[PathChanceConfig] = deriveDecoder

case class ForestFatigueChanceConfig(
    base: Double,
    perDepthFactorPoint: Double,
    perTier: Double,
    minFactor: Double
):
  def issues(path: String): List[Issue] =
    withinUnit(s"$path.base", base) ++
      atLeast(s"$path.perDepthFactorPoint", perDepthFactorPoint, 0) ++
      atLeast(s"$path.perTier", perTier, 0) ++
      greaterThan(s"$path.minFactor", minFactor, 0) ++
      atMost(s"$path.minFactor", minFactor, 1)

object ForestFatigueChanceConfig:
  given Decoder[ForestFatigueChanceConfig] = deriveDecoder

case class ChopConfig(fatigue: Int):
  def issues(path: String): List[Issue] =
    atLeast(s"$path.fatigue", fatigue, 0)

object ChopConfig:
  given Decoder[ChopConfig] = deriveDecoder

case class ForestConfig(
    glade: NodeCount,
    schedule: Schedule,
    resources: ListMap[String, ForestResourceConfig],
    path: ForestPathConfig,
    fastTravel: FastTravelConfig,
    depthFactor: DepthFactorConfig,
    yieldFactor: YieldFactorConfig,
    pathChance: PathChanceConfig,
    fatigueChance: ForestFatigueChanceConfig,
    chop: ChopConfig
):
  def issues(path: String): List[Issue] =
    glade.issues(s"$path.glade") ++
      schedule.issues(s"$path.schedule") ++
      Issue.when(
        s"$path.resources",
        resources.nonEmpty,
        "the forest needs at least one resource"
      ) ++
      nonEmptyKeys(s"$path.resources", resources.keys) ++
      resources.toList.flatMap { case (name, resource) =>
        resource.issues(s"$path.resources.$name")
      } ++
      this.path.issues(s"$path.path") ++
      fastTravel.issues(s"$path.fastTravel") ++
      depthFactor.issues(s"$path.depthFactor") ++
      yieldFactor.issues(s"$path.yieldFactor") ++
      pathChance.issues(s"$path.pathChance") ++
      fatigueChance.issues(s"$path.fatigueChance") ++
      chop.issues(s"$path.chop") ++
      Issue.when(
        s"$path.resources",
        scheduledResources.forall(resources.contains),
        "every resource in a band needs an entry"
      ) ++
      Issue.when(
        s"$path.resources",
        resources.keys.forall(scheduledResources.contains),
        "every resource needs a place in a band"
      )

  private def scheduledResources: Set[String] =
    schedule.bands.flatMap { band =>
      band.from.keys ++ band.to.toList.flatMap(_.keys)
    }.toSet

object ForestConfig:
  given Decoder[ForestConfig] = deriveDecoder

/** The tunable side of the game, verbatim from config/game.json (see [[GameConfigJson]]).
  *
  * Two rules govern what belongs there versus in code: tunables are numbers a designer adjusts
  * while balancing, and structural invariants are numbers that describe the save format, which stay
  * next to the schema that enforces them — MAX_LEVEL and MAX_SAVE_FATIGUE are examples.
  */
case class GameConfig(
    play: PlayConfig,
    skills: SkillsConfig,
    mining: MiningConfig,
    crafting: CraftingConfig,
    equipment: EquipmentConfig,
    building: BuildingConfig,
    forest: ForestConfig
):
  def issues: List[Issue] =
    play.issues("play") ++
      skills.issues("skills") ++
      mining.issues("mining") ++
      crafting.issues("crafting") ++
      equipment.issues("equipment") ++
      building.issues("building") ++
      forest.issues("forest")

object GameConfig:
  given Decoder[GameConfig] = deriveDecoder

  def parse(raw: String): Either[GameError, GameConfig] =
    io.circe.parser.parse(raw) match
      case Left(_) =>
        Left(GameError.NotJson)
      case Right(json) =>
        decode(json)

  def decode(json: Json): Either[GameError, GameConfig] =
    json.as[GameConfig] match
      case Left(failure) =>
        Left(GameError.InvalidConfig(List(Issue("", describe(failure)))))
      case Right(config) =>
        config.issues match
          case Nil    => Right(config)
          case issues => Left(GameError.InvalidConfig(issues))

  lazy val default: GameConfig =
    parse(GameConfigJson.raw)
      .fold(error => throw new IllegalStateException(error.message), identity)

  /** The power a material takes to break. Nothing for an item the schedule does not price, which
    * only a hand-edited save can put on a floor; callers treat that as unbreakable rather than
    * free.
    */
  def miningPowerFor(item: String, materials: Materials): Option[Int] =
    materials.power.get(item)
