package com.crianonim.janscape

import cats.Monad
import cats.effect.std.Random
import cats.syntax.all.*

/** The arena, the third of the depth systems alongside the mine and the forest.
  *
  * It differs from the two gathering systems in that the difficulty is opposed rather than rolled:
  * the Combat skill decides how often a blow lands against the opponent's Combat, one point of
  * difference moving the chance, and each landed blow is one exchange rather than one gather. A
  * fight is a sequence of exchanges, character first, until one of the two HP pools empties.
  *
  * Nothing here is stored beyond the character's own HP and arena depth: an opponent is rebuilt
  * from config when a fight starts, so only the character's part of the fight survives a reload.
  */

/** A live opponent: its fixed stats plus the HP it has left. The arena's equivalent of a floor of
  * nodes, rebuilt from config when a fight begins.
  */
case class Opponent(name: String, combat: Int, damage: Int, hp: Int)

/** How one exchange went: who landed, for how much, and whether the fight is over. Both the
  * character and the opponent carry their new HP, so the caller renders the bars without doing
  * arithmetic. `lost` means the character's pool hit zero; `won` means the opponent's did.
  */
case class CombatOutcome(
    character: Character,
    opponent: Opponent,
    youHit: Boolean,
    damageDealt: Int,
    opponentHit: Boolean,
    damageTaken: Int,
    xp: Int,
    levelsGained: Int,
    won: Boolean,
    lost: Boolean
)

/** Arriving at the next depth: what it cost and the fresh, stronger opponent waiting there. */
case class DeeperCombatOutcome(
    character: Character,
    opponent: Opponent,
    turns: Int,
    fatigue: Int,
    level: Int
)

