package com.crianonim.janscape

import cats.effect.IO
import cats.effect.std.Random
import cats.syntax.all.*
import io.circe.{Json, JsonObject}
import munit.CatsEffectSuite

import scala.collection.immutable.ListMap

/** The TypeScript janscape-power.ts harness, check for check. The harness rebuilds the config's
  * Mining Tool slot as a ladder of synthetic tools at every power the formulas have to hold for,
  * since power lives on the tool rather than on the save.
  */
class PowerTest extends CatsEffectSuite:

  private val seed                   = 20261007
  private def random: IO[Random[IO]] = Random.scalaUtilRandomSeedInt[IO](seed)

  private val Tools: List[(Int, String)] = List(
    0    -> "wooden pickaxe",
    1    -> "stone pickaxe",
    2    -> "copper pickaxe",
    3    -> "bronze pickaxe",
    4    -> "silver pickaxe",
    5    -> "gold pickaxe",
    6    -> "power 6 pickaxe",
    20   -> "power 20 pickaxe",
    50   -> "power 50 pickaxe",
    1000 -> "power 1000 pickaxe"
  )

  private val Tiers: List[(String, Int)] = List(
    "stone"      -> 0,
    "coal"       -> 0,
    "copper ore" -> 1,
    "tin ore"    -> 2,
    "iron ore"   -> 3,
    "silver ore" -> 4
  )

  private lazy val raw: Json =
    io.circe.parser
      .parse(GameConfigJson.raw)
      .fold(e => fail(s"embedded config is not JSON: ${e.message}"), identity)

  /** The shipped config, with the Mining Tool slot rewritten to hold every synthetic tool. */
  private lazy val config: GameConfig =
    val tools = Json.fromJsonObject(
      JsonObject.fromIterable(Tools.map { case (power, tool) => tool -> Json.fromInt(power) })
    )
    decode(withEquipment(Json.obj(Character.MiningToolSlot -> tools)))

  private lazy val shipped: GameConfig = GameConfig.default
  private val materials                = config.mining.materials
  private val mining                   = config.mining

  private def decode(json: Json): GameConfig =
    GameConfig.decode(json).fold(e => fail(e.message), identity)

  private def withEquipment(slots: Json): Json =
    val object_ = raw.asObject.getOrElse(fail("embedded config is not an object"))
    Json.fromJsonObject(
      object_.add("equipment", Json.obj("slots" -> slots))
    )

  private def withMiningBands(update: List[Json] => List[Json]): Json =
    val materialsJson = raw.hcursor
      .downField("mining")
      .downField("materials")
      .focus
      .getOrElse(fail("config has no mining.materials"))
    val bands = materialsJson.hcursor
      .downField("bands")
      .as[List[Json]]
      .toOption
      .getOrElse(fail("config has no mining.materials.bands"))
    val miningJson = Json.obj("materials" -> Json.obj("bands" -> Json.fromValues(update(bands))))
    raw.deepMerge(Json.obj("mining" -> miningJson))

  private def fresh(power: Int = 1): Character =
    val tool = Tools.toMap.getOrElse(power, fail(s"no tool of power $power"))
    Character
      .createCharacter("Tester")
      .copy(equipment = Map(Character.MiningToolSlot -> tool))

  private def itemNames(floor: List[ItemStack]): List[String] = floor.map(_.item).sorted

  private def scheduleNames(level: Int): List[String] =
    Schedule.weightsAtLevel(level, materials.schedule).keys.toList.sorted

  private def withRandom[A](f: Random[IO] => IO[A]): IO[A] =
    random.flatMap(f)

  // -- the power ladder -----------------------------------------------------

  test("the power ladder is declared"):
    val expected = Tiers.map((item, power) => item -> power)
    val actual   = expected.map((item, power) => item -> GameConfig.miningPowerFor(item, materials))
    assertEquals(actual, expected.map((item, power) => item -> Some(power)))
    assertEquals(GameConfig.miningPowerFor("mithril", materials), None)

  test("canMine follows the ladder"):
    val ladder = List(
      ("stone", 0, true),
      ("coal", 0, true),
      ("copper ore", 0, false),
      ("copper ore", 1, true),
      ("tin ore", 1, false),
      ("tin ore", 2, true),
      ("iron ore", 2, false),
      ("iron ore", 3, true),
      ("silver ore", 3, false),
      ("silver ore", 4, true)
    )
    val actual = ladder.map { (item, power, _) =>
      (item, power, Mining.canMine(item, fresh(power), config))
    }
    assertEquals(actual, ladder)
    assert(Mining.canMine("copper ore", fresh(), config))
    assert(!Mining.canMine("tin ore", fresh(), config))

  // -- floors carry the whole schedule --------------------------------------

  test("every floor is the full schedule, whatever the power"):
    withRandom { implicit r =>
      (0 to 5).toList
        .traverse { power =>
          val character = fresh(power)
          (1 to 40).toList.traverse { level =>
            Mining.enterMine[IO](character, level, config).map(level -> _)
          }
        }
        .map { perPower =>
          val problems = perPower.zipWithIndex.flatMap { case (floors, index) =>
            floors.flatMap { case (level, entered) =>
              val actual   = itemNames(entered.mineNodes)
              val expected = scheduleNames(level)
              (if actual != expected then List(s"power $index level $level: $actual != $expected")
               else Nil) ++
                (if actual.isEmpty then List(s"power $index level $level is empty") else Nil)
            }
          }
          assertEquals(problems, Nil)
        }
    }

  test("at the starting power the deep ores are on the floor but locked"):
    withRandom { implicit r =>
      val character = fresh(1)
      (15 to 30).toList
        .traverse(level => Mining.enterMine[IO](character, level, config))
        .map { floors =>
          val seen = floors.flatMap(_.mineNodes.map(_.item)).distinct
          assert(
            seen.contains("iron ore") && seen.contains("silver ore"),
            seen.sorted.mkString(",")
          )
          assert(
            !Mining.canMine("iron ore", character, config) &&
              !Mining.canMine("silver ore", character, config)
          )
          assert(Mining.canMine("stone", character, config))
          assert(Mining.canMine("coal", character, config))
          assert(Mining.canMine("silver ore", fresh(4), config))
        }
    }

  test("what is locked is read off the floor"):
    withRandom { implicit r =>
      def lockedOn(character: Character, level: Int): IO[List[String]] =
        Mining.enterMine[IO](character, level, config).map { entered =>
          entered.mineNodes
            .map(_.item)
            .filterNot(Mining.canMine(_, character, config))
            .sorted
        }

      for
        deep    <- lockedOn(fresh(1), 20)
        rich    <- lockedOn(fresh(4), 20)
        shallow <- lockedOn(fresh(0), 5)
        first   <- lockedOn(fresh(1), 1)
      yield
        assertEquals(deep, List("iron ore", "silver ore"))
        assertEquals(rich, Nil)
        assert(shallow.contains("copper ore"))
        assertEquals(first, Nil)
        assert(deep.forall(scheduleNames(20).contains))
    }

  // -- mineNode refuses what power cannot break -----------------------------

  test("mineNode refuses what the power cannot break"):
    withRandom { implicit r =>
      def floor(item: String, power: Int): Character =
        fresh(power).copy(mineNodes = List(ItemStack(item, 1)))

      for
        iron    <- Mining.mineNode[IO](floor("iron ore", 1), "iron ore", config)
        mithril <- Mining.mineNode[IO](floor("mithril", 1), "mithril", config)
        copper  <- Mining.mineNode[IO](floor("copper ore", 1), "copper ore", config)
      yield
        assert(iron.isLeft)
        assert(mithril.isLeft)
        copper match
          case Left(error)    => fail(error.message)
          case Right(outcome) =>
            assert(!outcome.failed)
            assert(outcome.character.inventory.exists(_.item == "copper ore"))
    }

  test("a floor with locked nodes cannot be cleared"):
    withRandom { implicit r =>
      for
        entered <- Mining.enterMine[IO](fresh(1), 20, config)
        start = entered.copy(fatigue = 0)
        consumed <- consumeMineable(start, 400)
      yield
        val lockedBefore =
          start.mineNodes.count(stack => !Mining.canMine(stack.item, start, config))
        assert(consumed.mineNodes.forall(stack => !Mining.canMine(stack.item, consumed, config)))
        assertEquals(consumed.mineNodes.length, lockedBefore)
        assert(lockedBefore > 0, s"lockedBefore was $lockedBefore")
        assert(consumed.mineNodes.nonEmpty)
    }

  /** Swing at the mineable nodes until none are left. Fatigue is reset each swing so the loop tests
    * the locked-node rule rather than the fatigue economy.
    */
  private def consumeMineable(character: Character, guard: Int)(using
      r: Random[IO]
  ): IO[Character] =
    if guard <= 0 then IO.pure(character)
    else
      character.mineNodes.find(stack => Mining.canMine(stack.item, character, config)) match
        case None        => IO.pure(character)
        case Some(stack) =>
          Mining.mineNode[IO](character.copy(fatigue = 0), stack.item, config).flatMap {
            case Left(error)    => IO.raiseError(new AssertionError(error.message))
            case Right(outcome) => consumeMineable(outcome.character, guard - 1)
          }

  test("descending rolls the full schedule at the new level"):
    withRandom { implicit r =>
      for
        down <- Mining.descend[IO](
          fresh(1).copy(mineLevel = 19, hasMineLadder = true),
          config
        )
        down4 <- Mining.descend[IO](
          fresh(4).copy(mineLevel = 19, hasMineLadder = true),
          config
        )
      yield
        assertEquals(itemNames(down.character.mineNodes), scheduleNames(20))
        assert(down.character.mineNodes.exists(_.item == "silver ore"))
        assert(!Mining.canMine("silver ore", down.character, config))
        assert(down4.character.mineNodes.exists(_.item == "silver ore"))
        assert(Mining.canMine("silver ore", down4.character, config))
    }

  test("power and skill are both kept out of the floor"):
    val lowSkillHighPower =
      fresh(4).copy(skills = Skills.starting.copy(mining = Skill(50, 0)))
    val highSkillLowPower =
      fresh(1).copy(skills = Skills.starting.copy(mining = Skill(1, 0)))

    def seen(character: Character)(using r: Random[IO]): IO[List[String]] =
      (1 to 300).toList
        .traverse(_ => Mining.enterMine[IO](character, 20, config))
        .map(floors => floors.flatMap(_.mineNodes.map(_.item)).distinct.sorted)

    withRandom { implicit r =>
      for
        low  <- seen(lowSkillHighPower)
        high <- seen(highSkillLowPower)
      yield
        assertEquals(low, high)
        assertEquals(low, scheduleNames(20))
        assert(!Mining.canMine("silver ore", highSkillLowPower, config))
        assert(Mining.canMine("silver ore", lowSkillHighPower, config))
    }

  // -- swings: time and fatigue ---------------------------------------------

  test("tier and power set swing time"):
    val swing                             = mining.swing
    def at(item: String, power: Int): Int = Mining.turnsFor(item, fresh(power), config)

    assert(Tiers.forall { (item, tier) =>
      at(item, tier) == math.round(swing.baseTurns + swing.turnsPerTier * tier).toInt
    })
    assert(at("silver ore", 4) > at("tin ore", 2))
    assert(at("tin ore", 2) > at("copper ore", 1))
    assert(at("silver ore", 6) < at("silver ore", 4))
    assertEquals(
      at("silver ore", 4) - at("silver ore", 6),
      math.round(2 * swing.turnsPerPower).toInt
    )
    assertEquals(at("stone", 1000), swing.minTurns)
    assertEquals(at("silver ore", 1000), swing.minTurns)
    assert(Tiers.forall { (item, _) =>
      List(0, 1, 2, 3, 4, 20, 1000).forall(power => at(item, power) >= swing.minTurns)
    })

    val lowSkill  = fresh(3)
    val highSkill = fresh(3).copy(skills = Skills.starting.copy(mining = Skill(99, 0)))
    assertEquals(
      Mining.turnsFor("iron ore", lowSkill, config),
      Mining.turnsFor("iron ore", highSkill, config)
    )
    assertEquals(
      Mining.turnsFor("iron ore", lowSkill.copy(mineLevel = 50), config),
      Mining.turnsFor("iron ore", lowSkill, config)
    )

  test("tier and power set fatigue chance"):
    // The shipped slope saturates at the shipped base chance, so the shape is checked against a
    // rested, over-leveled character where nothing clamps and the spread is visible.
    def sharp(power: Int): Character =
      fresh(power).copy(skills = Skills.starting.copy(mining = Skill(100, 0)))

    def at(item: String, power: Int): Double =
      Mining.fatigueChanceFor(item, sharp(power), config)

    assert(at("silver ore", 4) > at("tin ore", 2))
    assert(at("tin ore", 2) > at("copper ore", 1))
    assert(at("copper ore", 1) > at("stone", 0))
    assert(at("silver ore", 6) < at("silver ore", 4))
    assert(Tiers.forall { (item, _) =>
      List(0, 1, 2, 3, 4, 5, 50, 1000).forall { power =>
        val chance = at(item, power)
        chance >= 0 && chance <= 1
      }
    })

    val floored       = Mining.fatigueChanceFor("silver ore", fresh(1000), config)
    val expectedFloor =
      Mining.mineFatigueChance(fresh(1000), config) * mining.fatigueChance.minFactor
    assert(math.abs(floored - expectedFloor) < 1e-9)

    // At the shipped base chance hard ores saturate and stone does not.
    assertEquals(Mining.fatigueChanceFor("tin ore", fresh(2), config), 1.0)
    assertEquals(Mining.fatigueChanceFor("iron ore", fresh(3), config), 1.0)
    assertEquals(Mining.fatigueChanceFor("silver ore", fresh(4), config), 1.0)
    assert(Mining.fatigueChanceFor("stone", fresh(0), config) < 1.0)

    val under = fresh(3).copy(
      mineLevel = 20,
      skills = Skills.starting.copy(mining = Skill(1, 0))
    )
    val over = fresh(3).copy(
      mineLevel = 1,
      skills = Skills.starting.copy(mining = Skill(40, 0))
    )
    val underChance = Mining.fatigueChanceFor("iron ore", under, config)
    val overChance  = Mining.fatigueChanceFor("iron ore", over, config)
    assert(underChance != overChance)
    assert(overChance < underChance)
    assertEquals(
      Mining.turnsFor("iron ore", over, config),
      Mining.turnsFor("iron ore", under, config)
    )

  test("the shipped tier table"):
    val expected =
      List(
        "stone"      -> 2,
        "coal"       -> 2,
        "copper ore" -> 3,
        "tin ore"    -> 4,
        "iron ore"   -> 5,
        "silver ore" -> 6
      )
    val tierOf = Tiers.toMap
    val actual = expected.map { case (item, _) =>
      Mining.turnsFor(item, fresh(tierOf(item)), config)
    }
    assertEquals(actual, expected.map(_._2))

  // -- saves and config validation ------------------------------------------

  test("saves carry the starting tool and config validation holds"):
    Character.parse("""{"name":"Old","skills":{}}""") match
      case Left(error) => fail(error.message)
      case Right(old)  =>
        assertEquals(old.equipment.get(Character.MiningToolSlot), Some("stone pickaxe"))
    assertEquals(
      Character.createCharacter("New").equipment.get(Character.MiningToolSlot),
      Some("stone pickaxe")
    )

    // A tool may grant none, but no tool may grant less than none, and a slot still has to
    // accept the character's starting tool.
    val zeroTool = withEquipment(
      Json.obj(Character.MiningToolSlot -> Json.obj("stone pickaxe" -> Json.fromInt(0)))
    )
    assert(GameConfig.decode(zeroTool).isRight)

    val negativeTool = withEquipment(
      Json.obj(Character.MiningToolSlot -> Json.obj("stone pickaxe" -> Json.fromInt(-1)))
    )
    assert(GameConfig.decode(negativeTool).isLeft)

    val emptySlot = withEquipment(Json.obj(Character.MiningToolSlot -> Json.obj()))
    assert(GameConfig.decode(emptySlot).isLeft)

    val missingStart = withEquipment(
      Json.obj("Back" -> Json.obj("stone pickaxe" -> Json.fromInt(1)))
    )
    assert(GameConfig.decode(missingStart).isLeft)

    val unpriced = withMiningBands { bands =>
      bands.head.deepMerge(Json.obj("from" -> Json.obj("mithril" -> Json.fromInt(5)))) ::
        bands.tail
    }
    assert(GameConfig.decode(unpriced).isLeft)

    // Gating a whole band is legal: the floor simply cannot be cleared there until the power
    // is earned, which is the intended design.
    val gated = withMiningBands { bands =>
      Json.obj(
        "from" -> Json.obj("copper ore" -> Json.fromInt(1)),
        "to"   -> Json.obj("tin ore" -> Json.fromInt(1))
      ) :: bands.tail
    }
    assert(GameConfig.decode(gated).isRight)

    assertEquals(shipped.mining.materials.power("silver ore"), 4)

  // -- generateFloor only sees weights --------------------------------------

  test("generateFloor only sees weights"):
    withRandom { implicit r =>
      for
        single <- Schedule.generateFloor[IO](
          ListMap("stone" -> 3.0, "coal" -> 1.0),
          NodeCount(8, 8)
        )
        floors <- (1 to 800).toList.traverse { _ =>
          Schedule.generateFloor[IO](
            ListMap("stone" -> 6.0, "coal" -> 2.0),
            NodeCount(8, 8)
          )
        }
      yield
        assert(single.forall(stack => stack.item == "stone" || stack.item == "coal"))
        val total = floors.map(Character.totalItems).sum
        val stone = floors.map(_.find(_.item == "stone").fold(0)(_.count)).sum
        assert(math.abs(stone.toDouble / total - 0.75) < 0.02, s"was ${stone.toDouble / total}")
    }
