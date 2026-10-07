package com.crianonim.janscape

import munit.CatsEffectSuite

import scala.collection.immutable.ListMap

/** The TypeScript janscape-equipment.ts harness, check for check. */
class EquipmentTest extends CatsEffectSuite:

  private val equipment = GameConfig.default.equipment
  private val Slot      = Character.MiningToolSlot
  private val Axe       = "Forestry Tool"

  private def held(character: Character, item: String): Int =
    Character.heldAmount(character, item)

  private def withItems(
      items: List[ItemStack],
      base: Character = Character.createCharacter("Tester")
  ): Character =
    base.copy(inventory = items)

  private def stack(item: String, count: Int): ItemStack = ItemStack(item, count)

  private def equipOrDie(
      character: Character,
      item: String
  ): Character =
    Equipment.equip(character, item, equipment) match
      case Left(error)  => fail(error.message)
      case Right(value) => value

  test("the starting loadout"):
    val fresh = Character.createCharacter("Tester")
    assertEquals(fresh.equipment.get(Slot), Some("stone pickaxe"))
    assertEquals(held(fresh, "stone pickaxe"), 0)
    assertEquals(
      Character.StartingEquipment,
      Map(Slot -> "stone pickaxe")
    )

  test("the config declares the slots"):
    assertEquals(
      equipment.slots.get(Slot),
      Some(ListMap("stone pickaxe" -> 1, "copper pickaxe" -> 2, "bronze pickaxe" -> 3))
    )
    assertEquals(
      equipment.slots.get(Axe),
      Some(ListMap("stone axe" -> 1, "copper axe" -> 2, "bronze axe" -> 3))
    )
    assertEquals(
      equipment.slots(Slot).values.toList,
      equipment.slots(Axe).values.toList
    )
    assertEquals(Character.createCharacter("Tester").equipment.get(Axe), None)
    assertEquals(Equipment.slotFor("copper axe", equipment), Some(Axe))

  test("legacy saves gain the loadout"):
    val legacy = Character.parse("""{"name":"Old","skills":{}}""")
    legacy match
      case Left(error) => fail(error.message)
      case Right(old)  => assertEquals(old.equipment.get(Slot), Some("stone pickaxe"))

    val round = Character.parse(Character.encode(Character.createCharacter("X")))
    round match
      case Left(error)   => fail(error.message)
      case Right(loaded) => assertEquals(loaded.equipment.get(Slot), Some("stone pickaxe"))

  test("reading equipment"):
    val fresh = Character.createCharacter("Tester")
    assertEquals(Equipment.slotFor("copper pickaxe", equipment), Some(Slot))
    assertEquals(Equipment.slotFor("stone", equipment), None)
    assertEquals(Equipment.equippedItem(fresh, Slot, equipment), Some("stone pickaxe"))
    assertEquals(
      Equipment.equippedItem(fresh.copy(equipment = Map.empty), Slot, equipment),
      None
    )
    assertEquals(Equipment.equippedItem(fresh, "Back", equipment), None)
    assertEquals(
      Equipment.equippedItem(fresh.copy(equipment = Map(Slot -> "stone")), Slot, equipment),
      None
    )

  test("what a swap can offer"):
    val rich = withItems(
      List(stack("copper pickaxe", 1), stack("bronze pickaxe", 2), stack("stone", 5))
    )
    assertEquals(
      Equipment.inventoryItemsForSlot(rich, Slot, equipment),
      List("copper pickaxe", "bronze pickaxe")
    )
    assert(!Equipment.inventoryItemsForSlot(rich, Slot, equipment).contains("stone pickaxe"))
    assertEquals(
      Equipment.inventoryItemsForSlot(withItems(List(stack("stone", 5))), Slot, equipment),
      Nil
    )

  test("swapping sends the old tool to the inventory"):
    val rich    = withItems(List(stack("copper pickaxe", 1), stack("bronze pickaxe", 2)))
    val swapped = equipOrDie(rich, "copper pickaxe")
    assertEquals(swapped.equipment.get(Slot), Some("copper pickaxe"))
    assertEquals(held(swapped, "copper pickaxe"), 0)
    assertEquals(held(swapped, "stone pickaxe"), 1)
    assertEquals(held(swapped, "bronze pickaxe"), 2)

    val twice = equipOrDie(withItems(List(stack("copper pickaxe", 2))), "copper pickaxe")
    assertEquals(held(twice, "copper pickaxe"), 1)

  test("equipping into an empty slot"):
    val empty = withItems(
      List(stack("copper pickaxe", 1)),
      Character.createCharacter("Tester").copy(equipment = Map.empty)
    )
    val equipped = equipOrDie(empty, "copper pickaxe")
    assertEquals(equipped.equipment.get(Slot), Some("copper pickaxe"))
    assertEquals(held(equipped, "copper pickaxe"), 0)
    assertEquals(held(equipped, "stone pickaxe"), 0)

  test("equipping the equipped tool is a no-op"):
    val both = withItems(List(stack("copper pickaxe", 1)))
      .copy(equipment = Map(Slot -> "copper pickaxe"))
    Equipment.equip(both, "copper pickaxe", equipment) match
      case Left(error) => fail(error.message)
      case Right(same) => assert(same eq both, "the same character comes back")

  test("equip refuses what it cannot do"):
    val fresh = Character.createCharacter("Tester")
    assert(Equipment.equip(fresh, "stone", equipment).isLeft)
    assert(Equipment.equip(fresh, "copper pickaxe", equipment).isLeft)
