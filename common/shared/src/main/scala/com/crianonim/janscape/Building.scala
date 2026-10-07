package com.crianonim.janscape

import scala.collection.immutable.ListMap

/** One build: what was built, and what it cost.
  *
  * Building spends materials and turns and earns nothing else: no XP and no fatigue, because a
  * workstation is a one-off rather than a repeated action.
  */
final case class BuildOutcome(
    character: Character,
    name: String,
    turns: Int
)

object Building:

  /** Whether a station has already been built. Only one of each type can exist, so this is the
    * whole "can I build another of these" gate.
    */
  def isBuilt(character: Character, name: String): Boolean =
    character.buildings.contains(name)

  /** A station is buildable when it is known, not already built, and every input is on hand. There
    * is no level gate: building is a material sink, not a skill.
    */
  def canBuild(
      character: Character,
      name: String,
      buildables: ListMap[String, Buildable]
  ): Boolean =
    buildables.get(name).exists { buildable =>
      !isBuilt(character, name) &&
      buildable.inputs.forall { (item, required) =>
        Character.heldAmount(character, item) >= required
      }
    }

  /** Build one station. Errors when it is unknown, already built, or an input is short; the UI
    * cannot produce any of those, since it gates the button with [[canBuild]]. The inputs are
    * checked before anything is spent, so a build either pays the whole cost or changes nothing.
    */
  def build(
      character: Character,
      name: String,
      config: GameConfig
  ): Either[GameError, BuildOutcome] =
    config.building.buildables.get(name) match
      case None =>
        Left(GameError.InvalidAction(s"No buildable called $name"))
      case Some(_) if isBuilt(character, name) =>
        Left(GameError.InvalidAction(s"${character.name} has already built the $name"))
      case Some(buildable) =>
        buildable.inputs
          .find { (item, required) =>
            Character.heldAmount(character, item) < required
          }
          .map { (item, _) =>
            GameError.InvalidAction(s"Not enough $item to build the $name")
          } match
          case Some(error) => Left(error)
          case None        =>
            val spent = buildable.inputs.foldLeft(character.inventory) {
              case (inventory, (item, required)) =>
                Character.removeItem(inventory, item, required)
            }
            val next = Character.addTurns(
              character.copy(
                inventory = spent,
                buildings = character.buildings :+ name
              ),
              buildable.turns
            )
            Right(BuildOutcome(character = next, name = name, turns = buildable.turns))
