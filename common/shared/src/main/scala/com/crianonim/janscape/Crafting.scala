package com.crianonim.janscape

import scala.collection.immutable.ListMap

/** One craft: what it produced, and what it cost.
  *
  * Crafting spends turns and earns Smithing XP, and nothing else: it is a deliberate action rather
  * than a roll, so there is no fatigue check and no way for a batch to come up short.
  */
final case class CraftOutcome(
    character: Character,
    item: String,
    amount: Int,
    turns: Int,
    xp: Int,
    levelsGained: Int
)

object Crafting:

  /** Whether the character's Smithing level is high enough to work a recipe at all. This is the
    * recipe's own gate, separate from how large a batch can be: knowing copper at level 1 does not
    * let a level-1 character make two at once.
    */
  def isUnlocked(character: Character, item: String, recipes: ListMap[String, Recipe]): Boolean =
    recipes.get(item).exists(recipe => character.skills.smithing.level >= recipe.level)

  /** A recipe is craftable when it is unlocked and every input is on hand. There is no station, so
    * this is the whole gate.
    */
  def canCraft(character: Character, item: String, recipes: ListMap[String, Recipe]): Boolean =
    recipes.get(item).exists { recipe =>
      character.skills.smithing.level >= recipe.level &&
      recipe.inputs.forall { (input, required) =>
        Character.heldAmount(character, input) >= required
      }
    }

  /** How many of a recipe can be made in one batch. The Smithing level is the cap the player earns;
    * the materials on hand are the cap they can actually pay, so a batch is the smaller of the two.
    * Zero means the craft cannot start, which covers both a locked recipe and one the character
    * cannot afford. Read from the character rather than passed in, so a batch can never be sized
    * against a level the character does not have.
    */
  def maxCraftable(character: Character, item: String, recipes: ListMap[String, Recipe]): Int =
    recipes.get(item) match
      case None         => 0
      case Some(recipe) =>
        val level = character.skills.smithing.level
        if level < recipe.level then 0
        else
          val byMaterials = recipe.inputs.iterator
            .map { (input, required) => Character.heldAmount(character, input) / required }
            .minOption
            .getOrElse(0)
          math.max(0, math.min(level, byMaterials))

  /** Craft one batch. Errors when the recipe is unknown, the batch is empty, the batch overshoots
    * the Smithing level, or an input is short; the UI cannot produce any of those, since it sizes
    * the batch with [[maxCraftable]]. Turns are per item, so a batch costs turns * amount and earns
    * the same in XP.
    */
  def craft(
      character: Character,
      item: String,
      amount: Int,
      config: GameConfig
  ): Either[GameError, CraftOutcome] =
    config.crafting.recipes.get(item) match
      case None =>
        Left(GameError.InvalidAction(s"No recipe for $item"))
      case Some(recipe) =>
        val batch = amount
        val level = character.skills.smithing.level

        if level < recipe.level then
          Left(
            GameError.InvalidAction(
              s"$item needs Smithing level ${recipe.level}, but ${character.name} has $level"
            )
          )
        else if batch < 1 then Left(GameError.InvalidAction(s"Cannot craft fewer than one $item"))
        else if batch > level then
          Left(
            GameError.InvalidAction(
              s"Smithing level $level caps a batch at $level, not $batch"
            )
          )
        else
          recipe.inputs
            .find { (input, required) =>
              Character.heldAmount(character, input) < required * batch
            }
            .map { (input, _) =>
              GameError.InvalidAction(s"Not enough $input to craft $batch $item")
            } match
            case Some(error) => Left(error)
            case None        =>
              val turns = recipe.turns * batch

              // Inputs are spent before the output is added, so a recipe whose output is also one
              // of its inputs cannot pay itself forward.
              val spent = recipe.inputs.foldLeft(character.inventory) {
                case (inventory, (input, required)) =>
                  Character.removeItem(inventory, input, required * batch)
              }
              val inventory = Character.addItem(spent, item, batch)
              val award     = Xp.grantXp(character.skills.smithing, turns, config.skills)
              val next      = Character.addTurns(
                character.copy(
                  skills = character.skills.updated(SkillName.Smithing, award.skill),
                  inventory = inventory
                ),
                turns
              )

              Right(
                CraftOutcome(
                  character = next,
                  item = item,
                  amount = batch,
                  turns = turns,
                  xp = turns,
                  levelsGained = award.levelsGained
                )
              )
