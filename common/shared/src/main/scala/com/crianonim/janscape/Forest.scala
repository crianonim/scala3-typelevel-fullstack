package com.crianonim.janscape

import cats.Monad
import cats.effect.std.Random
import cats.syntax.all.*

/** The forest is the mine with the mining taken out. A glade is rolled from the same depth
  * schedule, a path is the ladder, and going deeper is descending. What changes is what a node
  * costs: nothing is gated on power, a gather is not an effort at all, and only a tree tires you.
  *
  * Nothing here reads a tool. Forestry has no equipment of its own, so a character with an empty
  * inventory can still work a glade, which is what makes the forest a place to go before the mine
  * pays for itself.
  */

/** How a gather ended.
  *
  * `foundPath` is only rolled for a gather that landed, and never for one the character was too
  * tired to attempt, so a path is something the gather earned.
  */
case class HarvestOutcome(
    character: Character,
    item: String,
    failed: Boolean,
    gladeCleared: Boolean,
    foundPath: Boolean,
    amount: Int,
    turns: Int,
    fatigue: Int,
    xp: Int,
    levelsGained: Int
)

/** Going deeper spends the path and turns it into the next glade, mirroring descend in the mine.
  * The depth earned as a shortcut is reported so the caller can say so, absent between milestones.
  */
case class DeeperOutcome(
    character: Character,
    turns: Int,
    fatigue: Int,
    depth: Int,
    unlocked: Option[Int]
)

/** The band one gather can bring back at this depth and this Forestry. */
case class YieldRange(min: Int, max: Int)

