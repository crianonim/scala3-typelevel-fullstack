package com.crianonim.janscape

import cats.Monad
import cats.effect.std.Random
import cats.syntax.all.*

/** What a swing at one node did, so the caller can report it in the player's terms. `turns` and
  * `xp` are the swing's price and wage whether it landed or missed; a miss only ever costs the
  * turns.
  */
case class MineOutcome(
    character: Character,
    item: String,
    failed: Boolean,
    floorCleared: Boolean,
    foundLadder: Boolean,
    turns: Int,
    fatigue: Int,
    xp: Int,
    levelsGained: Int
)

/** A descent: what it cost, where it landed, and whether the destination was a shortcut worth
  * keeping. `unlocked` is the level a fast-travel milestone was earned for, absent when the descent
  * landed between milestones.
  */
case class DescendOutcome(
    character: Character,
    turns: Int,
    fatigue: Int,
    level: Int,
    unlocked: Option[Int]
)

object Mining:

  /** The three coin flips a swing makes, in the order the TypeScript original makes them: the
    * effort check, then the ladder, then whether the swing tires. Drawn by [[mineNode]] and passed
    * in here so a test can force any one of them.
    */
  case class MineRolls(fatigueCheck: Int, ladder: Double, tiring: Double)

  def canDescend(character: Character): Boolean = character.hasMineLadder

  /** Shortcuts are earned by depth rather than by skill, so they can never carry a character
    * somewhere they have not already been. Every multiple of everyLevels counts, which makes the
    * milestone a property of the destination rather than of how the character got there.
    */
  def isFastTravelLevel(level: Int, mining: MiningConfig): Boolean =
    level % mining.fastTravel.everyLevels == 0

  /** What the entry picker offers: the first level, which everyone can always reach, plus every
    * shortcut earned so far. Deduped because the starting level is not itself an unlock, but
    * nothing stops a save listing it anyway.
    */
  def entryLevels(character: Character): List[Int] =
    (Character.StartingMineLevel :: character.unlockedMineLevels).distinct.sorted

  /** How far the character's Mining sits above the depth they are mining.
    *
    * Truncating rather than rounding keeps the two directions symmetric: a gap of 1 to 4 either way
    * is not enough to count. Clamped so a large gap can never swing the result further than the
    * configured maximum.
    */
  def mineDepthFactor(character: Character, mining: MiningConfig): Int =
    val gap    = character.skills.mining.level - character.mineLevel
    val factor = (gap / mining.depthFactor.levelsPerPoint).toInt
    math.min(mining.depthFactor.max, math.max(-mining.depthFactor.max, factor))

  /** How much mining power the equipped tool grants. Read from the config rather than stored on the
    * save, so swapping a tool changes what can be broken immediately and the two can never drift
    * apart. An empty slot, or one holding something it does not accept, grants none.
    */
  def miningPower(character: Character, equipment: EquipmentConfig): Int =
    Equipment
      .equippedItem(character, Character.MiningToolSlot, equipment)
      .flatMap(tool => equipment.slots.get(Character.MiningToolSlot).flatMap(_.get(tool)))
      .getOrElse(0)

  /** Whether the character has the mining power a material takes to break. An item the schedule
    * does not price counts as unbreakable, which is the safe reading for anything a hand-edited
    * save put on a floor.
    */
  def canMine(item: String, character: Character, config: GameConfig): Boolean =
    GameConfig
      .miningPowerFor(item, config.mining.materials)
      .exists(_ <= miningPower(character, config.equipment))

  /** Entering the mine picks a starting depth rather than always taking the shallowest one, so the
    * shortcut is the point of arriving. The ladder is dropped either way, because it belonged to
    * whichever level was being left behind, and the floor below is rolled fresh.
    */
  def enterMine[F[_]: Monad](character: Character, level: Int, config: GameConfig)(using
      random: Random[F]
  ): F[Character] =
    Schedule
      .generateFloor[F](
        Schedule.weightsAtLevel(level, config.mining.materials.schedule),
        config.mining.nodes
      )
      .map(nodes => character.copy(mineLevel = level, hasMineLadder = false, mineNodes = nodes))

  /** Descending consumes the ladder and spends turns and fatigue, so a deep climb is a real cost
    * rather than a free level. The floor rolled is the whole schedule for the level being arrived
    * at: what the character cannot break is shown locked rather than hidden.
    */
  def descend[F[_]: Monad](character: Character, config: GameConfig)(using
      random: Random[F]
  ): F[DescendOutcome] =
    val ladder   = config.mining.ladder
    val level    = character.mineLevel + 1
    val unlocked = if isFastTravelLevel(level, config.mining) then Some(level) else None

    for
      descended <- Monad[F].pure {
        val moved = Character.goDownMineLevel(character)
        Character.addFatigue(
          Character.addTurns(moved, ladder.descendTurns),
          ladder.descendFatigue,
          config.play.fatigue.max
        )
      }
      nodes <- Schedule.generateFloor[F](
        Schedule.weightsAtLevel(level, config.mining.materials.schedule),
        config.mining.nodes
      )
      moved      = descended.copy(mineNodes = nodes)
      withUnlock = unlocked.fold(moved)(value => Character.addMineUnlock(moved, value))
    yield DescendOutcome(
      character = withUnlock,
      turns = ladder.descendTurns,
      fatigue = ladder.descendFatigue,
      level = level,
      unlocked = unlocked
    )

  /** One swing at one node of the floor.
    *
    * A miss costs the turns it took and nothing else: the node stays, no XP, no fatigue, and no
    * ladder roll — clearing the floor is the reliable way down rather than one of two coin flips.
    * The two validation failures are the TypeScript original's throws, returned as errors: the
    * floor check (which the UI cannot produce, since its buttons come from the same floor) and the
    * power check (which only a hand-edited save or a skipped UI can reach).
    */
  def mineNode[F[_]: Monad](character: Character, item: String, config: GameConfig)(using
      random: Random[F]
  ): F[Either[GameError, MineOutcome]] =
    validateSwing(character, item, config) match
      case Left(error) => Monad[F].pure(Left(error))
      case Right(_)    =>
        // The original's roll: floor(random * rollMax) + 1, so 1 and rollMax are both reachable.
        for
          check <- random.nextDouble.map(d =>
            (math.floor(d * config.play.fatigue.rollMax).toInt) + 1
          )
          ladder <- random.nextDouble
          tiring <- random.nextDouble
        yield mineNodeWith(character, item, config, MineRolls(check, ladder, tiring))

  /** The swing body with its coin flips already drawn. Repeats the validation the effectful wrapper
    * performs, so a test can drive every branch without a random source.
    */
  private[janscape] def mineNodeWith(
      character: Character,
      item: String,
      config: GameConfig,
      rolls: MineRolls
  ): Either[GameError, MineOutcome] =
    validateSwing(character, item, config).map { _ =>
      val swing = config.mining.swing
      val turns = turnsFor(item, character, config)

      if !Fatigue.checkFatigue(character.fatigue, config.play, rolls.fatigueCheck).success then
        MineOutcome(
          character = Character.addTurns(character, turns),
          item = item,
          failed = true,
          floorCleared = false,
          foundLadder = false,
          turns = turns,
          fatigue = 0,
          xp = 0,
          levelsGained = 0
        )
      else
        val xpPerMine    = math.floor(swing.xpPerMine).toInt
        val award        = Xp.grantXp(character.skills.mining, xpPerMine, config.skills)
        val floor        = Character.removeItem(character.mineNodes, item, 1)
        val floorCleared = Character.totalItems(floor) == 0

        // Rolled only once the swing has landed, so the ladder is something the swing earned
        // rather than something it can turn up on the way to missing. Emptying the floor always
        // uncovers the way down, so unlucky ladder rolls cannot strand a character on a level
        // they have cleared.
        val rolled      = !character.hasMineLadder && rolls.ladder < config.mining.ladder.chance
        val foundLadder = rolled || (floorCleared && !character.hasMineLadder)

        val fatigue =
          if rolls.tiring < fatigueChanceFor(item, character, config) then swing.fatigue else 0

        val swung = character.copy(
          skills = character.skills.updated(SkillName.Mining, award.skill),
          mineNodes = floor,
          inventory = Character.addItem(character.inventory, item, 1)
        )
        val next = Character.addFatigue(
          Character.addTurns(swung, turns),
          fatigue,
          config.play.fatigue.max
        )

        MineOutcome(
          character = if foundLadder then Character.setMineLadder(next, true) else next,
          item = item,
          failed = false,
          floorCleared = floorCleared,
          foundLadder = foundLadder,
          turns = turns,
          fatigue = fatigue,
          xp = xpPerMine,
          levelsGained = award.levelsGained
        )
    }

  private def validateSwing(
      character: Character,
      item: String,
      config: GameConfig
  ): Either[GameError, Unit] =
    if Schedule.nodeCount(character.mineNodes, item) == 0 then
      Left(
        GameError.InvalidAction(s"No $item left on mine level ${character.mineLevel}")
      )
    else if !canMine(item, character, config) then
      val required = GameConfig.miningPowerFor(item, config.mining.materials).getOrElse(0)
      Left(
        GameError.InvalidAction(
          s"$item needs mining power $required, but ${character.name} has " +
            s"${miningPower(character, config.equipment)}"
        )
      )
    else Right(())

  /** The chance a landed swing tires the character, derived from the depth factor so it cannot
    * drift out of sync with it, clamped so the factor can never drive it outside 0-1.
    */
  def mineFatigueChance(character: Character, config: GameConfig): Double =
    val fatigueChance = config.mining.fatigueChance
    val chance        =
      fatigueChance.base - fatigueChance.perDepthFactorPoint *
        mineDepthFactor(character, config.mining)
    math.min(1, math.max(0, chance))

  /** How many turns a swing at a material costs. Time comes from the material and the character's
    * power, never from the Mining skill. The relief term is clamped to 0 so an item the character
    * cannot break reads as paying its full cost rather than earning reverse relief, and the total
    * is floored at minTurns so an overwhelming power still leaves a swing to make.
    */
  def turnsFor(item: String, character: Character, config: GameConfig): Int =
    val swing  = config.mining.swing
    val tier   = GameConfig.miningPowerFor(item, config.mining.materials).getOrElse(0)
    val relief = math.max(0, miningPower(character, config.equipment) - tier)
    math.max(
      swing.minTurns,
      math.round(swing.baseTurns + swing.turnsPerTier * tier - swing.turnsPerPower * relief).toInt
    )

  /** The chance a landed swing tires the character, material included: the depth factor's base
    * chance with a multiplier the material layers on top. Harder material is more wearing, power
    * beyond what it takes buys some of that back, and the multiplier is floored so overwhelming
    * power cannot drive the chance negative.
    */
  def fatigueChanceFor(item: String, character: Character, config: GameConfig): Double =
    val fatigueChance = config.mining.fatigueChance
    val tier          = GameConfig.miningPowerFor(item, config.mining.materials).getOrElse(0)
    val relief        = math.max(0, miningPower(character, config.equipment) - tier)
    val multiplier    =
      math.max(
        fatigueChance.minFactor,
        1 + fatigueChance.perTier * tier -
          fatigueChance.perPower * relief
      )
    val chance = mineFatigueChance(character, config) * multiplier
    math.min(1, math.max(0, chance))
