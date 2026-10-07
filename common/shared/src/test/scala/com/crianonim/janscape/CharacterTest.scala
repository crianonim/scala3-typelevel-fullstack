package com.crianonim.janscape

import munit.FunSuite

class CharacterTest extends FunSuite:

  test("a fresh character round-trips through encode and parse"):
    val character = Character.createCharacter("Ellis")
    assertEquals(Character.parse(Character.encode(character)), Right(character))

  test("a minimal save fills every field the zod original defaults"):
    val parsed = Character.parse("""{"name":"Old","skills":{}}""")
    parsed match
      case Left(error)      => fail(error.message)
      case Right(character) =>
        assertEquals(character.name, "Old")
        assertEquals(character.skills, Skills.starting)
        assertEquals(character.inventory, Nil)
        assertEquals(character.equipment, Character.StartingEquipment)
        assertEquals(character.buildings, Nil)
        assertEquals(character.turn, 0)
        assertEquals(character.fatigue, 0)
        assertEquals(character.mineLevel, 1)
        assertEquals(character.hasMineLadder, false)
        assertEquals(character.unlockedMineLevels, Nil)
        assertEquals(character.mineNodes, Nil)
        assertEquals(character.forestLevel, 1)
        assertEquals(character.hasForestPath, false)
        assertEquals(character.unlockedForestLevels, Nil)
        assertEquals(character.forestNodes, Nil)

  test("a save written before the forest existed still loads"):
    val parsed = Character.parse(
      """{"name":"Ellis","skills":{"mining":{"level":5,"xp":160}},
         |"inventory":[{"item":"stone","count":12}],"turn":40}""".stripMargin
    )
    parsed match
      case Left(error)      => fail(error.message)
      case Right(character) =>
        assertEquals(character.skills.mining.level, 5)
        assertEquals(character.skills.forestry, Skill.starting)
        assertEquals(character.inventory, List(ItemStack("stone", 12)))
        assertEquals(character.forestLevel, 1)

  test("duplicate stacks, unlocks and buildings collapse on parse"):
    val parsed = Character.parse(
      """{"name":"Ellis","skills":{},
         |"inventory":[{"item":"stone","count":3},{"item":"stone","count":4},{"item":"stone","count":0}],
         |"buildings":["Furnace","Furnace"],
         |"unlockedMineLevels":[5,2,2,1],"unlockedForestLevels":[3,1]}""".stripMargin
    )
    parsed match
      case Left(error)      => fail(error.message)
      case Right(character) =>
        assertEquals(character.inventory, List(ItemStack("stone", 7)))
        assertEquals(character.buildings, List("Furnace"))
        assertEquals(character.unlockedMineLevels, List(1, 2, 5))
        assertEquals(character.unlockedForestLevels, List(1, 3))

  test("an out-of-range save is rejected rather than repaired"):
    val parsed = Character.parse("""{"name":"Ellis","skills":{},"fatigue":5000}""")
    parsed match
      case Right(_)    => fail("expected a rejected save")
      case Left(error) =>
        assert(error.message.contains("fatigue"), error.message)

  test("not-JSON is its own error, distinct from an invalid save"):
    assertEquals(Character.parse("nonsense"), Left(GameError.NotJson))

  test("name validation mirrors the zod original's messages"):
    assertEquals(Character.validateName("   "), Left("Name is required."))
    assertEquals(Character.validateName("a" * 25), Left("Name must be 24 characters or fewer."))
    assertEquals(
      Character.validateName("!!"),
      Left("Name must contain at least one letter or number.")
    )
    assertEquals(Character.validateName("  Ellis  "), Right("Ellis"))

  test("spending an item drops the stack once emptied and never goes negative"):
    val inventory = List(ItemStack("stone", 5), ItemStack("coal", 2))
    assertEquals(Character.removeItem(inventory, "stone", 5), List(ItemStack("coal", 2)))
    assertEquals(Character.removeItem(inventory, "stone", 99), List(ItemStack("coal", 2)))
    assertEquals(
      Character.removeItem(inventory, "stone", 3),
      List(ItemStack("stone", 2), ItemStack("coal", 2))
    )
    assertEquals(Character.removeItem(inventory, "silver", 1), inventory)

  test("adding an item merges into an existing stack"):
    val inventory = List(ItemStack("stone", 5), ItemStack("coal", 2))
    assertEquals(
      Character.addItem(inventory, "coal", 3),
      List(ItemStack("stone", 5), ItemStack("coal", 5))
    )
    assertEquals(Character.addItem(inventory, "iron ore", 4), inventory :+ ItemStack("iron ore", 4))

  test("turns floor and clamp at zero"):
    val character = Character.createCharacter("Ellis")
    assertEquals(Character.addTurns(character, 4.6).turn, 4)
    assertEquals(Character.addTurns(character.copy(turn = 2), -9.9).turn, 0)

  test("fatigue clamps to the configured cap and back to zero"):
    val character = Character.createCharacter("Ellis").copy(fatigue = 8)
    assertEquals(Character.addFatigue(character, 5, 10).fatigue, 10)
    assertEquals(Character.addFatigue(character, -99, 10).fatigue, 0)