object Forest:

  /** The coin flips a gather makes, in the order the TypeScript original makes them: the effort
    * check, then the yield, then the path, then whether the chop tires. The effort check and the
    * tired roll are only consulted for a tiring resource, and the path only while none is held;
    * [[harvestNodeWith]] ignores what the glade cannot be asking about. Drawn by [[harvestNode]]
    * and passed in here so a test can force any one of them.
    */
  case class HarvestRolls(fatigueCheck: Int, yielding: Double, path: Double, tiring: Double)

  def isFastTravelDepth(depth: Int, forest: ForestConfig): Boolean =
    depth % forest.fastTravel.everyLevels == 0

  /** What the entry picker offers: the first depth, which everyone can always reach, plus every
    * shortcut earned so far. Deduped because the starting depth is not itself an unlock, but
    * nothing stops a save listing it anyway.
    */
  def entryDepths(character: Character): List[Int] =
    (Character.StartingForestLevel :: character.unlockedForestLevels).distinct.sorted

  /** Each completed levelsPerPoint levels of Forestry above the depth you are at earns +1, and each
    * completed group below costs -1, so a character who has outgrown their depth forages well and
    * one who has fallen behind struggles.
    *
    * Identical in shape to mineDepthFactor, and for the same reasons: truncating keeps the two
    * directions symmetric, and clamping stops a large gap swinging the result further than the
    * configured maximum. Named for the depth it reads against rather than the skill it reads, since
    * it is the depth that supplies the difficulty.
    */
  def forestDepthFactor(character: Character, forest: ForestConfig): Int =
    val gap    = character.skills.forestry.level - character.forestLevel
    val factor = (gap / forest.depthFactor.levelsPerPoint).toInt
    math.min(forest.depthFactor.max, math.max(-forest.depthFactor.max, factor))

  /** Whether a resource the character can work. Everything in a glade is workable, since nothing in
    * the forest is gated on power; this exists so the view and the action agree, and so a
    * hand-edited save naming something the schedule does not produce fails loudly.
    */
  def canHarvest(item: String, config: GameConfig): Boolean =
    config.forest.resources.contains(item)

  /** The turns one gather or chop costs, which comes from the resource and never from Forestry.
    * Working a glade well means being able to reach the deeper parts of it, not being quicker than
    * everyone else. An item the forest does not produce is an error rather than a cost.
    */
  def turnsFor(item: String, config: GameConfig): Either[GameError, Int] =
    resourceFor(item, config).map(_.turns)

  /** How much one gather brings back, at this depth and this Forestry. The configured range is the
    * base. The depth factor shifts the roll rather than the range, so an over-leveled forager sees
    * a wider band of amounts and an under-leveled one a narrower band, while the floor keeps every
    * gather worth at least the configured minimum. That is the same lever the mine pulls on
    * fatigue, pointed at output instead of effort. Max is floored to min so a factor running well
    * below the base still leaves a range to roll rather than an empty one.
    */
  def yieldRange(
      item: String,
      character: Character,
      config: GameConfig
  ): Either[GameError, YieldRange] =
    resourceFor(item, config).map(yieldRangeOf(_, character, config.forest))

  /** The chance a landed gather tires the character, resource included. The depth factor sets a
    * base chance and the tier layers a multiplier on top, so a deeper resource is more wearing: the
    * multiplier is floored so the factor can never drive it negative, and the result is clamped to
    * the 0-1 range a chance needs.
    */
  def fatigueChanceFor(
      item: String,
      character: Character,
      config: GameConfig
  ): Either[GameError, Double] =
    resourceFor(item, config).map(fatigueChanceOf(_, character, config.forest))

  /** The chance a landed gather turns up the path deeper in. Rolled per gather rather than per
    * glade, so how deep the forest goes is a function of how long a character is willing to work it
    * rather than a single roll they can restart until it comes up well. The depth factor widens the
    * chance for a forager who has outgrown their depth.
    */
  def pathChanceFor(character: Character, config: GameConfig): Double =
    val chance = config.forest.pathChance.base +
      config.forest.pathChance.perDepthFactorPoint * forestDepthFactor(character, config.forest)
    math.min(1, math.max(0, chance))

  /** The nodes on a glade are generated when the character arrives at a depth, so two visits to the
    * same depth are two different glades. Which resources can appear, and in what proportion, is
    * resolved by depth before this runs; see [[Schedule.weightsAtLevel]].
    */
  def enterForest[F[_]: Monad](character: Character, depth: Int, config: GameConfig)(using
      random: Random[F]
  ): F[Character] =
    Schedule
      .generateFloor[F](
        Schedule.weightsAtLevel(depth, config.forest.schedule),
        config.forest.glade
      )
      .map(nodes => character.copy(forestLevel = depth, hasForestPath = false, forestNodes = nodes))

  def canGoDeeper(character: Character): Boolean = character.hasForestPath

  /** Descending spends the path and the configured price, mirroring the mine's descend. The glade
    * rolled is the whole schedule for the depth being arrived at; nodes left behind stay left
    * behind, since the glade below is its own place rather than a continuation.
    */
  def goDeeper[F[_]: Monad](character: Character, config: GameConfig)(using
      random: Random[F]
  ): F[DeeperOutcome] =
    val path     = config.forest.path
    val depth    = character.forestLevel + 1
    val unlocked = if isFastTravelDepth(depth, config.forest) then Some(depth) else None

    for
      deepened <- Monad[F].pure {
        Character.addFatigue(
          Character.addTurns(Character.goDeeperForestLevel(character), path.deeperTurns),
          path.deeperFatigue,
          config.play.fatigue.max
        )
      }
      nodes <- Schedule.generateFloor[F](
        Schedule.weightsAtLevel(depth, config.forest.schedule),
        config.forest.glade
      )
      moved      = deepened.copy(forestNodes = nodes)
      withUnlock = unlocked.fold(moved)(value => Character.addForestUnlock(moved, value))
    yield DeeperOutcome(
      character = withUnlock,
      turns = path.deeperTurns,
      fatigue = path.deeperFatigue,
      depth = depth,
      unlocked = unlocked
    )

  /** Working one node of the glade.
    *
    * A gather always lands. Picking berries or a fallen stick is not an effort, so there is no
    * effort check to fail and nothing stops a tired character from eating what they found. A chop
    * does go through the check, because swinging an axe at a tree is work: a character too tired
    * for it spends the turns and gets nothing — no yield, no XP, and no roll for the path, so bad
    * luck costs time without spiralling into a dead end.
    *
    * The two validation failures are the TypeScript original's throws, returned as errors: an item
    * the forest cannot produce (which describes a glade that could not have been rolled) and a node
    * no longer on the glade (which the UI cannot produce, since its buttons come from the same
    * glade).
    */
  def harvestNode[F[_]: Monad](character: Character, item: String, config: GameConfig)(using
      random: Random[F]
  ): F[Either[GameError, HarvestOutcome]] =
    validateHarvest(character, item, config) match
      case Left(error) => Monad[F].pure(Left(error))
      case Right(_)    =>
        // The original's roll for the effort check: floor(random * rollMax) + 1.
        for
          check <- random.nextDouble.map(d =>
            (math.floor(d * config.play.fatigue.rollMax).toInt) + 1
          )
          yielding <- random.nextDouble
          path     <- random.nextDouble
          tiring   <- random.nextDouble
        yield harvestNodeWith(
          character,
          item,
          config,
          HarvestRolls(check, yielding, path, tiring)
        )

  /** The gather body with its coin flips already drawn. Repeats the validation the effectful
    * wrapper performs, so a test can drive every branch without a random source.
    */
  private[janscape] def harvestNodeWith(
      character: Character,
      item: String,
      config: GameConfig,
      rolls: HarvestRolls
  ): Either[GameError, HarvestOutcome] =
    validateHarvest(character, item, config).map { resource =>
      val turns = resource.turns

      val tooTired =
        resource.tiring && !Fatigue
          .checkFatigue(character.fatigue, config.play, rolls.fatigueCheck)
          .success

      if tooTired then
        HarvestOutcome(
          character = Character.addTurns(character, turns),
          item = item,
          failed = true,
          gladeCleared = false,
          foundPath = false,
          amount = 0,
          turns = turns,
          fatigue = 0,
          xp = 0,
          levelsGained = 0
        )
      else
        // Turns are the XP, which is what the mine does per swing and what crafting does per
        // item: work is measured the same way everywhere.
        val award        = Xp.grantXp(character.skills.forestry, turns, config.skills)
        val range        = yieldRangeOf(resource, character, config.forest)
        val span         = range.max - range.min + 1
        val amount       = range.min + math.floor(rolls.yielding * span).toInt
        val floor        = Character.removeItem(character.forestNodes, item, 1)
        val gladeCleared = Character.totalItems(floor) == 0

        // Rolled only once the work has landed, so the path is something the gather earned
        // rather than something it can turn up on the way to failing. Emptying the glade always
        // uncovers the way deeper, so unlucky path rolls cannot strand a character on a depth
        // they have cleared.
        val rolled    = !character.hasForestPath && rolls.path < pathChanceFor(character, config)
        val foundPath = rolled || (gladeCleared && !character.hasForestPath)

        // Only a tiring resource can tire you, so a gather reports 0 here and never consults the
        // chance at all.
        val fatigue =
          if resource.tiring && rolls.tiring < fatigueChanceOf(resource, character, config.forest)
          then config.forest.chop.fatigue
          else 0

        val next = Character.addFatigue(
          Character.addTurns(
            character.copy(
              skills = character.skills.updated(SkillName.Forestry, award.skill),
              forestNodes = floor,
              inventory = Character.addItem(character.inventory, item, amount)
            ),
            turns
          ),
          fatigue,
          config.play.fatigue.max
        )

        HarvestOutcome(
          character = if foundPath then Character.setForestPath(next, true) else next,
          item = item,
          failed = false,
          gladeCleared = gladeCleared,
          foundPath = foundPath,
          amount = amount,
          turns = turns,
          fatigue = fatigue,
          xp = turns,
          levelsGained = award.levelsGained
        )
    }

  private def validateHarvest(
      character: Character,
      item: String,
      config: GameConfig
  ): Either[GameError, ForestResourceConfig] =
    resourceFor(item, config).flatMap { resource =>
      if Schedule.nodeCount(character.forestNodes, item) == 0 then
        Left(
          GameError.InvalidAction(s"No $item left on forest depth ${character.forestLevel}")
        )
      else Right(resource)
    }

  private def resourceFor(
      item: String,
      config: GameConfig
  ): Either[GameError, ForestResourceConfig] =
    config.forest.resources
      .get(item)
      .toRight(GameError.InvalidAction(s"$item is not something the forest produces"))

  private def yieldRangeOf(
      resource: ForestResourceConfig,
      character: Character,
      forest: ForestConfig
  ): YieldRange =
    val shift = forest.yieldFactor.perDepthFactorPoint * forestDepthFactor(character, forest)
    val floor =
      math.max(forest.yieldFactor.min, math.round(resource.`yield`.min + shift).toInt)
    YieldRange(min = floor, max = math.max(floor, math.round(resource.`yield`.max + shift).toInt))

  private def fatigueChanceOf(
      resource: ForestResourceConfig,
      character: Character,
      forest: ForestConfig
  ): Double =
    val fatigueChance = forest.fatigueChance
    val chance        =
      (fatigueChance.base - fatigueChance.perDepthFactorPoint * forestDepthFactor(
        character,
        forest
      )) *
        math.max(fatigueChance.minFactor, 1 + fatigueChance.perTier * resource.tier)
    math.min(1, math.max(0, chance))
