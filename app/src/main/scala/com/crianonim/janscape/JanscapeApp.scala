package com.crianonim.janscape

import cats.effect.*
import cats.effect.std.Random
import org.scalajs.dom
import scala.scalajs.js
import tyrian.*
import tyrian.Html.*

import com.crianonim.shadcn.Icons
import com.crianonim.ui.{Button, Card, FileInput, Input, Tooltip}

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

  /** The collapsible HUD sections: a closed section is a single button in the top row and opens
    * into its full list when pressed.
    */
  enum HudPane:
    case Inventory, Equipment, Workstations

  case class Model(
      config: GameConfig,
      character: Option[Character],
      screen: Screen,
      mineEntry: Option[Int],
      forestEntry: Option[Int],
      hudOpen: Set[HudPane],
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
    case ToggleHud(pane: HudPane)
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
    hudOpen = Set.empty,
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

  /** Effectful entry into the mine: the floor roll decides the nodes. Shared by the level picker
    * (Msg.EnterMine) and the direct entry when only the starting level is unlocked (Msg.GoTo).
    */
  private def enterMine(model: Model, level: Int): (Model, Cmd[IO, Msg]) =
    model.character match
      case None => (model, Cmd.None)
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

  /** Effectful entry into the forest, mirroring `enterMine`.
    */
  private def enterForest(model: Model, depth: Int): (Model, Cmd[IO, Msg]) =
    model.character match
      case None => (model, Cmd.None)
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
      val updated = model.copy(
        screen = next,
        mineEntry = None,
        forestEntry = None,
        showOptions = false,
        lastAction = None,
        error = None
      )
      model.character match
        case Some(character) if next == Screen.Mine && Mining.entryLevels(character).size == 1 =>
          enterMine(updated, Mining.entryLevels(character).head)
        case Some(character) if next == Screen.Forest && Forest.entryDepths(character).size == 1 =>
          enterForest(updated, Forest.entryDepths(character).head)
        case _ => (updated, Cmd.None)

    case Msg.ToggleHud(pane) =>
      (
        model.copy(
          hudOpen = if model.hudOpen.contains(pane) then model.hudOpen - pane else model.hudOpen + pane
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

    case Msg.EnterMine(level) => enterMine(model, level)

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

    case Msg.EnterForest(depth) => enterForest(model, depth)

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
            val config = model.config
            div(cls := "flex flex-col gap-4")(
              div(cls := "flex justify-center")(viewStatusBar(character, config)),
              div(cls := "flex flex-col gap-4 md:flex-row md:items-start")(
                div(cls := "flex-1 flex justify-center")(viewMain(model, character)),
                viewHud(model, character)
              )
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

  /** Same as `card`, but the header is one row: title (and optional description) on the left and a
    * small back-arrow button on the far right. The arrow returns to the play hub; the tooltip
    * spells out the full `leaveLabel` ("Leave Mine", "Leave Craft", …).
    */
  private def cardWithLeave(
      title: String,
      description: String,
      leaveLabel: String
  )(content: Html[Msg]*): Html[Msg] =
    div(cls := "w-full max-w-sm")(
      Card.apply(Card.Props())(
        Some(
          div(cls := "flex items-center justify-between gap-2")(
            div(cls := "flex min-w-0 flex-col gap-1")(
              h2(cls := "truncate text-xl font-semibold")(text(title)),
              if description.nonEmpty then p(cls := "text-gray-600")(text(description)) else div()()
            ),
            Tooltip.text(leaveLabel, backButton)
          )
        ),
        content*
      )
    )

  private val backButton: Html[Msg] =
    iconButton(Msg.GoTo(Screen.Play), Icons.arrowLeft[Msg]("size-4"), "Back to Home Village", false)

  /** A small icon button for the floor action bars (the back arrow, Go Down, Go Deeper). Enabled
    * state is wrapped in `Tooltip.text` so the full action ("Go Down (20 turns)") shows on hover;
    * a disabled button renders plain grey and can't hover.
    */
  private def iconButton(
      msg: Msg,
      icon: Html[Msg],
      tooltip: String,
      disabled: Boolean
  ): Html[Msg] =
    val enabled = !disabled
    val el =
      if enabled then
        button(
          cls := "inline-flex size-9 shrink-0 items-center justify-center rounded-md border border-gray-300 text-gray-700 shadow-sm transition-colors hover:bg-gray-50 active:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2",
          onClick(msg)
        )(icon)
      else
        button(
          cls := "inline-flex size-9 shrink-0 cursor-not-allowed items-center justify-center rounded-md border border-gray-300 bg-gray-200 text-gray-400",
          attribute("disabled", "true")
        )(icon)
    if enabled then Tooltip.text(tooltip, el) else el

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

  private def restZzz(character: Character, restTurns: Int): Html[Msg] =
    if Fatigue.canRest(character) then
      Tooltip.text(
        s"Rest (${turnWord(restTurns)})",
        Button.secondary("Zzz", Msg.Rest, Button.Size.Small)
      )
    else Button.disabledButton("Zzz", Button.Size.Small)

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
    card("Home Village", "Pick an activity.")(
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
    card("Enter the mine", "Pick a level to start at.")(
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
    val nodes =
      distinctNodes(character.mineNodes).map { (item, count) =>
        if Mining.canMine(item, character, config) then
          val turns   = Mining.turnsFor(item, character, config)
          val fatigue =
            math.round(Mining.fatigueChanceFor(item, character, config) * 100).toInt
          Button(
            s"$item ($count) · ${turns}t · $fatigue% fat",
            Msg.MineNode(item),
            Button.Variant.Primary,
            Button.Size.Small,
            "w-full"
          )
        else
          val required = GameConfig.miningPowerFor(item, config.mining.materials).getOrElse(0)
          Button(
            s"$item ($count) · needs power $required",
            Msg.MineNode(item),
            Button.Variant.Disabled,
            Button.Size.Small,
            "w-full"
          )
      }

    cardWithLeave(
      s"Mine ${character.mineLevel}, Power $power, DF ${factorLabel(depthFactor)}",
      "",
      "Leave Mine"
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
          div(cls := "flex flex-col gap-2")(nodes*)
        else div(cls := "text-sm text-gray-500")(text("You have cleared this level.")),
        div(cls := "flex items-center gap-2")(
          restZzz(character, restTurns),
          iconButton(
            Msg.Descend,
            Icons.arrowDown[Msg]("size-4"),
            s"Go Down (${turnWord(ladder.descendTurns)})",
            !Mining.canDescend(character)
          )
        ),
        if locked.nonEmpty then
          hint(
            s"This level cannot be cleared until you can break " +
              locked.map((item, power) => s"$item (mining power $power)").mkString(", ") + "."
          )
        else div()(),
        banner(model),
        note(model),
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
    cardWithLeave(
      "Craft",
      s"Smithing level ${smithing.level} caps a batch at ${smithing.level}.",
      "Leave Craft"
    )(
      div(cls := "flex flex-col gap-4")(
        viewSkills(character, config),
        div(cls := "flex flex-col gap-3")(
          recipes.toList.map { (item, recipe) =>
            val unlocked = Crafting.isUnlocked(character, item, recipes)
            val max      = Crafting.maxCraftable(character, item, recipes)
            val status   =
              if !unlocked then s"Needs Smithing level ${recipe.level}."
              else if max == 0 then
                val held = recipe.inputs.keys
                  .map(input => s"${Character.heldAmount(character, input)} $input")
                  .mkString(", ")
                s"Not enough materials. You have $held."
              else s"You can make up to $max."
            val needs = recipe.inputs.toList.map((input, n) => s"$n $input").mkString(", ")
            Tooltip.expanded(
              div(cls := "flex flex-col text-left whitespace-normal")(
                div()(text(s"${turnWord(recipe.turns)} each, needs $needs.")),
                div(cls := "text-gray-300")(text(status))
              ),
              div(
                cls := "flex w-full items-center justify-between gap-2 rounded-lg border p-2"
              )(
                div(cls := "font-medium whitespace-nowrap")(text(item)),
                div(cls := "flex flex-wrap items-center gap-x-2 text-sm")(
                  recipe.inputs.toList.map { (input, n) =>
                    val have = Character.heldAmount(character, input) >= n
                    span(cls := (if have then "text-emerald-600" else "text-red-600"))(
                      text(s"$n $input")
                    )
                  }*
                ),
                div(cls := "flex items-center gap-2")(
                  div(cls := "w-12")(
                    Input.interactive(
                      model.craftAmounts.getOrElse(item, "1"),
                      (value: String) => Msg.SetCraftAmount(item, value),
                      "number",
                      "px-1 py-1 text-sm"
                    )
                  ),
                  if unlocked && max > 0 then Button.primary("Craft", Msg.Craft(item), Button.Size.Small)
                  else Button.disabledButton("Craft", Button.Size.Small)
                )
              )
            )
          }
        ),
        banner(model),
        note(model),
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
    cardWithLeave("Build", s"${builtLabel.capitalize}.", "Leave Build")(
      div(cls := "flex flex-col gap-4")(
        viewSkills(character, config),
        div(cls := "flex flex-col gap-3")(
          buildables.toList.map { (name, buildable) =>
            val alreadyBuilt = Building.isBuilt(character, name)
            val ready        = Building.canBuild(character, name, buildables)
            val status =
              if alreadyBuilt then "Only one can exist."
              else if ready then "Ready to build."
              else
                val held = buildable.inputs.keys
                  .map(input => s"${Character.heldAmount(character, input)} $input")
                  .mkString(", ")
                s"Not enough materials. You have $held."
            val needs =
              buildable.inputs.toList.map((input, n) => s"$n $input").mkString(", ")
            Tooltip.expanded(
              div(cls := "flex flex-col text-left whitespace-normal")(
                div()(text(s"${turnWord(buildable.turns)} to build, needs $needs.")),
                div(cls := "text-gray-300")(text(status))
              ),
              div(
                cls := "flex w-full items-center justify-between gap-2 rounded-lg border p-2"
              )(
                div(cls := "font-medium whitespace-nowrap")(text(name)),
                div(cls := "flex flex-wrap items-center gap-x-2 text-sm")(
                  buildable.inputs.toList.map { (input, n) =>
                    val have = Character.heldAmount(character, input) >= n
                    span(cls := (if have then "text-emerald-600" else "text-red-600"))(
                      text(s"$n $input")
                    )
                  }*
                ),
                if alreadyBuilt then
                  span(
                    cls := "rounded-full border border-gray-300 bg-gray-100 px-3 py-1 text-sm font-medium text-gray-500"
                  )(text("Done"))
                else if ready then Button.primary("Build", Msg.Build(name), Button.Size.Small)
                else Button.disabledButton("Build", Button.Size.Small)
              )
            )
          }
        ),
        banner(model),
        note(model),
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
    card("Enter the forest", "Pick a depth to start at.")(
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
    val power       = Forest.forestryPower(character, config.equipment)
    val depthFactor = Forest.forestDepthFactor(character, config.forest)
    val path        = config.forest.path
    val restTurns   = config.play.rest.turns
    val locked      = distinctNodes(character.forestNodes)
      .map((item, _) => item)
      .filterNot(Forest.canHarvest(_, character, config))
      .map(item => item -> Forest.requiredPower(item, config).getOrElse(0))
    val nodes =
      distinctNodes(character.forestNodes).map { (item, count) =>
        if Forest.canHarvest(item, character, config) then
          val tiring  = config.forest.resources.get(item).exists(_.tiring)
          val turns   = Forest.turnsFor(item, config).getOrElse(0)
          val range   = Forest.yieldRange(item, character, config).getOrElse(YieldRange(0, 0))
          val fatigue =
            if tiring then
              math
                .round(Forest.fatigueChanceFor(item, character, config).getOrElse(0.0) * 100)
                .toInt
            else 0
          val worn = if tiring then s" · $fatigue% fat" else ""
          Button(
            s"$item ($count) · ${turns}t · ${range.min}-${range.max}$worn",
            Msg.Harvest(item),
            Button.Variant.Primary,
            Button.Size.Small,
            "w-full"
          )
        else
          val required = Forest.requiredPower(item, config).getOrElse(0)
          Button(
            s"$item ($count) · needs power $required",
            Msg.Harvest(item),
            Button.Variant.Disabled,
            Button.Size.Small,
            "w-full"
          )
      }
    cardWithLeave(
      s"Forest ${character.forestLevel}, Power $power, DF ${factorLabel(depthFactor)}",
      "",
      "Leave Forest"
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
          div(cls := "flex flex-col gap-2")(nodes*)
        else div(cls := "text-sm text-gray-500")(text("You have cleared this glade.")),
        div(cls := "flex items-center gap-2")(
          restZzz(character, restTurns),
          iconButton(
            Msg.GoDeeper,
            Icons.arrowDown[Msg]("size-4"),
            s"Go Deeper (${turnWord(path.deeperTurns)})",
            !Forest.canGoDeeper(character)
          )
        ),
        if locked.nonEmpty then
          hint(
            s"This glade cannot be cleared until you equip an axe that can work " +
              locked.map((item, power) => s"$item (forestry power $power)").mkString(", ") + "."
          )
        else div()(),
        banner(model),
        note(model),
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
  // Compact on every screen: a 3-letter shortcut, the level and a thin progress bar. The full
  // details (name, XP, XP to the next level) surface on hover.
  private def skillShort(name: SkillName): String = name match
    case SkillName.Mining   => "MIN"
    case SkillName.Combat   => "CMB"
    case SkillName.Smithing => "SMI"
    case SkillName.Forestry => "FOR"

  private def viewSkills(character: Character, config: GameConfig): Html[Msg] =
    div(cls := "grid grid-cols-4 gap-2")(
      SkillNames.map { name =>
        val skill    = character.skills(name)
        val progress = Xp.levelProgress(skill.xp, config.skills)
        val width    = math.round(progress.percent * 100).toInt
        Tooltip.expanded(
          div(cls := "max-w-40 whitespace-normal text-left")(
            div(cls := "font-semibold")(text(name.toString)),
            div()(text(s"Level ${skill.level}")),
            div(cls := "text-gray-300")(text(s"${skill.xp} XP")),
            if progress.xpForNextLevel > 0 then
              div(cls := "text-gray-300")(
                text(s"${math.round(progress.xpForNextLevel).toInt} XP to ${progress.level + 1}")
              )
            else div(cls := "text-gray-300")(text("Max level"))
          ),
          div(
            cls := "flex w-full flex-col items-center gap-1 rounded-lg border border-gray-100 px-1 py-2"
          )(
            span(cls := "text-[11px] font-semibold tracking-wide text-gray-500")(
              text(skillShort(name))
            ),
            span(cls := "text-sm font-semibold tabular-nums")(text(skill.level.toString)),
            div(cls := "h-1.5 w-full overflow-hidden rounded-full bg-gray-200")(
              div(
                cls := "h-1.5 rounded-full bg-gray-700",
                attribute("style", s"width: $width%")
              )()
            )
          )
        )
      }
    )

  // ---- HUD: collapsible sections. Closed, each is a button in a horizontal row (a stacked column
  //      in the side panel from md up); pressing one expands the full list underneath. Turns and
  //      fatigue live in the top status bar instead. ----

  private def hudCard(title: String)(content: Html[Msg]*): Html[Msg] =
    Card.simple(Card.Variant.Default, Card.Padding.Small)(
      div(cls := "flex flex-col gap-2")(
        List[Html[Msg]](
          div(cls := "text-sm font-semibold text-gray-700")(text(title))
        ) ++ content
      )
    )

  // ---- Status bar: character name, turns and fatigue in one row on top of the screen ----

  private def viewStatusBar(character: Character, config: GameConfig): Html[Msg] =
    val fatigueMax = config.play.fatigue.max
    val fatiguePct =
      math.min(100, math.round(character.fatigue * 100.0 / math.max(1, fatigueMax)).toInt)
    div(
      cls := "flex w-full max-w-sm items-center justify-between gap-3 rounded-lg border border-gray-300 bg-white px-4 py-2"
    )(
      div(cls := "flex min-w-0 items-baseline gap-2")(
        span(cls := "truncate text-sm font-semibold text-gray-700")(text(character.name)),
        span(cls := "text-xs font-medium tabular-nums text-gray-500")(
          text(turnWord(character.turn))
        )
      ),
      div(cls := "h-6 w-px shrink-0 bg-gray-200")(),
      div(cls := "flex min-w-0 flex-1 flex-col gap-1")(
        div(cls := "flex items-baseline justify-between gap-2")(
          span(cls := "text-xs font-medium uppercase tracking-wide text-gray-500")(text("Fatigue")),
          div(cls := "flex items-baseline gap-1")(
            span(cls := "text-xl font-semibold tabular-nums")(text(character.fatigue.toString)),
            span(cls := "text-xs text-gray-500")(text(s"/ $fatigueMax"))
          )
        ),
        div(cls := "h-2 overflow-hidden rounded-full bg-gray-200")(
          div(
            cls := "h-2 rounded-full bg-orange-400",
            attribute("style", s"width: $fatiguePct%")
          )()
        )
      )
    )

  private def hudToggle(pane: HudPane, label: String, open: Set[HudPane]): Html[Msg] =
    Button.apply(
      label,
      Msg.ToggleHud(pane),
      if open.contains(pane) then Button.Variant.Primary else Button.Variant.Secondary,
      Button.Size.Small,
      "w-full truncate"
    )

  private def viewHud(model: Model, character: Character): Html[Msg] =
    val config = model.config
    val built  = config.building.buildables.keys
      .filter(Building.isBuilt(character, _))
      .toList
    div(cls := "flex w-full flex-col gap-3 md:w-48 md:shrink-0")(
      div(cls := "grid grid-cols-3 gap-2 md:grid-cols-1")(
        hudToggle(HudPane.Inventory, "Inventory", model.hudOpen),
        hudToggle(HudPane.Equipment, "Equipment", model.hudOpen),
        hudToggle(HudPane.Workstations, "Workstations", model.hudOpen)
      ),
      if model.hudOpen.contains(HudPane.Inventory) then
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
        )
      else div()(),
      if model.hudOpen.contains(HudPane.Equipment) then
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
        )
      else div()(),
      if model.hudOpen.contains(HudPane.Workstations) then
        hudCard("Workstations")(
          if built.isEmpty then div(cls := "text-sm text-gray-500")(text("None"))
          else
            div(cls := "flex flex-col gap-1")(
              built.map(name => div(cls := "text-sm truncate")(text(name)))
            )
        )
      else div()()
    )
