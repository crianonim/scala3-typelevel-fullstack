package com.crianonim.janscape

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

/** One stack of one item. Serves the inventory and the mine floor and the forest glade alike. */
case class ItemStack(item: String, count: Int)

object ItemStack:
  given Encoder[ItemStack] = deriveEncoder
  given Decoder[ItemStack] = deriveDecoder

case class Character(
    name: String,
    skills: Skills,
    inventory: List[ItemStack],
    equipment: Map[String, String],
    buildings: List[String],
    turn: Int,
    fatigue: Int,
    mineLevel: Int,
    hasMineLadder: Boolean,
    unlockedMineLevels: List[Int],
    mineNodes: List[ItemStack],
    forestLevel: Int,
    hasForestPath: Boolean,
    unlockedForestLevels: List[Int],
    forestNodes: List[ItemStack]
):

  /** Rejects nonsense from a hand-edited save, mirroring the zod schema's ranges. Runs before
    * [[Character.normalized]], which silently repairs what is repairable.
    */
  def validate: List[Issue] =
    Issue.when("name", name.length >= 1, "must be at least 1 character") ++
      Issue.when(
        "name",
        name.length <= Character.MaxNameLength,
        s"must be at most ${Character.MaxNameLength} characters"
      ) ++
      skillIssues(skills.mining, "skills.mining") ++
      skillIssues(skills.combat, "skills.combat") ++
      skillIssues(skills.smithing, "skills.smithing") ++
      skillIssues(skills.forestry, "skills.forestry") ++
      stackIssues(inventory, "inventory") ++
      stackIssues(mineNodes, "mineNodes") ++
      stackIssues(forestNodes, "forestNodes") ++
      Issue.when("turn", turn >= 0, "must be >= 0") ++
      Issue.when(
        "fatigue",
        fatigue >= 0 && fatigue <= Character.MaxSaveFatigue,
        s"must be between 0 and ${Character.MaxSaveFatigue}"
      ) ++
      Issue.when("mineLevel", mineLevel >= 1, "must be >= 1") ++
      Issue.when("forestLevel", forestLevel >= 1, "must be >= 1") ++
      unlockIssues(unlockedMineLevels, "unlockedMineLevels") ++
      unlockIssues(unlockedForestLevels, "unlockedForestLevels")

  /** What the zod original does with its transforms: stacks merge on write so a hand-edited save
    * with duplicate or emptied stacks cannot show the same item twice, unlocks sort and dedupe so
    * the picker renders one button per depth, and duplicated buildings collapse.
    */
  def normalized: Character =
    copy(
      inventory = Character.mergeStacks(inventory),
      buildings = buildings.distinct,
      unlockedMineLevels = Character.sortLevels(unlockedMineLevels),
      unlockedForestLevels = Character.sortLevels(unlockedForestLevels),
      mineNodes = Character.mergeStacks(mineNodes),
      forestNodes = Character.mergeStacks(forestNodes)
    )

  private def skillIssues(skill: Skill, path: String): List[Issue] =
    Issue.when(
      s"$path.level",
      skill.level >= 1 && skill.level <= MaxLevel,
      s"must be between 1 and $MaxLevel"
    ) ++
      Issue.when(s"$path.xp", skill.xp >= 0, "must be >= 0")

  private def stackIssues(nodes: List[ItemStack], path: String): List[Issue] =
    nodes.zipWithIndex.flatMap { case (stack, index) =>
      Issue.when(s"$path.$index.item", stack.item.nonEmpty, "must be at least 1 character") ++
        Issue.when(s"$path.$index.count", stack.count >= 0, "must be >= 0")
    }

  private def unlockIssues(levels: List[Int], path: String): List[Issue] =
    levels.zipWithIndex.flatMap { case (level, index) =>
      Issue.when(s"$path.$index", level >= Character.StartingMineLevel, "must be >= 1")
    }

