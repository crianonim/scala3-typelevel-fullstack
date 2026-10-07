package com.crianonim.janscape

import cats.effect.*
import cats.effect.std.Random
import org.scalajs.dom
import scala.scalajs.js
import tyrian.*
import tyrian.Html.*

import com.crianonim.ui.{Button, Card, FileInput, Input}

/** The Janscape tab: a Tyrian reimplementation of the Next.js app's screens (main menu, play hub,
  * mine, craft, build, forest) on top of the shared mechanics in `common`.
  *
  * The split mirrors the original: `update` runs every effectful action (the mining and forest
  * rolls) against `Random[IO]` and persists the resulting character; `view` only renders the model
  * and emits messages. The character autosaves to localStorage under `janscape:character` after
  * every mutation, and every screen shows the last action or error in the player's terms.
  */
object JanscapeApp:

  private val SaveKey = "janscape:character"

  /** Internal screens, the counterpart of the original's `/`, `/play`, `/mine`, `/craft`, `/build`
    * and `/forest` routes. Entering the mine or the forest always starts at the entry picker, as a
    * page remount did in the original.
    */
  enum Screen:
    case Menu, Play, Mine, Craft, Build, Forest

  case class Model(
      config: GameConfig,
      character: Option[Character],
      screen: Screen,
      mineEntry: Option[Int],
      forestEntry: Option[Int],
      showOptions: Boolean,
      nameInput: String,
      nameError: Option[String],
      craftAmounts: Map[String, String],
      lastAction: Option[String],
      error: Option[String]
  )

  enum Msg:
    case Loaded(saved: Option[Character])
    case SetName(value: String)
    case NewCharacter
    case GoTo(next: Screen)
    case ToggleOptions
    case ImportCharacter(file: dom.File)
    case CharacterLoaded(result: Either[GameError, Character])
    case ExportCharacter
    case Equip(item: String)
    case Rest
    case SetCraftAmount(item: String, value: String)
    case Craft(item: String)
    case Build(name: String)
    case EnterMine(level: Int)
    case EnteredMine(level: Int, character: Character)
    case MineNode(item: String)
    case Mined(result: Either[GameError, MineOutcome])
    case Descend
    case Descended(outcome: DescendOutcome)
    case EnterForest(depth: Int)
    case EnteredForest(depth: Int, character: Character)
    case Harvest(item: String)
    case Harvested(result: Either[GameError, HarvestOutcome])
    case GoDeeper
    case Deepened(outcome: DeeperOutcome)

  def init: Model = Model(
    config = GameConfig.default,
    character = None,
    screen = Screen.Menu,
    mineEntry = None,
    forestEntry = None,
    showOptions = false,
    nameInput = "",
    nameError = None,
    craftAmounts = Map.empty,
    lastAction = None,
    error = None
  )

  /** Reads the autosave; wired from `App.init`. A missing or unreadable save simply leaves the menu
    * without a continue button.
    */
  def initCmd: Cmd[IO, Msg] =
    Cmd.Run(IO(LS.get(SaveKey).flatMap(raw => Character.parse(raw).toOption)))(Msg.Loaded.apply)

  private object LS:
    def get(key: String): Option[String] =
      Option(dom.window.localStorage.getItem(key))
    def set(key: String, value: String): Unit =
      dom.window.localStorage.setItem(key, value)

  private def autosaveCmd(character: Character): Cmd[IO, Msg] =
    Cmd.SideEffect[IO, Unit](IO(LS.set(SaveKey, Character.encode(character))))

  /** The single way a mutated character enters the model: replaces it, clears the previous error
    * and autosaves. `note` keeps the previous one when absent, so a silent action (an equipment
    * swap) does not erase the last reported outcome.
    */
  private def applied(
      model: Model,
      character: Character,
      note: Option[String]
  ): (Model, Cmd[IO, Msg]) =
    (
      model.copy(
        character = Some(character),
        error = None,
        lastAction = note.orElse(model.lastAction)
      ),
      autosaveCmd(character)
    )

  private def failed(model: Model, error: GameError): (Model, Cmd[IO, Msg]) =
    (model.copy(error = Some(error.message)), Cmd.None)

  // ============ UPDATE ============

  def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = {
    case Msg.Loaded(saved) =>
      (model.copy(character = saved), Cmd.None)

    case Msg.SetName(value) =>
      (model.copy(nameInput = value, nameError = None), Cmd.None)

    case Msg.NewCharacter =>
      Character.validateName(model.nameInput) match
        case Left(error) =>
          (model.copy(nameError = Some(error)), Cmd.None)
        case Right(name) =>
          val created = Character.createCharacter(name)
          (
            model.copy(
              character = Some(created),
              screen = Screen.Play,
              nameInput = "",
              nameError = None,
              lastAction = None,
              error = None
            ),
            autosaveCmd(created)
          )

    case Msg.GoTo(next) =>
      (
        model.copy(
          screen = next,
          mineEntry = None,
          forestEntry = None,
          showOptions = false,
          lastAction = None,
          error = None
        ),
        Cmd.None
      )

    case Msg.ToggleOptions =>
      (model.copy(showOptions = !model.showOptions, error = None), Cmd.None)

    case Msg.ImportCharacter(file) =>
      (model, FileInput.readFileCmd(file)(raw => Msg.CharacterLoaded(Character.parse(raw))))

    case Msg.CharacterLoaded(result) =>
      result match
        case Left(error)   => failed(model, error)
        case Right(loaded) =>
          val screen = if model.screen == Screen.Menu then Screen.Play else model.screen
          applied(
            model.copy(screen = screen, showOptions = false),
            loaded,
            Some(s"Loaded ${loaded.name}.")
          )

    case Msg.ExportCharacter =>
      model.character match
        case None            => (model, Cmd.None)
        case Some(character) =>
          val json     = Character.encode(character)
          val filename = saveFilename(character)
          (
            model,
            Cmd.SideEffect[IO, Unit](
              IO {
                val blob = new dom.Blob(
                  js.Array(json),
                  dom.BlobPropertyBag(`type` = "application/json")
                )
                val url  = dom.URL.createObjectURL(blob)
                val link = dom.document.createElement("a").asInstanceOf[dom.HTMLAnchorElement]
                link.href = url
                link.download = filename
                dom.document.body.appendChild(link)
                link.click()
                dom.document.body.removeChild(link)
                dom.URL.revokeObjectURL(url)
              }
            )
          )

    case Msg.Equip(item) =>
      model.character match
        case None            => (model, Cmd.None)
        case Some(character) =>
          Equipment.equip(character, item, model.config.equipment) match
            case Right(next) => applied(model, next, None)
            case Left(error) => failed(model, error)

    case Msg.Rest =>
      model.character.filter(Fatigue.canRest) match
        case None            => (model, Cmd.None)
        case Some(character) =>
          val outcome = Fatigue.rest(character, model.config.play)
          applied(
            model,
            outcome.character,
            Some(
              s"You rest for ${outcome.turns} turns. Fatigue is now ${outcome.character.fatigue}."
            )
          )

    case Msg.SetCraftAmount(item, value) =>
      (model.copy(craftAmounts = model.craftAmounts.updated(item, value)), Cmd.None)

    case Msg.Craft(item) =>
      model.character match
        case None            => (model, Cmd.None)
        case Some(character) =>
          val recipes = model.config.crafting.recipes
          val raw     = model.craftAmounts.get(item).flatMap(parseBatch).getOrElse(1)
          val amount  =
            math.min(math.max(1, raw), math.max(1, Crafting.maxCraftable(character, item, recipes)))
          Crafting.craft(character, item, amount, model.config) match
            case Left(error)    => failed(model, error)
            case Right(outcome) =>
              val (next, cmd) = applied(model, outcome.character, Some(describeCraft(outcome)))
              (next.copy(craftAmounts = next.craftAmounts.updated(item, "1")), cmd)

    case Msg.Build(name) =>
      model.character match
        case None            => (model, Cmd.None)
        case Some(character) =>
          Building.build(character, name, model.config) match
            case Right(outcome) => applied(model, outcome.character, Some(describeBuild(outcome)))
            case Left(error)    => failed(model, error)

    // ---- Mine (effectful: the floor roll and the swings are random) ----

    case Msg.EnterMine(level) =>
      model.character match
        case None            => (model, Cmd.None)
        case Some(character) =>
          val config = model.config
          (
            model,
            Cmd.Run {
              Random.scalaUtilRandom[IO].flatMap { implicit r =>
                Mining.enterMine[IO](character, level, config)
              }
            }(next => Msg.EnteredMine(level, next))
          )

    case Msg.EnteredMine(level, next) =>
      val (updated, cmd) = applied(model, next, Some(s"You enter the mine at level $level."))
      (updated.copy(mineEntry = Some(level)), cmd)

    case Msg.MineNode(item) =>
      model.character match
        case None            => (model, Cmd.None)
        case Some(character) =>
          val config = model.config
          (
            model,
            Cmd.Run {
              Random.scalaUtilRandom[IO].flatMap { implicit r =>
                Mining.mineNode[IO](character, item, config)
              }
            }(Msg.Mined.apply)
          )

    case Msg.Mined(result) =>
      result match
        case Right(outcome) => applied(model, outcome.character, Some(describeMine(outcome)))
        case Left(error)    => failed(model, error)

    case Msg.Descend =>
      model.character.filter(Mining.canDescend) match
        case None            => (model, Cmd.None)
        case Some(character) =>
          val config = model.config
          (
            model,
            Cmd.Run {
              Random.scalaUtilRandom[IO].flatMap { implicit r =>
                Mining.descend[IO](character, config)
              }
            }(Msg.Descended.apply)
          )

    case Msg.Descended(outcome) =>
      applied(model, outcome.character, Some(describeDescend(outcome)))

    // ---- Forest (effectful: the glade roll and the gathers are random) ----

    case Msg.EnterForest(depth) =>
      model.character match
        case None            => (model, Cmd.None)
        case Some(character) =>
          val config = model.config
          (
            model,
            Cmd.Run {
              Random.scalaUtilRandom[IO].flatMap { implicit r =>
                Forest.enterForest[IO](character, depth, config)
              }
            }(next => Msg.EnteredForest(depth, next))
          )

    case Msg.EnteredForest(depth, next) =>
      val (updated, cmd) = applied(model, next, Some(s"You enter the forest at depth $depth."))
      (updated.copy(forestEntry = Some(depth)), cmd)

    case Msg.Harvest(item) =>
      model.character match
        case None            => (model, Cmd.None)
        case Some(character) =>
          val config = model.config
          (
            model,
            Cmd.Run {
              Random.scalaUtilRandom[IO].flatMap { implicit r =>
                Forest.harvestNode[IO](character, item, config)
              }
            }(Msg.Harvested.apply)
          )

    case Msg.Harvested(result) =>
      result match
        case Right(outcome) => applied(model, outcome.character, Some(describeHarvest(outcome)))
        case Left(error)    => failed(model, error)

    case Msg.GoDeeper =>
      model.character.filter(Forest.canGoDeeper) match
        case None            => (model, Cmd.None)
        case Some(character) =>
          val config = model.config
          (
            model,
            Cmd.Run {
              Random.scalaUtilRandom[IO].flatMap { implicit r =>
                Forest.goDeeper[IO](character, config)
              }
            }(Msg.Deepened.apply)
          )

    case Msg.Deepened(outcome) =>
      applied(model, outcome.character, Some(describeDeeper(outcome)))
  }

  // ============ OUTCOME NOTES (the original's player-facing strings) ============

  private def describeMine(outcome: MineOutcome): String =
    val turns = s"+${outcome.turns} turns"
    if outcome.failed then s"Your swing at the ${outcome.item} comes up empty. $turns."
    else
      val ladder =
        if !outcome.foundLadder then ""
        else if outcome.floorCleared then " The floor is cleared and the way down is open."
        else " You uncovered a ladder down!"
      val levelUp =
        if outcome.levelsGained > 0 then
          val plural = if outcome.levelsGained > 1 then "levels " else "level "
          s" Level up! Mining is now $plural${outcome.character.skills(SkillName.Mining).level}."
        else ""
      val fatigue = if outcome.fatigue > 0 then s" +${outcome.fatigue} fatigue." else ""
      s"You mined 1 ${outcome.item}. +${outcome.xp} Mining XP. $turns.$fatigue$ladder$levelUp"

  private def describeHarvest(outcome: HarvestOutcome): String =
    val turns = s"+${outcome.turns} turns"
    if outcome.failed then s"You are too tired to work the ${outcome.item}. $turns."
    else
      val path =
        if !outcome.foundPath then ""
        else if outcome.gladeCleared then " The glade is cleared and the path deeper is open."
        else " You found a path deeper into the forest!"
      val levelUp =
        if outcome.levelsGained > 0 then
          val plural = if outcome.levelsGained > 1 then "levels " else "level "
          s" Level up! Forestry is now $plural${outcome.character.skills(SkillName.Forestry).level}."
        else ""
      val fatigue = if outcome.fatigue > 0 then s" +${outcome.fatigue} fatigue." else ""
      val plural  = if outcome.amount == 1 then "" else "s"
      s"You gathered ${outcome.amount} ${outcome.item}$plural. +${outcome.xp} Forestry XP. $turns.$fatigue$path$levelUp"

  private def describeCraft(outcome: CraftOutcome): String =
    val item    = s"${outcome.amount} ${outcome.item}${if outcome.amount == 1 then "" else "s"}"
    val levelUp =
      if outcome.levelsGained > 0 then
        val plural = if outcome.levelsGained > 1 then "levels " else "level "
        s" Level up! Smithing is now $plural${outcome.character.skills(SkillName.Smithing).level}."
      else ""
    s"You crafted $item. +${outcome.turns} turns, +${outcome.xp} Smithing XP.$levelUp"

  private def describeBuild(outcome: BuildOutcome): String =
    s"You built the ${outcome.name}. +${outcome.turns} turns."

  private def describeDescend(outcome: DescendOutcome): String =
    val unlocked =
      outcome.unlocked.fold("")(level => s" You can now start at level $level directly.")
    s"You climb down to level ${outcome.level}. ${outcome.turns} turns, +${outcome.fatigue} fatigue.$unlocked"

  private def describeDeeper(outcome: DeeperOutcome): String =
    val unlocked =
      outcome.unlocked.fold("")(depth => s" You can now start at depth $depth directly.")
    s"You follow the path to depth ${outcome.depth}. ${outcome.turns} turns, +${outcome.fatigue} fatigue.$unlocked"

  // ============ VIEW HELPERS ============

  private def parseBatch(raw: String): Option[Int] =
    scala.util.Try(raw.trim.toInt).toOption

  private def clampBatch(raw: Option[String], max: Int): Int =
    math.min(math.max(1, raw.flatMap(parseBatch).getOrElse(1)), math.max(1, max))

  private def saveFilename(character: Character): String =
    val slug = slugify(character.name)
    s"${if slug.isEmpty then "character" else slug}.json"

  /** The original's slugify minus the NFKD pass, which Scala.js cannot reach through
    * `java.text.Normalizer`. An all-non-ASCII name slugs to nothing and falls back to "character".
    */
  private def slugify(name: String): String =
    val normalized = name.asInstanceOf[js.Dynamic].normalize("NFKD").asInstanceOf[String]
    normalized.toLowerCase
      .replaceAll("[\u0300-\u036f]", "")
      .replaceAll("[^a-z0-9]+", "-")
      .replaceAll("(^-+)|(-+$)", "")

  private def turnWord(count: Int): String = s"$count turn${if count == 1 then "" else "s"}"

  private def factorLabel(factor: Int): String =
    if factor > 0 then s"+$factor" else factor.toString

  private def distinctNodes(nodes: List[ItemStack]): List[(String, Int)] =
    nodes.map(_.item).distinct.map(item => item -> nodes.filter(_.item == item).map(_.count).sum)

  // ============ VIEW ============

  def view(model: Model): Html[Msg] =
    model.screen match
      case Screen.Menu =>
        div(cls := "flex justify-center py-8")(viewMenu(model))
      case _ =>
        model.character match
          case None =>
            div(cls := "py-8 text-sm text-gray-500")(text("Loading character…"))
          case Some(character) =>
            div(cls := "flex gap-4 items-start")(
              div(cls := "flex-1 flex justify-center")(viewMain(model, character)),
              viewHud(model, character)
            )

  private def viewMain(model: Model, character: Character): Html[Msg] =
    model.screen match
      case Screen.Menu   => div()()
      case Screen.Play   => viewPlay(model, character)
      case Screen.Mine   => viewMine(model, character)
      case Screen.Craft  => viewCraft(model, character)
      case Screen.Build  => viewBuild(model, character)
      case Screen.Forest => viewForest(model, character)

  private def card(title: String, description: String)(content: Html[Msg]*): Html[Msg] =
    div(cls := "w-full max-w-sm")(
      Card.withHeader(title, description)(content*)
    )

  private def note(model: Model): Html[Msg] =
    model.lastAction match
      case Some(value) => div(cls := "text-sm text-gray-600")(text(value))
      case None        => div()()

  private def banner(model: Model): Html[Msg] =
    model.error match
      case Some(value) => div(cls := "text-sm text-red-600")(text(value))
      case None        => div()()

  private def hint(value: String): Html[Msg] =
    div(cls := "text-xs text-gray-500")(text(value))

  private def restButton(character: Character, restTurns: Int): Html[Msg] =
    val label = s"Rest (${turnWord(restTurns)})"
    if Fatigue.canRest(character) then Button.secondary(label, Msg.Rest)
    else Button.disabledButton(label)

  // ---- Main menu ----

  private def viewMenu(model: Model): Html[Msg] =
    card("Janscape", "Create a character, or load a save you exported earlier.")(
      div(cls := "flex flex-col gap-2")(
        banner(model),
        div(cls := "flex flex-col gap-1")(
          div(cls := "text-sm font-medium text-gray-700")(text("Name")),
          Input.interactive(model.nameInput, Msg.SetName.apply),
          div(cls := "text-xs text-gray-500")(text("Letters and numbers only.")),
          model.nameError match
            case Some(error) => div(cls := "text-sm text-red-600")(text(error))
            case None        => div()()
        ),
        Button.primary("New Character", Msg.NewCharacter),
        FileInput.simple(Msg.ImportCharacter.apply, ".json"),
        model.character match
          case Some(character) =>
            Button.secondary(s"Continue as ${character.name}", Msg.GoTo(Screen.Play))
          case None => div()()
      )
    )

  // ---- Play hub (the original's options menu) ----

  private def viewPlay(model: Model, character: Character): Html[Msg] =
    card(character.name, "Playing")(
      div(cls := "flex flex-col gap-4")(
        viewSkills(character, model.config),
        if !model.showOptions then
          div(cls := "flex flex-col gap-2")(
            Button.primary("Mine", Msg.GoTo(Screen.Mine)),
            Button.secondary("Craft", Msg.GoTo(Screen.Craft)),
            Button.secondary("Forest", Msg.GoTo(Screen.Forest)),
            Button.secondary("Build", Msg.GoTo(Screen.Build)),
            Button.secondary("Options", Msg.ToggleOptions)
          )
        else
          div(cls := "flex flex-col gap-2")(
            Button.primary("Export Character", Msg.ExportCharacter),
            FileInput.simple(Msg.ImportCharacter.apply, ".json"),
            Button.secondary("Back", Msg.ToggleOptions),
            Button.secondary("Back to Main Menu", Msg.GoTo(Screen.Menu))
          )
        ,
        banner(model),
        note(model)
      )
    )

  // ---- Mine ----

  private def viewMine(model: Model, character: Character): Html[Msg] =
    model.mineEntry match
      case None    => viewMineEntry(model, character)
      case Some(_) => viewMineFloor(model, character)

  private def viewMineEntry(model: Model, character: Character): Html[Msg] =
    val everyLevels = model.config.mining.fastTravel.everyLevels
    card("Enter the mine", s"${character.name} — pick a level to start at.")(
      div(cls := "flex flex-col gap-4")(
        Mining.entryLevels(character).map { level =>
          if level == Character.StartingMineLevel then
            Button.primary(s"Level $level", Msg.EnterMine(level))
          else Button.secondary(s"Level $level (unlocked)", Msg.EnterMine(level))
        } ++ List(
          Button.secondary("Leave", Msg.GoTo(Screen.Play)),
          hint(
            s"Level $everyLevels earns a shortcut straight back to it, and so does every " +
              s"$everyLevels levels after that. Taking one costs nothing, but ladders still have " +
              "to be found from wherever you start."
          )
        )
      )
    )

  private def viewMineFloor(model: Model, character: Character): Html[Msg] =
    val config      = model.config
    val power       = Mining.miningPower(character, config.equipment)
    val depthFactor = Mining.mineDepthFactor(character, config.mining)
    val ladder      = config.mining.ladder
    val restTurns   = config.play.rest.turns
    val locked      = distinctNodes(character.mineNodes)
      .map((item, _) => item)
      .filterNot(Mining.canMine(_, character, config))
      .map(item => item -> GameConfig.miningPowerFor(item, config.mining.materials).getOrElse(0))

    card(
      s"Mine level ${character.mineLevel}",
      s"${character.name} — mining power $power, depth factor ${factorLabel(depthFactor)}"
    )(
      div(cls := "flex flex-col gap-4")(
        if character.hasMineLadder then
          div(
            cls := "text-sm text-emerald-700 bg-emerald-50 border border-emerald-200 rounded px-3 py-2"
          )(
            text(
              s"You found a ladder down to level ${character.mineLevel + 1}. " +
                "It stays here until you use it."
            )
          )
        else div()(),
        viewSkills(character, config),
        if character.mineNodes.nonEmpty then
          div(cls := "flex flex-col gap-2")(
            distinctNodes(character.mineNodes).map { (item, count) =>
              if Mining.canMine(item, character, config) then
                val turns   = Mining.turnsFor(item, character, config)
                val fatigue =
                  math.round(Mining.fatigueChanceFor(item, character, config) * 100).toInt
                Button.primary(
                  s"Mine $item ($count) · ${turnWord(turns)} · $fatigue% fatigue",
                  Msg.MineNode(item)
                )
              else
                val required = GameConfig.miningPowerFor(item, config.mining.materials).getOrElse(0)
                Button.disabledButton(s"Mine $item ($count) — needs power $required")
            }
          )
        else div(cls := "text-sm text-gray-500")(text("You have cleared this level.")),
        if locked.nonEmpty then
          hint(
            s"This level cannot be cleared until you can break " +
              locked.map((item, power) => s"$item (mining power $power)").mkString(", ") + "."
          )
        else div()(),
        banner(model),
        note(model),
        if Mining.canDescend(character) then
          Button.secondary(s"Go Down (${turnWord(ladder.descendTurns)})", Msg.Descend)
        else Button.disabledButton(s"Go Down (${turnWord(ladder.descendTurns)})"),
        restButton(character, restTurns),
        Button.secondary("Leave Mine", Msg.GoTo(Screen.Play)),
        hint(
          s"You land ${math.round(Fatigue.landChance(character.fatigue, config.play) * 100).toInt}% " +
            s"of swings at your current fatigue. Going down costs ${ladder.descendTurns} turns " +
            s"and ${ladder.descendFatigue} fatigue. Ladders are found on " +
            s"${math.round(ladder.chance * 100).toInt}% of landed swings. A material takes longer " +
            "and tires you more the harder it is, and less as your power outgrows it."
        )
      )
    )

  // ---- Craft ----

  private def viewCraft(model: Model, character: Character): Html[Msg] =
    val config   = model.config
    val recipes  = config.crafting.recipes
    val smithing = character.skills(SkillName.Smithing)
    card(
      "Craft",
      s"${character.name} — smithing level ${smithing.level}, so a batch is capped at ${smithing.level}."
    )(
      div(cls := "flex flex-col gap-4")(
        viewSkills(character, config),
        div(cls := "flex flex-col gap-3")(
          recipes.toList.map { (item, recipe) =>
            val unlocked    = Crafting.isUnlocked(character, item, recipes)
            val max         = Crafting.maxCraftable(character, item, recipes)
            val amount      = clampBatch(model.craftAmounts.get(item), max)
            val turns       = amount * recipe.turns
            val buttonLabel = s"Craft (${turnWord(turns)})"
            val status      =
              if !unlocked then s"Needs Smithing level ${recipe.level}."
              else if max == 0 then
                val held = recipe.inputs.keys
                  .map(input => s"${Character.heldAmount(character, input)} $input")
                  .mkString(", ")
                s"Not enough materials. You have $held."
              else s"You can make up to $max."
            div(cls := "flex flex-col gap-2 rounded-lg border p-3")(
              div(cls := "flex items-baseline justify-between gap-2")(
                div(cls := "font-medium")(text(item)),
                hint(s"${turnWord(recipe.turns)} each")
              ),
              hint(
                s"Needs ${recipe.inputs.toList.map((input, n) => s"$n $input").mkString(", ")}."
              ),
              div(cls := "flex items-center gap-2")(
                Input.interactive(
                  model.craftAmounts.getOrElse(item, "1"),
                  (value: String) => Msg.SetCraftAmount(item, value),
                  "number"
                ),
                if unlocked && max > 0 then Button.primary(buttonLabel, Msg.Craft(item))
                else Button.disabledButton(buttonLabel)
              ),
              hint(status)
            )
          }
        ),
        banner(model),
        note(model),
        Button.secondary("Leave Craft", Msg.GoTo(Screen.Play)),
        hint(
          "Crafting spends turns and earns the same amount of Smithing XP. Every recipe is " +
            "available from the start; the Smithing level only caps how large a batch can be."
        )
      )
    )

  // ---- Build ----

  private def viewBuild(model: Model, character: Character): Html[Msg] =
    val config     = model.config
    val buildables = config.building.buildables
    val built      = character.buildings.length
    val builtLabel =
      if built == 0 then "no workstations built yet"
      else s"$built workstation${if built == 1 then "" else "s"} built"
    card("Build", s"${character.name} — $builtLabel.")(
      div(cls := "flex flex-col gap-4")(
        viewSkills(character, config),
        div(cls := "flex flex-col gap-3")(
          buildables.toList.map { (name, buildable) =>
            val alreadyBuilt = Building.isBuilt(character, name)
            val ready        = Building.canBuild(character, name, buildables)
            val status       =
              if alreadyBuilt then "Only one can exist."
              else if ready then "Ready to build."
              else
                val held = buildable.inputs.keys
                  .map(input => s"${Character.heldAmount(character, input)} $input")
                  .mkString(", ")
                s"Not enough materials. You have $held."
            div(cls := "flex flex-col gap-2 rounded-lg border p-3")(
              div(cls := "flex items-baseline justify-between gap-2")(
                div(cls := "font-medium")(text(name)),
                hint(turnWord(buildable.turns))
              ),
              hint(
                s"Needs ${buildable.inputs.toList.map((item, n) => s"$n $item").mkString(", ")}."
              ),
              if alreadyBuilt then Button.disabledButton("Built")
              else if ready then
                Button.primary(s"Build (${turnWord(buildable.turns)})", Msg.Build(name))
              else Button.disabledButton(s"Build (${turnWord(buildable.turns)})"),
              hint(status)
            )
          }
        ),
        banner(model),
        note(model),
        Button.secondary("Leave Build", Msg.GoTo(Screen.Play)),
        hint(
          "Building spends materials and turns and nothing else: no XP, no fatigue. Each " +
            "workstation is a one-off, and only one of each can exist."
        )
      )
    )

  // ---- Forest ----

  private def viewForest(model: Model, character: Character): Html[Msg] =
    model.forestEntry match
      case None    => viewForestEntry(model, character)
      case Some(_) => viewForestFloor(model, character)

  private def viewForestEntry(model: Model, character: Character): Html[Msg] =
    val everyLevels = model.config.forest.fastTravel.everyLevels
    card("Enter the forest", s"${character.name} — pick a depth to start at.")(
      div(cls := "flex flex-col gap-4")(
        Forest.entryDepths(character).map { depth =>
          if depth == Character.StartingForestLevel then
            Button.primary(s"Depth $depth", Msg.EnterForest(depth))
          else Button.secondary(s"Depth $depth (unlocked)", Msg.EnterForest(depth))
        } ++ List(
          Button.secondary("Leave", Msg.GoTo(Screen.Play)),
          hint(
            s"Depth $everyLevels earns a shortcut straight back to it, and so does every " +
              s"$everyLevels depths after that. Taking one costs nothing, but paths still have to " +
              "be found from wherever you start."
          )
        )
      )
    )

  private def viewForestFloor(model: Model, character: Character): Html[Msg] =
    val config      = model.config
    val depthFactor = Forest.forestDepthFactor(character, config.forest)
    val path        = config.forest.path
    val restTurns   = config.play.rest.turns
    card(
      s"Forest depth ${character.forestLevel}",
      s"${character.name} — depth factor ${factorLabel(depthFactor)}"
    )(
      div(cls := "flex flex-col gap-4")(
        if character.hasForestPath then
          div(
            cls := "text-sm text-emerald-700 bg-emerald-50 border border-emerald-200 rounded px-3 py-2"
          )(
            text(
              s"You found a path to depth ${character.forestLevel + 1}. " +
                "It stays here until you follow it."
            )
          )
        else div()(),
        viewSkills(character, config),
        if character.forestNodes.nonEmpty then
          div(cls := "flex flex-col gap-2")(
            distinctNodes(character.forestNodes).map { (item, count) =>
              val tiring  = config.forest.resources.get(item).exists(_.tiring)
              val turns   = Forest.turnsFor(item, config).getOrElse(0)
              val range   = Forest.yieldRange(item, character, config).getOrElse(YieldRange(0, 0))
              val fatigue =
                if tiring then
                  math
                    .round(Forest.fatigueChanceFor(item, character, config).getOrElse(0.0) * 100)
                    .toInt
                else 0
              val verb = if tiring then "Chop" else "Gather"
              val worn = if tiring then s" · $fatigue% fatigue" else ""
              Button.primary(
                s"$verb $item ($count) · ${turnWord(turns)} · ${range.min}-${range.max}$worn",
                Msg.Harvest(item)
              )
            }
          )
        else div(cls := "text-sm text-gray-500")(text("You have cleared this glade.")),
        banner(model),
        note(model),
        if Forest.canGoDeeper(character) then
          Button.secondary(s"Go Deeper (${turnWord(path.deeperTurns)})", Msg.GoDeeper)
        else Button.disabledButton(s"Go Deeper (${turnWord(path.deeperTurns)})"),
        restButton(character, restTurns),
        Button.secondary("Leave Forest", Msg.GoTo(Screen.Play)),
        hint(
          s"${math.round(Fatigue.landChance(character.fatigue, config.play) * 100).toInt}% of chops " +
            "land at your current fatigue; gathering never tires you out. Going deeper costs " +
            s"${path.deeperTurns} turns and ${path.deeperFatigue} fatigue. Paths turn up on " +
            s"${math.round(Forest.pathChanceFor(character, config) * 100).toInt}% of gathers, and " +
            "always once a glade is cleared. Your Forestry against the depth decides how much a " +
            "gather brings back and how likely a path is."
        )
      )
    )

  // ---- Skills ----

  private def viewSkills(character: Character, config: GameConfig): Html[Msg] =
    div(cls := "flex flex-col")(
      List[Html[Msg]](
        div(cls := "flex text-xs font-medium text-gray-500 pb-1")(
          div(cls := "flex-1")(text("Skill")),
          div(cls := "w-16 text-right")(text("Level")),
          div(cls := "w-28 text-right")(text("XP"))
        )
      ) ++ SkillNames.map { name =>
        val skill    = character.skills(name)
        val progress = Xp.levelProgress(skill.xp, config.skills)
        div(cls := "flex items-baseline border-t border-gray-100 py-1 text-sm")(
          div(cls := "flex-1 font-medium")(text(name.toString)),
          div(cls := "w-16 text-right tabular-nums")(text(skill.level.toString)),
          div(cls := "w-28 text-right tabular-nums")(
            div()(text(skill.xp.toString)),
            if progress.xpForNextLevel > 0 then
              div(cls := "text-xs text-gray-400")(
                text(s"${math.round(progress.xpForNextLevel).toInt} to ${progress.level + 1}")
              )
            else div()()
          )
        )
      }
    )

  // ---- Status HUD (the original's fixed side panels) ----

  private def hudCard(title: String)(content: Html[Msg]*): Html[Msg] =
    Card.simple(Card.Variant.Default, Card.Padding.Small)(
      div(cls := "flex flex-col gap-2")(
        List[Html[Msg]](
          div(cls := "text-sm font-semibold text-gray-700")(text(title))
        ) ++ content
      )
    )

  private def viewHud(model: Model, character: Character): Html[Msg] =
    val config = model.config
    val built  = config.building.buildables.keys
      .filter(Building.isBuilt(character, _))
      .toList
    div(cls := "w-48 shrink-0 flex flex-col gap-3")(
      hudCard("Turns")(
        div(cls := "text-2xl font-semibold tabular-nums")(text(character.turn.toString))
      ),
      hudCard("Fatigue")(
        div(cls := "flex items-baseline gap-1")(
          div(cls := "text-2xl font-semibold tabular-nums")(text(character.fatigue.toString)),
          div(cls := "text-sm text-gray-500")(text(s"/ ${config.play.fatigue.max}"))
        ),
        div(cls := "bg-gray-200 rounded h-2 overflow-hidden")(
          div(
            cls := "bg-orange-400 h-2 rounded",
            attribute(
              "style",
              s"width: ${math.min(100, math.round(character.fatigue * 100.0 / math.max(1, config.play.fatigue.max)).toInt)}%"
            )
          )()
        )
      ),
      hudCard("Inventory")(
        if character.inventory.isEmpty then div(cls := "text-sm text-gray-500")(text("Empty"))
        else
          div(cls := "flex flex-col gap-1")(
            character.inventory.map(stack =>
              div(cls := "flex justify-between gap-2 text-sm")(
                div(cls := "truncate")(text(stack.item)),
                div(cls := "text-gray-500 tabular-nums")(text(stack.count.toString))
              )
            )
          )
      ),
      hudCard("Equipment")(
        if config.equipment.slots.isEmpty then div(cls := "text-sm text-gray-500")(text("None"))
        else
          div(cls := "flex flex-col gap-3")(
            config.equipment.slots.keys.toList.map { slot =>
              val current = Equipment.equippedItem(character, slot, config.equipment)
              val swaps   = Equipment
                .inventoryItemsForSlot(character, slot, config.equipment)
                .filterNot(current.contains)
              div(cls := "flex flex-col gap-1")(
                List[Html[Msg]](
                  div(cls := "text-xs text-gray-500")(text(slot)),
                  div(cls := "text-sm font-medium truncate")(text(current.getOrElse("Empty")))
                ) ++ swaps.map(item =>
                  Button.secondary(s"Equip $item", Msg.Equip(item), Button.Size.Small)
                )
              )
            }
          )
      ),
      hudCard("Workstations")(
        if built.isEmpty then div(cls := "text-sm text-gray-500")(text("None"))
        else
          div(cls := "flex flex-col gap-1")(
            built.map(name => div(cls := "text-sm truncate")(text(name)))
          )
      )
    )
