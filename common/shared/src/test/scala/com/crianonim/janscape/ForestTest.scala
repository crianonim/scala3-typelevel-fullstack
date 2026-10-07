package com.crianonim.janscape

import cats.effect.IO
import cats.effect.std.Random
import cats.syntax.all.*
import munit.CatsEffectSuite

/** The TypeScript janscape-forest.ts harness, check for check. The rolls a gather makes are passed
  * explicitly, so every branch is forced exactly the way the original forces it by queueing
  * Math.random.
  */
class ForestTest extends CatsEffectSuite:

  private val seed     = 20261007
  private val config   = GameConfig.default
  private val forest   = config.forest
  private val schedule = forest.schedule
  private val Deep     = 60
  private val Gather   = List("sticks", "berries")

  private def random: IO[Random[IO]]                       = Random.scalaUtilRandomSeedInt[IO](seed)
  private def withRandom[A](f: Random[IO] => IO[A]): IO[A] = random.flatMap(r => f(r))

  private def fresh(): Character = Character.createCharacter("Tester")

  private def resourcesAt(depth: Int): List[String] =
    Schedule.weightsAtLevel(depth, schedule).keys.toList

  private def debut(item: String): Option[Int] =
    (1 to Deep).find(depth => resourcesAt(depth).contains(item))

  private def at(depth: Int, level: Int = 1): Character =
    val character = fresh()
    character.copy(
      skills = character.skills.updated(SkillName.Forestry, Skill(level, 0)),
      forestLevel = depth
    )

  private def glade(nodes: List[ItemStack], depth: Int = 1, level: Int = 1): Character =
    at(depth, level).copy(forestNodes = nodes)

  private def rolls(
      fatigueCheck: Int = 1,
      yielding: Double = 0.5,
      path: Double = 1.0,
      tiring: Double = 1.0
  ): Forest.HarvestRolls =
    Forest.HarvestRolls(fatigueCheck, yielding, path, tiring)

  private def gather(
      character: Character,
      item: String,
      withRolls: Forest.HarvestRolls = rolls()
  ): HarvestOutcome =
    Forest.harvestNodeWith(character, item, config, withRolls) match
      case Left(error)  => fail(error.message)
      case Right(value) => value

  private def turnsOf(item: String): Int =
    Forest.turnsFor(item, config) match
      case Left(error)  => fail(error.message)
      case Right(value) => value

  private def rangeOf(item: String, character: Character): YieldRange =
    Forest.yieldRange(item, character, config) match
      case Left(error)  => fail(error.message)
      case Right(value) => value

  private def chanceOf(item: String, character: Character): Double =
    Forest.fatigueChanceFor(item, character, config) match
      case Left(error)  => fail(error.message)
      case Right(value) => value

  private def round3(value: Double): Double = math.round(value * 1000).toDouble / 1000

  test("depth schedule"):
    assert(Gather.forall(item => debut(item) == Some(1)), Gather.map(debut).mkString(","))
    assertEquals(debut("ash tree"), Some(5))
    assertEquals(debut("mushrooms"), Some(10))
    assert((1 to 4).forall(depth => !resourcesAt(depth).contains("ash tree")))
    assert(
      (1 to 4).forall(depth => resourcesAt(depth).forall(Gather.contains))
    )

    val missing = (1 to Deep).toList.flatMap { depth =>
      resourcesAt(depth)
        .filterNot(Forest.canHarvest(_, config))
        .map(item => s"$depth:$item")
    }
    assertEquals(missing, Nil)

    assertEquals(Schedule.weightsAtLevel(Deep, schedule).keys.size, 4)

  test("glades"):
    withRandom { implicit r =>
      for
        floors <- (1 to 300).toList.traverse(_ => Forest.enterForest[IO](fresh(), 1, config))
        deep   <- Forest.enterForest[IO](fresh(), Deep, config)
      yield
        val sizes = floors.map(f => Character.totalItems(f.forestNodes))
        assert(sizes.min >= forest.glade.min, s"was ${sizes.min}")
        assert(sizes.max <= forest.glade.max, s"was ${sizes.max}")
        assert(sizes.distinct.size > 10, s"${sizes.min}-${sizes.max}")
        assert(sizes.forall(_ >= 1))
        assert(
          deep.forestNodes.forall(stack => forest.resources.contains(stack.item)),
          deep.forestNodes.map(_.item).mkString(",")
        )
    }

  test("entry"):
    val plain = fresh()
    assertEquals(Forest.entryDepths(plain), List(1))

    val shortcut = plain.copy(unlockedForestLevels = List(1, 10, 5))
    assertEquals(Forest.entryDepths(shortcut), List(1, 5, 10))

    withRandom { implicit r =>
      for entered <- Forest.enterForest[IO](shortcut, 10, config)
      yield
        assertEquals(entered.forestLevel, 10)
        assert(!entered.hasForestPath)
        assertEquals(entered.mineLevel, plain.mineLevel)
        assertEquals(entered.mineNodes, Nil)
        assert(entered.forestNodes.nonEmpty)
        assertEquals(plain.forestLevel, Character.StartingForestLevel)
        assert(!plain.hasForestPath)
        assertEquals(plain.forestNodes, Nil)
    }

  test("depth factor"):
    val depth                   = 20
    def factor(level: Int): Int = Forest.forestDepthFactor(at(depth, level), forest)

    assertEquals(factor(depth), 0)
    assertEquals(factor(depth + 4), 0)
    assertEquals(factor(depth + 5), 1)
    assertEquals(factor(depth - 5), -1)
    assertEquals(factor(depth - 10), -2)
    assertEquals(factor(depth + 500), forest.depthFactor.max)
    assertEquals(factor(depth - 500), -forest.depthFactor.max)
    assertEquals(factor(depth - 1), 0)

  test("yield"):
    val base = rangeOf("sticks", at(1))
    assertEquals(base.min, 2)
    assertEquals(base.max, 4)

    val ahead      = rangeOf("sticks", at(depth = 20, level = 20 + 5 * forest.depthFactor.max))
    val aheadShift =
      (forest.yieldFactor.perDepthFactorPoint * forest.depthFactor.max).toInt
    assertEquals(ahead.min, base.min + aheadShift)

    val behind = rangeOf("sticks", at(depth = 20, level = 10))
    assertEquals(behind.max, (4 - forest.yieldFactor.perDepthFactorPoint * 2).toInt)
    assert(behind.min >= forest.yieldFactor.min, s"was ${behind.min}")
    assert(behind.max >= behind.min)

    val tree = rangeOf("ash tree", at(depth = 5))
    assertEquals(tree.min, 2)
    assertEquals(tree.max, 10)

    // The button promises a range; the roll has to stay inside it.
    val character = glade(List(ItemStack("sticks", 500)))
    val amounts   = List(0.0, 0.5, 0.999).map { yieldRoll =>
      gather(character, "sticks", rolls(yielding = yieldRoll)).amount
    }
    assert(
      amounts.forall(amount => amount >= base.min && amount <= base.max),
      amounts.mkString(",")
    )

  test("turns and fatigue"):
    assertEquals(turnsOf("sticks"), forest.resources("sticks").turns)
    assertEquals(turnsOf("ash tree"), forest.resources("ash tree").turns)
    // Turns have no character in their signature at all, so Forestry can never price them.

    val even  = round3(chanceOf("ash tree", at(depth = 20, level = 20)))
    val ahead =
      round3(chanceOf("ash tree", at(depth = 20, level = 20 + 5 * forest.depthFactor.max)))
    val behind = round3(chanceOf("ash tree", at(depth = 20, level = 10)))
    assert(ahead < even, s"$ahead < $even")
    assert(behind > even, s"$behind > $even")
    assert(List(even, ahead, behind).forall(c => c >= 0 && c <= 1))

    val tiered = round3(chanceOf("mushrooms", at(depth = 20, level = 20)))
    assert(tiered > even, s"$tiered > $even")

  test("gathering"):
    val character = glade(List(ItemStack("sticks", 1), ItemStack("berries", 1)))

    // Fatigue at the cap fails every effort check, so this is the strongest statement that
    // gathering skips it: a character who could not chop anything can still feed themselves.
    val tired = gather(
      character.copy(fatigue = config.play.fatigue.max),
      "sticks",
      rolls(yielding = 0.5, path = 0.99)
    )
    assert(!tired.failed)
    assertEquals(tired.character.turn, forest.resources("sticks").turns)
    assert(tired.amount >= 2 && tired.amount <= 4)
    assertEquals(tired.character.skills.forestry.xp, tired.turns)
    assertEquals(tired.fatigue, 0)
    assertEquals(tired.character.fatigue, config.play.fatigue.max)
    assertEquals(Schedule.nodeCount(tired.character.forestNodes, "sticks"), 0)
    assertEquals(Schedule.nodeCount(tired.character.forestNodes, "berries"), 1)
    assert(!tired.gladeCleared)
    assertEquals(Character.heldAmount(tired.character, "sticks"), tired.amount)
    assert(!tired.foundPath)
    assert(!tired.character.hasForestPath)

  test("chopping"):
    val oneTree = glade(List(ItemStack("ash tree", 1)), depth = 5)

    // Roll 1 against fatigue at the cap: the effort check cannot pass.
    val blocked = gather(
      oneTree.copy(fatigue = config.play.fatigue.max),
      "ash tree",
      rolls(fatigueCheck = 1)
    )
    assert(blocked.failed)
    assertEquals(blocked.character.turn, forest.resources("ash tree").turns)
    assertEquals(blocked.amount, 0)
    assertEquals(blocked.character.skills.forestry.xp, 0)
    assertEquals(Schedule.nodeCount(blocked.character.forestNodes, "ash tree"), 1)
    assert(!blocked.foundPath)
    assert(!blocked.gladeCleared)

    // Draw 0.5 as the effort check: floor(0.5 * 100) + 1 = 51, past a rested character's
    // threshold of 0.
    val landed = gather(
      oneTree,
      "ash tree",
      rolls(fatigueCheck = 51, yielding = 0.99, path = 0.99, tiring = 0.99)
    )
    assert(!landed.failed)
    assert(landed.gladeCleared)
    assert(landed.foundPath)
    assertEquals(Schedule.nodeCount(landed.character.forestNodes, "ash tree"), 0)

    val tired = gather(
      oneTree,
      "ash tree",
      rolls(fatigueCheck = 51, yielding = 0.0, path = 0.0, tiring = 0.0)
    )
    assertEquals(tired.fatigue, forest.chop.fatigue)
    assertEquals(tired.character.fatigue, forest.chop.fatigue)

    val notFound = gather(
      glade(List(ItemStack("ash tree", 2)), depth = 5),
      "ash tree",
      rolls(fatigueCheck = 51, yielding = 0.99, path = 0.99, tiring = 0.99)
    )
    assert(!notFound.foundPath)
    assert(!notFound.gladeCleared)

  test("paths and going deeper"):
    val lastStick = glade(List(ItemStack("sticks", 1)))
    val path      = gather(lastStick, "sticks", rolls(yielding = 0.5, path = 0.0))
    assert(path.foundPath)
    assert(path.character.hasForestPath)

    val held        = Character.setForestPath(fresh(), true)
    val alreadyHeld = gather(
      held.copy(forestNodes = List(ItemStack("sticks", 2))),
      "sticks",
      rolls(yielding = 0.5)
    )
    assert(!alreadyHeld.foundPath)
    assert(alreadyHeld.character.hasForestPath)

    assert(!Forest.canGoDeeper(fresh()))
    assert(Forest.canGoDeeper(held))

    withRandom { implicit r =>
      for
        entered   <- Forest.enterForest[IO](fresh(), 1, config)
        deeper    <- Forest.goDeeper[IO](Character.setForestPath(entered, true), config)
        cleared   <- Forest.enterForest[IO](held, 1, config)
        entered4  <- Forest.enterForest[IO](fresh(), 4, config)
        milestone <- Forest.goDeeper[IO](
          Character.setForestPath(entered4, true),
          config
        )
        again <- Forest.enterForest[IO](
          fresh().copy(unlockedForestLevels = List(5)),
          4,
          config
        )
        againDeeper <- Forest.goDeeper[IO](Character.setForestPath(again, true), config)
      yield
        assert(!cleared.hasForestPath)

        assertEquals(deeper.depth, 2)
        assertEquals(deeper.character.forestLevel, 2)
        assert(!deeper.character.hasForestPath)
        assertEquals(deeper.turns, forest.path.deeperTurns)
        assertEquals(deeper.character.turn, forest.path.deeperTurns)
        assertEquals(deeper.fatigue, forest.path.deeperFatigue)
        assertEquals(deeper.character.fatigue, forest.path.deeperFatigue)
        assert(deeper.character.forestNodes.nonEmpty)
        assert(
          Schedule.nodeCount(deeper.character.forestNodes, "sticks") <= forest.glade.max
        )
        assert(deeper.unlocked.isEmpty)
        assertEquals(deeper.character.unlockedForestLevels, Nil)

        assert(Forest.isFastTravelDepth(5, forest))
        assert(!Forest.isFastTravelDepth(6, forest))
        assert(Forest.isFastTravelDepth(0, forest))

        assertEquals(milestone.unlocked, Some(5))
        assertEquals(milestone.character.unlockedForestLevels, List(5))
        assertEquals(againDeeper.character.unlockedForestLevels, List(5))
    }

    val even  = Forest.pathChanceFor(at(depth = 20, level = 20), config)
    val ahead =
      Forest.pathChanceFor(at(depth = 20, level = 20 + 5 * forest.depthFactor.max), config)
    val behind = Forest.pathChanceFor(at(depth = 20, level = 10), config)
    assert(ahead > even, s"$ahead > $even")
    assert(behind < even, s"$behind < $even")
    assert(List(even, ahead, behind).forall(c => c >= 0 && c <= 1))

  test("guards"):
    val one = glade(List(ItemStack("sticks", 1)))

    Forest.harvestNodeWith(one, "berries", config, rolls()) match
      case Right(_)    => fail("a missing node was accepted")
      case Left(error) => assert(error.message.contains("No berries left"), error.message)

    Forest.harvestNodeWith(one, "coal", config, rolls()) match
      case Right(_)    => fail("an unknown resource was accepted")
      case Left(error) =>
        assert(error.message.contains("not something the forest produces"), error.message)

    Forest.turnsFor("coal", config) match
      case Right(value) => fail(s"turns for coal: $value")
      case Left(error)  =>
        assert(error.message.contains("not something the forest produces"), error.message)

  test("saves"):
    val legacy = Character.parse(
      """{"name":"Tester",
        |"skills":{"mining":{"level":3,"xp":80},"combat":{"level":1,"xp":0},"smithing":{"level":1,"xp":0}},
        |"inventory":[],"equipment":{"Mining Tool":"stone pickaxe"},"buildings":[],
        |"turn":4,"fatigue":1,"mineLevel":7,"hasMineLadder":true,"unlockedMineLevels":[5],
        |"mineNodes":[{"item":"stone","count":3}]}""".stripMargin
    )
    legacy match
      case Left(error) => fail(error.message)
      case Right(old)  =>
        assertEquals(old.forestLevel, Character.StartingForestLevel)
        assert(!old.hasForestPath)
        assertEquals(old.unlockedForestLevels, Nil)
        assertEquals(old.forestNodes, Nil)
        assertEquals(old.mineLevel, 7)
        assert(old.hasMineLadder)
        assertEquals(Schedule.nodeCount(old.mineNodes, "stone"), 3)
        assertEquals(old.skills.forestry.level, 1)
        assertEquals(old.skills.forestry.xp, 0)

    withRandom { implicit r =>
      for entered <- Forest.enterForest[IO](fresh(), 1, config)
      yield
        val round = Character.parse(Character.encode(entered)) match
          case Left(error)  => fail(error.message)
          case Right(value) => value
        assertEquals(round.forestLevel, 1)
        assert(round.forestNodes.nonEmpty)
        assertEquals(round.skills.forestry.level, 1)
    }
