package com.crianonim.janscape

import munit.FunSuite

import scala.collection.immutable.ListMap

class GameConfigTest extends FunSuite:

  test("embedded config parses and validates"):
    assertEquals(GameConfig.parse(GameConfigJson.raw), Right(GameConfig.default))

  test("defaults match the config's own numbers"):
    val config = GameConfig.default
    assertEquals(config.play.fatigue.max, 10)
    assertEquals(config.play.rest.turns, 10)
    assertEquals(config.skills.xpPerLevelSquared, 10.0)
    assertEquals(config.mining.swing.baseTurns, 2.0)
    assertEquals(config.mining.nodes.max, 25)
    assertEquals(config.mining.materials.power("iron ore"), 3)
    assertEquals(config.crafting.recipes("bronze axe").turns, 10)
    assertEquals(config.equipment.slots("Forestry Tool")("stone axe"), 1)
    assertEquals(config.building.buildables("Furnace").turns, 50)
    assertEquals(config.forest.glade.min, 10)
    assertEquals(config.forest.resources("ash tree").tier, 1)
    assertEquals(config.forest.chop.fatigue, 1)

  test("material power looks up a material and misses an unknown one"):
    val materials = GameConfig.default.mining.materials
    assertEquals(GameConfig.miningPowerFor("copper ore", materials), Some(1))
    assertEquals(GameConfig.miningPowerFor("wood", materials), None)

  test("a material the schedule uses but power omits is rejected"):
    assertInvalid("every material in a band needs a power") { config =>
      config.copy(mining =
        config.mining.copy(materials =
          config.mining.materials.copy(power = config.mining.materials.power.removed("iron ore"))
        )
      )
    }

  test("a recipe with no inputs is rejected"):
    assertInvalid("a recipe needs at least one input") { config =>
      config.copy(crafting =
        config.crafting.copy(recipes =
          config.crafting.recipes.updated("copper bar", Recipe(1, ListMap.empty, turns = 1))
        )
      )
    }

  test("fatigue max above what a full bar can overcome is rejected"):
    assertInvalid("max^exponent must be <= rollMax") { config =>
      config.copy(play =
        config.play.copy(fatigue = FatigueConfig(max = 20, rollMax = 100, exponent = 2))
      )
    }

  test("an item in two slots is rejected"):
    assertInvalid("an item can only belong to one slot") { config =>
      config.copy(equipment =
        EquipmentConfig(
          ListMap(
            "Mining Tool"   -> ListMap("stone pickaxe" -> 1),
            "Forestry Tool" -> ListMap("stone pickaxe" -> 1)
          )
        )
      )
    }

  test("a forest resource with no band is rejected"):
    assertInvalid("every resource needs a place in a band") { config =>
      config.copy(forest =
        config.forest.copy(resources =
          config.forest.resources.updated(
            "bamboo",
            ForestResourceConfig(tier = 0, turns = 1, `yield` = YieldConfig(1, 2), tiring = false)
          )
        )
      )
    }

  test("a forest band naming an unconfigured resource is rejected"):
    assertInvalid("every resource in a band needs an entry") { config =>
      config.copy(forest =
        config.forest.copy(resources = config.forest.resources.removed("mushrooms"))
      )
    }

  test("a not-a-json config reports NotJson"):
    assertEquals(GameConfig.parse("nonsense"), Left(GameError.NotJson))

  /** The TypeScript zod original's refinements, checked by rebuilding the broken config the way a
    * hand-edit would and looking for the message zod produces for it.
    */
  private def assertInvalid(message: String)(mutate: GameConfig => GameConfig)(using
      munit.Location
  ): Unit =
    val original = GameConfig.default
    assertEquals(original.issues, Nil, "the embedded config must be valid to begin with")
    val broken = mutate(original)
    assert(
      broken.issues.exists(_.message.contains(message)),
      s"expected an issue containing '$message', got: ${broken.issues.map(_.message)}"
    )
