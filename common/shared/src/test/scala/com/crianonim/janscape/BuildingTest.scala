package com.crianonim.janscape

import munit.CatsEffectSuite

import scala.collection.immutable.ListMap

/** The TypeScript janscape-building.ts harness, check for check. Building is pure, so no random
  * source is needed anywhere in this suite.
  */
class BuildingTest extends CatsEffectSuite:

  private val config     = GameConfig.default
  private val buildables = config.building.buildables

  private def withItems(items: List[ItemStack]): Character =
    Character.createCharacter("Tester").copy(inventory = items)

  private def stack(item: String, count: Int): ItemStack = ItemStack(item, count)

  private def buildOrDie(character: Character, name: String): BuildOutcome =
    Building.build(character, name, config) match
      case Left(error)  => fail(error.message)
      case Right(value) => value

  test("the buildables are declared"):
    assertEquals(buildables.keys.toList, List("Furnace", "Basic Workbench", "Stone Anvil"))
    assertEquals(
      buildables.get("Furnace"),
      Some(Buildable(ListMap("stone" -> 25), 50))
    )
    assertEquals(
      buildables.get("Basic Workbench"),
      Some(Buildable(ListMap("stone" -> 10), 10))
    )
    assertEquals(
      buildables.get("Stone Anvil"),
      Some(Buildable(ListMap("stone" -> 20), 10))
    )

  test("nothing is built to start"):
    assertEquals(Character.createCharacter("Tester").buildings, Nil)

    val legacy = Character.parse("""{"name":"Old","skills":{}}""")
    legacy match
      case Left(error) => fail(error.message)
      case Right(old)  => assertEquals(old.buildings, Nil)

    val deduped = Character.parse(
      """{"name":"Old","skills":{},"buildings":["Furnace","Furnace","Stone Anvil"]}"""
    )
    deduped match
      case Left(error)  => fail(error.message)
      case Right(dupes) => assertEquals(dupes.buildings, List("Furnace", "Stone Anvil"))

    val round = Character.parse(Character.encode(Character.createCharacter("X")))
    round match
      case Left(error)   => fail(error.message)
      case Right(loaded) => assertEquals(loaded.buildings, Nil)

  test("canBuild gates on materials and one-of-each"):
    val poor = withItems(List(stack("stone", 9)))
    assert(!Building.canBuild(poor, "Basic Workbench", buildables))

    val rich = withItems(List(stack("stone", 10)))
    assert(Building.canBuild(rich, "Basic Workbench", buildables))
    assert(!Building.canBuild(rich, "Loom", buildables))

    val already = rich.copy(buildings = List("Basic Workbench"))
    assert(!Building.canBuild(already, "Basic Workbench", buildables))

  test("building spends, records, and pays turns"):
    val rich    = withItems(List(stack("stone", 100)))
    val before  = rich.skills
    val outcome = buildOrDie(rich, "Furnace")

    assert(outcome.character.buildings.contains("Furnace"))
    assertEquals(outcome.character.buildings.length, 1)
    assertEquals(Character.heldAmount(outcome.character, "stone"), 75)
    assertEquals(outcome.turns, 50)
    assertEquals(outcome.character.turn, rich.turn + 50)
    assertEquals(outcome.character.skills, before)
    assertEquals(outcome.character.fatigue, rich.fatigue)
    assertEquals(outcome.name, "Furnace")

  test("building is one of each and permanent"):
    val rich = withItems(List(stack("stone", 200)))
    val once = buildOrDie(rich, "Stone Anvil").character
    assert(Building.isBuilt(once, "Stone Anvil"))
    assert(Building.build(once, "Stone Anvil", config).isLeft)
    assert(Building.canBuild(once, "Furnace", buildables))

  test("build refuses what it cannot do"):
    val rich = withItems(List(stack("stone", 100)))
    assert(Building.build(rich, "Loom", config).isLeft)
    assert(
      Building.build(withItems(List(stack("stone", 9))), "Basic Workbench", config).isLeft
    )