object Character:
  val MaxNameLength     = 24
  val MaxInventoryBytes = 64 * 1024

  val StartingTurns     = 0
  val StartingFatigue   = 0
  val StartingMineLevel = 1

  /** The depth the character starts at in the forest. The forest calls it depth where the mine
    * calls it a level, but the save field is numbered the same way, so one unlock list and one
    * depth schedule can serve both.
    */
  val StartingForestLevel = 1

  /** The slot whose equipped tool grants mining power. Slots and the power their items carry live
    * in config, but which slot the mine reads is a structural fact, so it lives here beside the
    * starting loadout that also names it.
    */
  val MiningToolSlot = "Mining Tool"

  val StartingEquipment: Map[String, String] = Map(MiningToolSlot -> "stone pickaxe")

  /** Save-format guard only, deliberately far above any real cap: its job is rejecting nonsense
    * from a hand-edited save, not describing how tired a character may get. The gameplay cap is
    * play.fatigue.max in config/game.json.
    */
  val MaxSaveFatigue = 1000

  def createCharacter(name: String): Character =
    Character(
      name = name.trim,
      skills = Skills.starting,
      inventory = Nil,
      equipment = StartingEquipment,
      buildings = Nil,
      turn = StartingTurns,
      fatigue = StartingFatigue,
      mineLevel = StartingMineLevel,
      hasMineLadder = false,
      unlockedMineLevels = Nil,
      mineNodes = Nil,
      forestLevel = StartingForestLevel,
      hasForestPath = false,
      unlockedForestLevels = Nil,
      forestNodes = Nil
    )

  /** A ladder belongs to the level it was found on, so descending clears it and there is never more
    * than one outstanding at a time.
    */
  def setMineLadder(character: Character, hasMineLadder: Boolean): Character =
    character.copy(hasMineLadder = hasMineLadder)

  def goDownMineLevel(character: Character): Character =
    character.copy(mineLevel = character.mineLevel + 1, hasMineLadder = false)

  def addMineUnlock(character: Character, level: Int): Character =
    if character.unlockedMineLevels.contains(level) then character
    else character.copy(unlockedMineLevels = sortLevels(character.unlockedMineLevels :+ level))

  /** A path belongs to the depth it was found on, so going deeper clears it. The mirror of
    * setMineLadder.
    */
  def setForestPath(character: Character, hasForestPath: Boolean): Character =
    character.copy(hasForestPath = hasForestPath)

  def goDeeperForestLevel(character: Character): Character =
    character.copy(forestLevel = character.forestLevel + 1, hasForestPath = false)

  def addForestUnlock(character: Character, level: Int): Character =
    if character.unlockedForestLevels.contains(level) then character
    else character.copy(unlockedForestLevels = sortLevels(character.unlockedForestLevels :+ level))

  /** Actions cost turns, so this is the single place they are charged. Clamped at zero so a
    * negative cost can never corrupt a save.
    */
  def addTurns(character: Character, turns: Double): Character =
    character.copy(turn = math.max(0, character.turn + math.floor(turns).toInt))

  /** Clamped so exhausting actions cannot push past the configured cap, and resting can never push
    * below zero. Read from the character as well as the cap, so a save written under a higher cap
    * settles down here rather than needing a migration.
    */
  def addFatigue(character: Character, fatigue: Double, max: Int): Character =
    val total = character.fatigue + math.floor(fatigue).toInt
    character.copy(fatigue = math.min(max, math.max(0, total)))

  def addItem(inventory: List[ItemStack], item: String, amount: Int): List[ItemStack] =
    inventory.indexWhere(_.item == item) match
      case -1 =>
        inventory :+ ItemStack(item, amount)
      case index =>
        val existing = inventory(index)
        inventory.updated(index, existing.copy(count = existing.count + amount))

  /** How many of a single item the inventory holds. Shared by the checks that size a craft or a
    * build against what is on hand.
    */
  def heldAmount(character: Character, item: String): Int =
    character.inventory.find(_.item == item).map(_.count).getOrElse(0)

  def totalItems(inventory: List[ItemStack]): Int =
    inventory.map(_.count).sum

  /** Spending a stack. An emptied stack is dropped rather than left at zero, which is what merging
    * does on parse, so a save round-trip cannot disagree with what is held in memory. Asking to
    * remove more than is there removes what is there, rather than going negative.
    */
  def removeItem(inventory: List[ItemStack], item: String, amount: Int): List[ItemStack] =
    inventory.indexWhere(_.item == item) match
      case -1    => inventory
      case index =>
        val left = inventory(index).count - amount
        if left <= 0 then
          inventory.zipWithIndex.collect { case (stack, at) if at != index => stack }
        else inventory.updated(index, inventory(index).copy(count = left))

  def validateName(raw: String): Either[String, String] =
    val name = raw.trim

    if name.isEmpty then Left("Name is required.")
    else if name.length > MaxNameLength then
      Left(s"Name must be $MaxNameLength characters or fewer.")
    else if !name.exists(java.lang.Character.isLetterOrDigit) then
      Left("Name must contain at least one letter or number.")
    else Right(name)

  def encode(character: Character): String =
    Encoder[Character].apply(character).spaces2

  def parse(raw: String): Either[GameError, Character] =
    io.circe.parser.parse(raw) match
      case Left(_) =>
        Left(GameError.NotJson)
      case Right(json) =>
        json.as[Character] match
          case Left(failure) =>
            Left(GameError.InvalidSave(List(Issue("", describe(failure)))))
          case Right(character) =>
            character.validate match
              case Nil    => Right(character.normalized)
              case issues => Left(GameError.InvalidSave(issues))

  given Encoder[Character] = deriveEncoder

  /** Defaults every field the zod original defaults, so saves written before an feature existed
    * still load: inventory, equipment, buildings, turn, fatigue and all the depth state arrive at
    * their starting values when absent. `skills` itself is required, but each skill inside it
    * defaults individually.
    */
  given Decoder[Character] = Decoder.instance { c =>
    def stacks(name: String): Decoder.Result[List[ItemStack]] =
      c.downField(name).as[Option[List[ItemStack]]].map(_.getOrElse(Nil))

    def ints(name: String): Decoder.Result[List[Int]] =
      c.downField(name).as[Option[List[Int]]].map(_.getOrElse(Nil))

    def int(name: String, default: Int): Decoder.Result[Int] =
      c.downField(name).as[Option[Int]].map(_.getOrElse(default))

    def bool(name: String, default: Boolean): Decoder.Result[Boolean] =
      c.downField(name).as[Option[Boolean]].map(_.getOrElse(default))

    for
      name      <- c.get[String]("name")
      skills    <- c.get[Skills]("skills")
      inventory <- stacks("inventory")
      equipment <- c
        .downField("equipment")
        .as[Option[Map[String, String]]]
        .map(_.getOrElse(StartingEquipment))
      buildings          <- c.downField("buildings").as[Option[List[String]]].map(_.getOrElse(Nil))
      turn               <- int("turn", StartingTurns)
      fatigue            <- int("fatigue", StartingFatigue)
      mineLevel          <- int("mineLevel", StartingMineLevel)
      hasMineLadder      <- bool("hasMineLadder", false)
      unlockedMineLevels <- ints("unlockedMineLevels")
      mineNodes          <- stacks("mineNodes")
      forestLevel        <- int("forestLevel", StartingForestLevel)
      hasForestPath      <- bool("hasForestPath", false)
      unlockedForestLevels <- ints("unlockedForestLevels")
      forestNodes          <- stacks("forestNodes")
    yield Character(
      name = name.trim,
      skills = skills,
      inventory = inventory,
      equipment = equipment,
      buildings = buildings,
      turn = turn,
      fatigue = fatigue,
      mineLevel = mineLevel,
      hasMineLadder = hasMineLadder,
      unlockedMineLevels = unlockedMineLevels,
      mineNodes = mineNodes,
      forestLevel = forestLevel,
      hasForestPath = hasForestPath,
      unlockedForestLevels = unlockedForestLevels,
      forestNodes = forestNodes
    )
  }

  private[janscape] def mergeStacks(inventory: List[ItemStack]): List[ItemStack] =
    val totals = inventory.groupBy(_.item).view.mapValues(_.map(_.count).sum).toMap
    inventory
      .map(_.item)
      .distinct
      .map(item => ItemStack(item, totals(item)))
      .filter(_.count > 0)

  private[janscape] def sortLevels(levels: List[Int]): List[Int] =
    levels.distinct.sorted
