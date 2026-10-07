package com.crianonim.janscape

import cats.effect.IO
import cats.effect.std.Random
import cats.syntax.all.*
import munit.CatsEffectSuite

import scala.collection.immutable.ListMap

/** The TypeScript janscape-regression.ts harness, check for check: the depth schedule, the
  * largest-remainder floor split, the node mechanics, entry and fast travel, and the save shape.
  */
class RegressionTest extends CatsEffectSuite:

  private val seed                   = 20261007
  private def random: IO[Random[IO]] = Random.scalaUtilRandomSeedInt[IO](seed)

  private val config           = GameConfig.default
  private val materials        = config.mining.materials
  private val Ores             = List("copper ore", "tin ore", "iron ore", "silver ore")
  private val Deep             = 60
  private def fresh: Character = Character.createCharacter("Tester")

  private def oresAt(level: Int): List[String] =
    Schedule.weightsAtLevel(level, materials.schedule).keys.filter(Ores.contains).toList

  private def debut(item: String): Option[Int] =
    (1 to Deep).find(level => oresAt(level).contains(item))

  private def itemNames(floor: List[ItemStack]): List[String] = floor.map(_.item).sorted

  private def scheduleNames(level: Int): List[String] =
    Schedule.weightsAtLevel(level, materials.schedule).keys.toList.sorted

  // -- depth schedule (pure) ------------------------------------------------

  test("ore debuts land on their band boundaries"):
    assertEquals(debut("copper ore"), Some(5))
    assertEquals(debut("tin ore"), Some(10))
    assertEquals(debut("iron ore"), Some(15))
    assertEquals(debut("silver ore"), Some(20))

  test("no ore before level 5"):
    assert((1 to 4).forall(level => oresAt(level).isEmpty))

  test("faded ores are gone by the level that replaces them"):
    assert(!List(15, 20, 40).exists(level => oresAt(level).contains("copper ore")))
    assert(!List(20, 30, 40).exists(level => oresAt(level).contains("tin ore")))

  test("never more than two ores on one level"):
    assert((1 to Deep).forall(level => oresAt(level).length <= 2))

  test("stone is heaviest and the base pair present at every level"):
    val notHeaviest =
      (1 to Deep).toList.filterNot { level =>
        val weights = Schedule.weightsAtLevel(level, materials.schedule)
        weights.maxBy(_._2)._1 == "stone"
      }
    val missingBase =
      (1 to Deep).toList.filter { level =>
        val weights = Schedule.weightsAtLevel(level, materials.schedule)
        !weights.contains("stone") || !weights.contains("coal")
      }
    assertEquals(notHeaviest, Nil)
    assertEquals(missingBase, Nil)

  test("bands line up with fast travel milestones"):
    assert(List(5, 10, 15, 20).forall { level =>
      Mining.isFastTravelLevel(level, config.mining) &&
      Schedule.bandOf(level, materials.schedule) == level / materials.bandLevels
    })

  test("the deepest band holds"):
    val at30 = Schedule.weightsAtLevel(30, materials.schedule)
    val at60 = Schedule.weightsAtLevel(60, materials.schedule)
    assertEquals(at30, at60)

  // -- largest remainder (F) ------------------------------------------------

  test("an even split divides cleanly"):
    withRandom { implicit r =>
      Schedule
        .generateFloor[IO](ListMap("a" -> 1.0, "b" -> 1.0), NodeCount(10, 10))
        .map(floor => assertEquals(floor, List(ItemStack("a", 5), ItemStack("b", 5))))
    }

  test("the odd node goes to the larger share"):
    withRandom { implicit r =>
      Schedule
        .generateFloor[IO](ListMap("a" -> 3.0, "b" -> 1.0), NodeCount(7, 7))
        .map(floor => assertEquals(floor, List(ItemStack("a", 5), ItemStack("b", 2))))
    }

  test("a zero weight is dropped, not shown as empty"):
    withRandom { implicit r =>
      Schedule
        .generateFloor[IO](ListMap("a" -> 5.0, "b" -> 0.0), NodeCount(4, 4))
        .map(floor => assertEquals(floor, List(ItemStack("a", 4))))
    }

  test("a single material takes the whole floor"):
    withRandom { implicit r =>
      Schedule
        .generateFloor[IO](ListMap("a" -> 1.0), NodeCount(3, 3))
        .map(floor => assertEquals(floor, List(ItemStack("a", 3))))
    }

  test("floors stay within the configured range and reach both ends"):
    withRandom { implicit r =>
      val weights = ListMap("a" -> 1.0, "b" -> 1.0)
      (1 to 3000).toList
        .traverse(_ => Schedule.generateFloor[IO](weights, config.mining.nodes))
        .map { floors =>
          val counts = floors.map(Character.totalItems)
          assert(
            counts.forall(n => n >= config.mining.nodes.min && n <= config.mining.nodes.max)
          )
          assertEquals(counts.min, config.mining.nodes.min)
          assertEquals(counts.max, config.mining.nodes.max)
        }
    }

  // -- node mechanics (F) ---------------------------------------------------

  test("entering rolls a floor and drops the ladder"):
    withRandom { implicit r =>
      Mining.enterMine[IO](fresh, 1, config).map { start =>
        assert(Character.totalItems(start.mineNodes) >= config.mining.nodes.min)
        assertEquals(start.hasMineLadder, false)
      }
    }

  test("a landed swing removes a node, adds an item, grants XP and costs turns"):
    withRandom { implicit r =>
      for
        start <- Mining.enterMine[IO](fresh, 1, config)
        item = start.mineNodes.head.item
        hit <- Mining.mineNode[IO](start.copy(fatigue = 0), item, config)
      yield hit match
        case Left(error)    => fail(error.message)
        case Right(outcome) =>
          assert(!outcome.failed)
          assertEquals(
            Schedule.nodeCount(outcome.character.mineNodes, item),
            Schedule.nodeCount(start.mineNodes, item) - 1
          )
          assertEquals(Character.heldAmount(outcome.character, item), 1)
          assert(outcome.xp > 0)
          assert(outcome.character.skills.mining.xp > 0)
          assertEquals(
            outcome.character.turn,
            start.turn + Mining.turnsFor(item, start, config)
          )
    }

  test("a capped swing misses and costs only the turns"):
    withRandom { implicit r =>
      for
        start <- Mining.enterMine[IO](fresh, 1, config)
        item = start.mineNodes.head.item
      yield
        val capped = start.copy(fatigue = config.play.fatigue.max)
        val miss   = Mining.mineNodeWith(
          capped,
          item,
          config,
          Mining.MineRolls(fatigueCheck = 1, ladder = 0.0, tiring = 0.0)
        ) match
          case Left(error)    => fail(error.message)
          case Right(outcome) => outcome

        assert(miss.failed)
        assertEquals(
          Schedule.nodeCount(miss.character.mineNodes, item),
          Schedule.nodeCount(capped.mineNodes, item)
        )
        assertEquals(miss.character.skills.mining.xp, capped.skills.mining.xp)
        assertEquals(miss.character.inventory, Nil)
        assertEquals(miss.character.fatigue, capped.fatigue)
        assertEquals(miss.character.turn, capped.turn + Mining.turnsFor(item, capped, config))
    }

  test("clearing the floor leaves a ladder"):
    withRandom { implicit r =>
      for
        start   <- Mining.enterMine[IO](fresh, 1, config)
        cleared <- clearFloor(start, 200)
      yield
        assertEquals(Character.totalItems(cleared.mineNodes), 0)
        assertEquals(cleared.hasMineLadder, true)
    }

  test("descending goes deeper, costs turns and fatigue, and consumes the ladder"):
    withRandom { implicit r =>
      for
        start   <- Mining.enterMine[IO](fresh, 1, config)
        cleared <- clearFloor(start, 200)
        withLadder = Character.setMineLadder(cleared, true)
        down <- Mining.descend[IO](withLadder, config)
      yield
        assertEquals(down.character.mineLevel, withLadder.mineLevel + 1)
        assertEquals(down.character.hasMineLadder, false)
        assert(Character.totalItems(down.character.mineNodes) >= config.mining.nodes.min)
        assertEquals(down.turns, config.mining.ladder.descendTurns)
        assertEquals(down.fatigue, config.mining.ladder.descendFatigue)
        assertEquals(Mining.canDescend(fresh), false)
        assertEquals(Mining.canDescend(withLadder), true)
    }

  /** Mine the floor until it is empty or every remaining node is beyond the character's power.
    * Fatigue is reset each swing so the loop tests the node rules rather than the fatigue economy.
    */
  private def clearFloor(character: Character, guard: Int)(using r: Random[IO]): IO[Character] =
    if guard <= 0 || Character.totalItems(character.mineNodes) == 0 then IO.pure(character)
    else
      val item = character.mineNodes.head.item
      Mining.mineNode[IO](character.copy(fatigue = 0), item, config).flatMap {
        case Left(error)    => IO.raiseError(new AssertionError(error.message))
        case Right(outcome) => clearFloor(outcome.character, guard - 1)
      }

  // -- entry and fast travel ------------------------------------------------

  test("entry starts at level 1 and unlocks sort and dedupe"):
    assertEquals(Mining.entryLevels(fresh), List(1))
    val unlocked = Character.addMineUnlock(Character.addMineUnlock(fresh, 10), 5)
    assertEquals(Mining.entryLevels(unlocked), List(1, 5, 10))

  test("unlock milestones are multiples of everyLevels"):
    assert(Mining.isFastTravelLevel(5, config.mining))
    assert(Mining.isFastTravelLevel(10, config.mining))
    assert(!Mining.isFastTravelLevel(7, config.mining))

  test("descending onto a milestone reports and records the unlock"):
    withRandom { implicit r =>
      val before = fresh.copy(mineLevel = 9, hasMineLadder = true)
      Mining.descend[IO](before, config).map { down =>
        assertEquals(down.level, 10)
        assertEquals(down.unlocked, Some(10))
        assert(down.character.unlockedMineLevels.contains(10))
      }
    }

  test("descending between milestones reports none"):
    withRandom { implicit r =>
      val before = fresh.copy(mineLevel = 6, hasMineLadder = true)
      Mining.descend[IO](before, config).map { down =>
        assertEquals(down.unlocked, None)
        assertEquals(down.character.unlockedMineLevels, Nil)
      }
    }

  // -- save shape -----------------------------------------------------------

  test("a character round-trips through JSON"):
    assertEquals(Character.parse(Character.encode(fresh)), Right(fresh))

  test("a legacy save loads with empty mine state"):
    val parsed = Character.parse("""{"name":"Old","skills":{}}""")
    parsed match
      case Left(error)      => fail(error.message)
      case Right(character) =>
        assertEquals(character.mineNodes, Nil)
        assertEquals(character.mineLevel, 1)
        assertEquals(character.hasMineLadder, false)

  test("a legacy save's inventory still counts"):
    val parsed =
      Character.parse("""{"name":"Old","skills":{},"inventory":[{"item":"stone","count":2}]}""")
    parsed match
      case Left(error)      => fail(error.message)
      case Right(character) => assertEquals(Character.heldAmount(character, "stone"), 2)

  private def withRandom[A](f: Random[IO] => IO[A]): IO[A] =
    random.flatMap(f)
