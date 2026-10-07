package com.crianonim.janscape

import munit.CatsEffectSuite

import scala.collection.immutable.ListMap

/** The TypeScript janscape-crafting.ts harness, check for check. Crafting is pure, so no random
  * source is needed anywhere in this suite.
  */
class CraftingTest extends CatsEffectSuite:

  private val config    = GameConfig.default
  private val recipes   = config.crafting.recipes
  private val copperBar =
    Recipe(1, ListMap("copper ore" -> 2, "coal" -> 1), 1)

  private def withXp(inventory: List[ItemStack], smithingLevel: Int): Character =
    val character = Character.createCharacter("Tester")
    val xp        = Xp.xpForLevel(smithingLevel, config.skills).toInt
    character.copy(
      inventory = inventory,
      skills = character.skills.updated(
        SkillName.Smithing,
        Skill(Xp.levelFromXp(xp, config.skills), xp)
      )
    )

  private def stack(item: String, count: Int): ItemStack = ItemStack(item, count)

  test("the recipes are declared"):
    assertEquals(
      recipes.keys.toList,
      List(
        "copper bar",
        "bronze bar",
        "iron bar",
        "copper pickaxe",
        "bronze pickaxe",
        "stone axe",
        "copper axe",
        "bronze axe"
      )
    )
    assertEquals(
      recipes.get("copper bar"),
      Some(copperBar)
    )
    assertEquals(copperBar.inputs.keys.toList, List("copper ore", "coal"))
    assertEquals(
      recipes.get("bronze bar"),
      Some(
        Recipe(2, ListMap("copper bar" -> 1, "tin ore" -> 2, "coal" -> 1), 2)
      )
    )
    assertEquals(
      recipes.get("iron bar"),
      Some(Recipe(3, ListMap("iron ore" -> 2, "coal" -> 1), 3))
    )
    assertEquals(
      recipes.get("copper pickaxe"),
      Some(Recipe(2, ListMap("copper bar" -> 10), 5))
    )
    assertEquals(
      recipes.get("bronze pickaxe"),
      Some(Recipe(3, ListMap("bronze bar" -> 10), 10))
    )
    assertEquals(
      recipes.get("stone axe"),
      Some(Recipe(1, ListMap("stone" -> 10), 5))
    )
    assertEquals(
      recipes.get("copper axe"),
      Some(Recipe(2, ListMap("copper bar" -> 10), 5))
    )
    assertEquals(
      recipes.get("bronze axe"),
      Some(Recipe(3, ListMap("bronze bar" -> 10), 10))
    )

    // The axe line is the pickaxe line with the name swapped, so a designer rebalancing one tool
    // cannot quietly unbalance the other.
    assert(
      List("copper", "bronze").forall { tier =>
        recipes.get(s"$tier axe") == recipes.get(s"$tier pickaxe")
      }
    )

  test("smithing is a saved skill"):
    assertEquals(Character.createCharacter("New").skills.smithing.level, 1)
    val legacy = Character.parse("""{"name":"Old","skills":{}}""")
    legacy match
      case Left(error) => fail(error.message)
      case Right(old)  => assertEquals(old.skills.smithing.level, 1)

    val roundTrip = Character.parse(
      Character.encode(Character.createCharacter("X"))
    )
    roundTrip match
      case Left(error)   => fail(error.message)
      case Right(loaded) => assertEquals(loaded.skills.smithing.xp, 0)

  test("recipes are gated by Smithing level"):
    val mats: List[ItemStack] = List(
      stack("copper ore", 10),
      stack("coal", 10),
      stack("copper bar", 10),
      stack("tin ore", 10),
      stack("iron ore", 10)
    )
    def at(level: Int): Character = withXp(mats, level)

    assert(Crafting.isUnlocked(at(1), "copper bar", recipes))
    assert(!Crafting.isUnlocked(at(1), "bronze bar", recipes))
    assert(Crafting.isUnlocked(at(2), "bronze bar", recipes))
    assert(!Crafting.isUnlocked(at(2), "iron bar", recipes))
    assert(Crafting.isUnlocked(at(3), "iron bar", recipes))

    // A locked recipe caps at zero even with materials.
    assertEquals(Crafting.maxCraftable(at(1), "bronze bar", recipes), 0)
    assert(Crafting.maxCraftable(at(2), "bronze bar", recipes) > 0)
    assert(
      !Crafting.canCraft(at(1), "iron bar", recipes) &&
        Crafting.canCraft(at(3), "iron bar", recipes)
    )
    assert(Crafting.craft(at(1), "bronze bar", 1, config).isLeft)

  test("maxCraftable is min(level, affordable)"):
    val broke = withXp(Nil, 1)
    assertEquals(Crafting.maxCraftable(broke, "copper bar", recipes), 0)

    val some = withXp(List(stack("copper ore", 4), stack("coal", 2)), 1)
    assertEquals(Crafting.maxCraftable(some, "copper bar", recipes), 1)

    val rich = withXp(List(stack("copper ore", 100), stack("coal", 100)), 5)
    assertEquals(Crafting.maxCraftable(rich, "copper bar", recipes), 5)

    val materialsBound = withXp(List(stack("copper ore", 6), stack("coal", 2)), 9)
    assertEquals(Crafting.maxCraftable(materialsBound, "copper bar", recipes), 2)

    val bronze =
      withXp(List(stack("copper bar", 1), stack("tin ore", 2), stack("coal", 1)), 3)
    assertEquals(Crafting.maxCraftable(bronze, "bronze bar", recipes), 1)

    assertEquals(Crafting.maxCraftable(rich, "mithril", recipes), 0)

  test("canCraft needs every input"):
    assert(
      Crafting.canCraft(
        withXp(List(stack("copper ore", 2), stack("coal", 1)), 1),
        "copper bar",
        recipes
      )
    )
    assert(
      !Crafting.canCraft(withXp(List(stack("copper ore", 2)), 1), "copper bar", recipes)
    )
    assert(
      !Crafting.canCraft(
        withXp(List(stack("copper ore", 2), stack("coal", 1)), 1),
        "mithril",
        recipes
      )
    )

  test("craft spends inputs, adds output, and pays turns and XP"):
    val before  = withXp(List(stack("copper ore", 10), stack("coal", 10)), 5)
    val outcome = Crafting.craft(before, "copper bar", 3, config) match
      case Left(error)  => fail(error.message)
      case Right(value) => value

    assertEquals(outcome.amount, 3)
    assertEquals(Character.heldAmount(outcome.character, "copper bar"), 3)
    assertEquals(Character.heldAmount(outcome.character, "copper ore"), 4)
    assertEquals(Character.heldAmount(outcome.character, "coal"), 7)
    assertEquals(outcome.turns, 3)
    assertEquals(outcome.character.turn, before.turn + 3)
    assertEquals(outcome.xp, outcome.turns)
    assertEquals(outcome.character.skills.smithing.xp, before.skills.smithing.xp + outcome.turns)
    assertEquals(outcome.character.fatigue, before.fatigue)

  test("craft refuses what it cannot pay"):
    val rich = withXp(List(stack("copper ore", 100), stack("coal", 100)), 1)
    assert(Crafting.craft(rich, "copper bar", 2, config).isLeft)
    assert(Crafting.craft(rich, "copper bar", 0, config).isLeft)
    assert(
      Crafting
        .craft(withXp(List(stack("copper ore", 10)), 1), "copper bar", 1, config)
        .isLeft
    )
    assert(Crafting.craft(rich, "mithril", 1, config).isLeft)

  test("bronze runs through copper"):
    val start =
      withXp(List(stack("copper ore", 2), stack("coal", 4), stack("tin ore", 2)), 2)
    assertEquals(Crafting.maxCraftable(start, "bronze bar", recipes), 0)

    val copper = Crafting.craft(start, "copper bar", 1, config) match
      case Left(error)  => fail(error.message)
      case Right(value) => value.character
    assertEquals(Character.heldAmount(copper, "copper bar"), 1)

    val bronze = Crafting.craft(copper, "bronze bar", 1, config) match
      case Left(error)  => fail(error.message)
      case Right(value) => value.character
    assertEquals(Character.heldAmount(bronze, "bronze bar"), 1)
    assertEquals(Character.heldAmount(bronze, "copper bar"), 0)
    assertEquals(Character.heldAmount(bronze, "tin ore"), 0)

  test("pickaxes consume bars and pay turns as XP"):
    assertEquals(
      Crafting.maxCraftable(withXp(List(stack("copper bar", 10)), 1), "copper pickaxe", recipes),
      0
    )
    assertEquals(
      Crafting.maxCraftable(withXp(List(stack("copper bar", 10)), 2), "copper pickaxe", recipes),
      1
    )

    val copper = Crafting.craft(
      withXp(List(stack("copper bar", 10)), 2),
      "copper pickaxe",
      1,
      config
    ) match
      case Left(error)  => fail(error.message)
      case Right(value) => value
    assertEquals(Character.heldAmount(copper.character, "copper pickaxe"), 1)
    assertEquals(Character.heldAmount(copper.character, "copper bar"), 0)
    assertEquals(copper.turns, 5)
    assertEquals(copper.xp, 5)

    assertEquals(
      Crafting.maxCraftable(withXp(List(stack("bronze bar", 10)), 2), "bronze pickaxe", recipes),
      0
    )
    val bronze = Crafting.craft(
      withXp(List(stack("bronze bar", 10)), 3),
      "bronze pickaxe",
      1,
      config
    ) match
      case Left(error)  => fail(error.message)
      case Right(value) => value
    assertEquals(bronze.turns, 10)
    assertEquals(bronze.xp, 10)
    assertEquals(Character.heldAmount(bronze.character, "bronze bar"), 0)

  test("leveling raises the batch cap"):
    val start = withXp(List(stack("copper ore", 40), stack("coal", 40)), 1)
    assertEquals(Crafting.maxCraftable(start, "copper bar", recipes), 1)

    val leveled = (1 to 10).foldLeft(start) { (character, _) =>
      Crafting.craft(character, "copper bar", 1, config) match
        case Left(error)  => fail(error.message)
        case Right(value) => value.character
    }

    assertEquals(leveled.skills.smithing.level, 2)
    assertEquals(Crafting.maxCraftable(leveled, "copper bar", recipes), 2)
