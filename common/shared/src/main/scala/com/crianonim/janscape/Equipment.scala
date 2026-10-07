package com.crianonim.janscape

import scala.collection.immutable.ListMap

object Equipment:

  /** Which slot an item belongs in, or nothing for anything that is not equipment. Reads the config
    * rather than the save, so an item a hand-edited save filed under the wrong slot is never
    * treated as gear.
    */
  def slotFor(item: String, equipment: EquipmentConfig): Option[String] =
    equipment.slots.collectFirst { case (slot, items) if items.contains(item) => slot }

  /** The item in a slot, or nothing when the slot is empty or holds something it does not accept.
    * Checking against the slot map keeps a stray save value from showing as equipped when the
    * character could never have put it there.
    */
  def equippedItem(
      character: Character,
      slot: String,
      equipment: EquipmentConfig
  ): Option[String] =
    character.equipment
      .get(slot)
      .filter(item => equipment.slots.get(slot).exists(_.contains(item)))

  /** The held items that fit a slot, in config order, which is what a swap can offer. The equipped
    * item is not in the inventory, so it never appears here.
    */
  def inventoryItemsForSlot(
      character: Character,
      slot: String,
      equipment: EquipmentConfig
  ): List[String] =
    equipment.slots
      .getOrElse(slot, ListMap.empty)
      .keys
      .filter(item => Character.heldAmount(character, item) >= 1)
      .toList

  /** Swap the item in its slot for one the character is holding. Whatever was equipped goes back to
    * the inventory, so a swap destroys nothing and the inventory stays the single place unequipped
    * gear lives. Equipping an item already held alongside the equipped copy is the only no-op; the
    * equipped copy itself is never in the inventory, so asking to equip what the slot already holds
    * is not holding it.
    */
  def equip(
      character: Character,
      item: String,
      equipment: EquipmentConfig
  ): Either[GameError, Character] =
    slotFor(item, equipment) match
      case None =>
        Left(GameError.InvalidAction(s"$item is not equipment"))
      case Some(slot) =>
        if Character.heldAmount(character, item) < 1 then
          Left(GameError.InvalidAction(s"${character.name} is not holding a $item"))
        else if character.equipment.get(slot).contains(item) then Right(character)
        else
          val removed   = Character.removeItem(character.inventory, item, 1)
          val inventory =
            character.equipment
              .get(slot)
              .fold(removed)(current => Character.addItem(removed, current, 1))
          Right(
            character.copy(equipment = character.equipment + (slot -> item), inventory = inventory)
          )