object Combat:

  /** The flat part of the XP a landed blow earns, before the Combat gap moves it: equal fighters
    * earn this, one Combat below the opponent earns one more, and a blow can never earn less than
    * one. Deliberately a constant rather than a knob, since it is the shape of the reward rather
    * than its tuning.
    */
  val XpBase = 2

  /** The four coin flips an exchange makes, in the order the original would queue them: your hit,
    * your damage, the opponent's hit, the opponent's damage. Drawn by [[attack]] and passed in here
    * so a test can force any one of them.
    */
  case class CombatRolls(youHit: Int, youDamage: Double, opponentHit: Int, opponentDamage: Double)

  /** The damage the equipped weapon puts out, read from the config rather than the save so swapping
    * a weapon changes it immediately. A bare hand, or a slot holding something it does not accept,
    * puts out none — which the minimum-damage floor then lifts to 1.
    */
  def weaponDamage(character: Character, equipment: EquipmentConfig): Int =
    Equipment
      .equippedItem(character, Character.WeaponSlot, equipment)
      .flatMap(weapon => equipment.slots.get(Character.WeaponSlot).flatMap(_.get(weapon)))
      .getOrElse(0)

  /** The chance that `attacker` lands on `defender`: even fighters meet at the configured base, and
    * every point of Combat advantage moves it by the configured step, clamped between the
    * configured floor and ceiling so no matchup is ever hopeless or certain.
    */
  def hitChance(attackerCombat: Int, defenderCombat: Int, config: GameConfig): Double =
    val hit    = config.combat.hitChance
    val chance = hit.base + hit.perPoint * (attackerCombat - defenderCombat)
    math.min(hit.max, math.max(hit.min, chance))

  /** The character's chance to land a blow on the opponent. */
  def yourHitChance(character: Character, opponent: Opponent, config: GameConfig): Double =
    hitChance(character.skills.combat.level, opponent.combat, config)

  /** The opponent's chance to land a blow back. */
  def opponentHitChance(character: Character, opponent: Opponent, config: GameConfig): Double =
    hitChance(opponent.combat, character.skills.combat.level, config)

  /** A blow's damage: the weapon's damage swung by up to ±damageVariance, floored at 1 so a landed
    * hit always takes something off the pool.
    */
  def rollDamage(baseDamage: Int, roll: Double, config: GameConfig): Int =
    val factor = 1 + config.combat.damageVariance * (2 * roll - 1)
    math.max(1, math.round(baseDamage * factor).toInt)

  /** The Combat XP a landed blow earns: the opponent's Combat above the character's, plus the flat
    * base, floored at 1. Even fighters earn the base, one below earns one more, and an opponent the
    * character has outgrown still earns the floor.
    */
  def xpFor(character: Character, opponent: Opponent): Int =
    math.max(1, (opponent.combat - character.skills.combat.level) + XpBase)

  /** The opponent waiting at an arena depth. `combat` climbs one per depth while HP and damage stay
    * put, so a deeper fight is harder to land against but not a longer slog.
    */
  def opponentFor(level: Int, config: GameConfig): Opponent =
    val (name, base) = config.combat.opponents.head
    Opponent(name = name, combat = base.combat + (level - 1), damage = base.damage, hp = base.hp)

  /** The arena depth a fresh character enters, where the picker starts and where a save without one
    * falls back to.
    */
  def entryLevel(character: Character): Int = character.combatLevel

  /** One exchange: the character swings, then the opponent swings back if it is still standing.
    *
    * The hit rolls are drawn on a 1-100 scale, so the configured chance is its own threshold and the
    * min/max clamp is what the player sees. A kill by the character ends the exchange before the
    * opponent can answer, so winning is reward rather than a race to lose the last trade.
    */
  def attack[F[_]: Monad](character: Character, opponent: Opponent, config: GameConfig)(using
      random: Random[F]
  ): F[CombatOutcome] =
    for
      youHit       <- random.nextDouble.map(d => (math.floor(d * 100).toInt) + 1)
      youDamage    <- random.nextDouble
      opponentHit  <- random.nextDouble.map(d => (math.floor(d * 100).toInt) + 1)
      opponentDmg  <- random.nextDouble
    yield attackWith(character, opponent, config, CombatRolls(youHit, youDamage, opponentHit, opponentDmg))

  /** The exchange body with its coin flips already drawn. Repeats what [[attack]] does, so a test
    * can drive every branch without a random source.
    */
  private[janscape] def attackWith(
      character: Character,
      opponent: Opponent,
      config: GameConfig,
      rolls: CombatRolls
  ): CombatOutcome =
    val youLand = rolls.youHit <= threshold(yourHitChance(character, opponent, config))
    val dealt   = if youLand then rollDamage(weaponDamage(character, config.equipment), rolls.youDamage, config) else 0
    val struck  = opponent.copy(hp = math.max(0, opponent.hp - dealt))

    val award =
      if youLand then Xp.grantXp(character.skills.combat, xpFor(character, opponent), config.skills)
      else XpAward(character.skills.combat, levelsGained = 0)

    val won       = struck.hp <= 0
    val oppLands  =
      !won && rolls.opponentHit <= threshold(opponentHitChance(character, opponent, config))
    val taken     = if oppLands then rollDamage(opponent.damage, rolls.opponentDamage, config) else 0
    val hpAfter   = math.max(0, character.hp - taken)
    val withSkills = character.copy(skills = character.skills.updated(SkillName.Combat, award.skill))

    CombatOutcome(
      character = Character.setHp(withSkills, hpAfter),
      opponent = struck,
      youHit = youLand,
      damageDealt = dealt,
      opponentHit = oppLands,
      damageTaken = taken,
      xp = if youLand then xpFor(character, opponent) else 0,
      levelsGained = award.levelsGained,
      won = won,
      lost = hpAfter <= 0
    )

  /** Going deeper spends turns and fatigue and restores the character to full, mirroring the mine
    * and the forest, and rolls the next opponent one Combat stronger.
    */
  def goDeeper(character: Character, config: GameConfig): DeeperCombatOutcome =
    val combat = config.combat
    val level  = character.combatLevel + 1
    val healed = Character.setHp(Character.goDeeperCombat(character), combat.characterHp)
    val moved  = Character.addFatigue(
      Character.addTurns(healed, combat.deeperTurns),
      combat.deeperFatigue,
      config.play.fatigue.max
    )
    DeeperCombatOutcome(
      character = moved,
      opponent = opponentFor(level, config),
      turns = combat.deeperTurns,
      fatigue = combat.deeperFatigue,
      level = level
    )

  /** What a loss costs: the configured turns and a pool restored to the configured fraction, so the
    * character comes back bloodied rather than where they started.
    */
  def lose(character: Character, config: GameConfig): Character =
    val combat = config.combat
    val hp     = math.max(1, math.round(combat.characterHp * combat.lossHpFraction).toInt)
    Character.setHp(Character.addTurns(character, combat.lossTurns), hp)

  /** The roll threshold a hit chance describes, on the same 1-100 scale the rolls use. */
  private def threshold(chance: Double): Int = math.round(chance * 100).toInt
