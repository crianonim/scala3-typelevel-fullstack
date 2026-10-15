package com.crianonim.janscape

import cats.effect.IO
import cats.effect.std.Random
import cats.syntax.all.*
import munit.CatsEffectSuite

/** The arena harness: the opposed hit chance, the weapon read from the config, the damage swing,
  * the exchange (both directions, and who gets to answer), and the cost of going deeper or losing.
  */
class CombatTest extends CatsEffectSuite:

  private val seed                   = 20261007
  private def random: IO[Random[IO]] = Random.scalaUtilRandomSeedInt[IO](seed)

  private val config = GameConfig.default

  private def fighter: Character =
    Character.setHp(Character.createCharacter("Tester"), config.combat.characterHp)

  private def wolf(level: Int = 1): Opponent = Combat.opponentFor(level, config)

  private def rolls(
      youHit: Int,
      youDamage: Double = 0.5,
      opponentHit: Int = 0,
      opponentDamage: Double = 0.5
  ): Combat.CombatRolls = Combat.CombatRolls(youHit, youDamage, opponentHit, opponentDamage)

  test("the opponent climbs one Combat per arena depth, its HP and damage fixed"):
    assertEquals(wolf(1), Opponent("wolf", combat = 2, damage = 4, hp = 15))
    assertEquals(wolf(5), Opponent("wolf", combat = 6, damage = 4, hp = 15))

  test("a fresh character enters at depth one"):
    assertEquals(Combat.entryLevel(fighter), 1)

  test("weapon damage comes from whatever is equipped in the weapon slot"):
    assertEquals(Combat.weaponDamage(fighter, config.equipment), 2)
    val armed = fighter.copy(equipment = fighter.equipment.updated(Character.WeaponSlot, "sword"))
    assertEquals(Combat.weaponDamage(armed, config.equipment), 6)
    assertEquals(
      Combat.weaponDamage(fighter.copy(equipment = Map.empty), config.equipment),
      0
    )
    val wrong = fighter.copy(equipment = Map(Character.WeaponSlot -> "stone"))
    assertEquals(Combat.weaponDamage(wrong, config.equipment), 0)

  test("hit chance is even at equal Combat and swings a step per point"):
    assertEquals(Combat.hitChance(5, 5, config), 0.5)
    assertEquals(Combat.hitChance(7, 5, config), 0.7)
    assertEquals(Combat.hitChance(5, 7, config), 0.3)

  test("hit chance is clamped so nothing is certain either way"):
    assertEquals(Combat.hitChance(100, 1, config), config.combat.hitChance.max)
    assertEquals(Combat.hitChance(1, 100, config), config.combat.hitChance.min)

  test("your chance opposes your Combat to the opponent's"):
    assertEquals(Combat.yourHitChance(fighter, wolf(1), config), 0.4)
    assertEquals(Combat.opponentHitChance(fighter, wolf(1), config), 0.6)

  test("a blow swings by the configured variance and never does less than one"):
    assertEquals(Combat.rollDamage(2, 0.5, config), 2)
    assertEquals(Combat.rollDamage(2, 1.0, config), 3)
    assertEquals(Combat.rollDamage(2, 0.0, config), 1)
    assertEquals(Combat.rollDamage(4, 1.0, config), 5)
    assertEquals(Combat.rollDamage(0, 0.5, config), 1)

  test("XP rewards the gap in Combat, floored at one"):
    val veteran = fighter.copy(skills = fighter.skills.updated(SkillName.Combat, Skill(5, 0)))
    assertEquals(Combat.xpFor(fighter, wolf(1)), 3)
    assertEquals(Combat.xpFor(fighter, wolf(5)), 7)
    assertEquals(Combat.xpFor(veteran, wolf(1)), 1)

  test("a landed blow damages the opponent, retaliates and earns XP"):
    val outcome = Combat.attackWith(fighter, wolf(1), config, rolls(youHit = 10, opponentHit = 10))
    assertEquals(outcome.youHit, true)
    assertEquals(outcome.damageDealt, 2)
    assertEquals(outcome.opponent.hp, 13)
    assertEquals(outcome.opponentHit, true)
    assertEquals(outcome.damageTaken, 4)
    assertEquals(outcome.character.hp, 16)
    assertEquals(outcome.xp, 3)
    assertEquals(outcome.character.skills.combat.xp, 3)
    assertEquals(outcome.levelsGained, 0)
    assertEquals(outcome.won, false)
    assertEquals(outcome.lost, false)

  test("a miss deals nothing, earns nothing, but still draws a reply"):
    val outcome = Combat.attackWith(fighter, wolf(1), config, rolls(youHit = 90, opponentHit = 10))
    assertEquals(outcome.youHit, false)
    assertEquals(outcome.damageDealt, 0)
    assertEquals(outcome.opponent.hp, 15)
    assertEquals(outcome.xp, 0)
    assertEquals(outcome.character.skills.combat.xp, 0)
    assertEquals(outcome.opponentHit, true)
    assertEquals(outcome.damageTaken, 4)

  test("a killing blow ends the exchange before the opponent can answer"):
    val outcome =
      Combat.attackWith(fighter, wolf(1).copy(hp = 1), config, rolls(youHit = 1, opponentHit = 1))
    assertEquals(outcome.damageDealt, 2)
    assertEquals(outcome.opponent.hp, 0)
    assertEquals(outcome.won, true)
    assertEquals(outcome.opponentHit, false)
    assertEquals(outcome.damageTaken, 0)
    assertEquals(outcome.character.hp, 20)
    assertEquals(outcome.lost, false)

  test("losing the whole pool is a loss"):
    val nearlyDead = Character.setHp(fighter, 3)
    val outcome =
      Combat.attackWith(nearlyDead, wolf(1), config, rolls(youHit = 90, opponentHit = 1, opponentDamage = 1.0))
    assertEquals(outcome.opponentHit, true)
    assertEquals(outcome.damageTaken, 5)
    assertEquals(outcome.character.hp, 0)
    assertEquals(outcome.lost, true)
    assertEquals(outcome.won, false)

  test("a big enough reward levels Combat up"):
    val outcome = Combat.attackWith(fighter, wolf(1).copy(combat = 20), config, rolls(youHit = 1))
    assertEquals(outcome.xp, 21)
    assertEquals(outcome.levelsGained, 1)
    assertEquals(outcome.character.skills.combat.level, 2)

  test("going deeper heals, spends turns and fatigue, and rolls a stronger opponent"):
    val wounded = Character.setHp(fighter.copy(turn = 40, fatigue = 3, combatLevel = 1), 4)
    val outcome = Combat.goDeeper(wounded, config)
    assertEquals(outcome.level, 2)
    assertEquals(outcome.character.combatLevel, 2)
    assertEquals(outcome.character.hp, config.combat.characterHp)
    assertEquals(outcome.character.turn, 60)
    assertEquals(outcome.character.fatigue, 4)
    assertEquals(outcome.turns, config.combat.deeperTurns)
    assertEquals(outcome.fatigue, config.combat.deeperFatigue)
    assertEquals(outcome.opponent.combat, 3)

  test("losing costs turns and leaves the character on a fraction of their pool"):
    val beaten = Character.setHp(fighter.copy(turn = 100), 0)
    val next   = Combat.lose(beaten, config)
    assertEquals(next.turn, 130)
    assertEquals(next.hp, 10)

  test("a fought exchange through the effectful entry stays within the pools"):
    random.flatMap { implicit r: Random[IO] =>
      val character = fighter
      val opponent  = wolf(1)
      (1 to 200).toList
        .traverse(_ => Combat.attack[IO](character, opponent, config))
        .map { outcomes =>
          assert(outcomes.forall(o => o.damageDealt >= 0 && o.damageTaken >= 0))
          assert(outcomes.forall(o => !o.youHit || o.damageDealt >= 1))
          assert(outcomes.forall(o => !o.opponentHit || o.damageTaken >= 1))
          assert(outcomes.exists(_.youHit))
          assert(outcomes.exists(!_.youHit))
        }
    }
