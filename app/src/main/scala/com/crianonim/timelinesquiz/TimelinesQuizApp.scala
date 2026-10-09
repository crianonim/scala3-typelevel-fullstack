package com.crianonim.timelinesquiz

import cats.effect.IO
import scala.concurrent.duration.DurationInt
import scala.util.Random
import tyrian.*
import tyrian.Html.*

import com.crianonim.shadcn.{Badge, Button, Card, Icons, Input}

/** The "Timeline Quiz" tab: a Tyrian reimplementation of the Next.js app. Pick a timeline, guess
  * which entries cover a given year, and get instant feedback. Overlap mode lets you multi-select
  * entries for years where several were active. A faint debug toggle forces a specific year.
  */
object TimelinesQuizApp:

  val AdvanceDelayMs = 1200

  enum Screen:
    case Menu, Preview, Quiz

  case class Result(question: Question, chosen: Set[String], ok: Boolean)

  case class Model(
      screen: Screen,
      selected: Option[QuizTimeline],
      question: Option[Question],
      ready: Boolean,
      revealed: Boolean,
      result: Option[Result],
      overlapMode: Boolean,
      chosen: Set[String],
      total: Int,
      score: Int,
      debug: Boolean,
      debugYear: String,
      debugNote: Option[String]
  )

  enum Msg:
    case Noop
    case SelectTimeline(id: String)
    case OpenPreview(id: String)
    case BackToMenu
    case QuestionRolled(question: Option[Question])
    case Answer(key: String)
    case ToggleOverlap
    case ToggleOption(key: String)
    case Submit
    case NextQuestion
    case ToggleDebug
    case SetDebugYear(value: String)
    case DebugRoll

  def init: Model = Model(
    screen = Screen.Menu,
    selected = None,
    question = None,
    ready = false,
    revealed = false,
    result = None,
    overlapMode = false,
    chosen = Set.empty,
    total = 0,
    score = 0,
    debug = false,
    debugYear = "",
    debugNote = None
  )

  private val entryOrdering: Ordering[QuizEntry] =
    Ordering.by(e => (e.start, e.end, e.name))

  private def roll(entries: List[QuizEntry]): Cmd[IO, Msg] =
    Cmd.Run(IO(Quiz.pickQuestion(entries, new Random)))(Msg.QuestionRolled.apply)

  private def rollYear(entries: List[QuizEntry], year: Int): Cmd[IO, Msg] =
    Cmd.Run(IO(Quiz.questionForYear(entries, year, new Random)))(Msg.QuestionRolled.apply)

  private val advance: Cmd[IO, Msg] =
    Cmd.Run(IO.sleep(AdvanceDelayMs.millis))(_ => Msg.NextQuestion)

  private def reveal(model: Model, question: Question, chosen: Set[String], ok: Boolean): Model =
    model.copy(
      revealed = true,
      result = Some(Result(question, chosen, ok)),
      total = model.total + 1,
      score = model.score + (if ok then 1 else 0)
    )

  def update(model: Model): Msg => (Model, Cmd[IO, Msg]) =
    case Msg.Noop               => (model, Cmd.None)
    case Msg.BackToMenu         => (model.copy(screen = Screen.Menu), Cmd.None)
    case Msg.SelectTimeline(id) =>
      QuizLibrary.timelines.find(_.id == id) match
        case Some(timeline) =>
          (
            model.copy(
              screen = Screen.Quiz,
              selected = Some(timeline),
              question = None,
              ready = false,
              revealed = false,
              result = None,
              overlapMode = false,
              chosen = Set.empty,
              total = 0,
              score = 0,
              debugNote = None
            ),
            roll(timeline.entries)
          )
        case None => (model, Cmd.None)
    case Msg.OpenPreview(id) =>
      QuizLibrary.timelines.find(_.id == id) match
        case Some(timeline) =>
          (model.copy(screen = Screen.Preview, selected = Some(timeline)), Cmd.None)
        case None => (model, Cmd.None)
    case Msg.QuestionRolled(question) =>
      (
        model.copy(
          question = question,
          ready = true,
          revealed = false,
          result = None,
          overlapMode = false,
          chosen = Set.empty,
          debugNote = None
        ),
        Cmd.None
      )
    case Msg.Answer(key) =>
      model.question match
        case Some(question) if !model.revealed && !model.overlapMode =>
          val correctKeys = question.correct.map(Quiz.entryKey)
          val ok          = correctKeys.size == 1 && correctKeys.head == key
          (reveal(model, question, Set(key), ok), advance)
        case _ => (model, Cmd.None)
    case Msg.ToggleOverlap =>
      if model.revealed then (model, Cmd.None)
      else (model.copy(overlapMode = !model.overlapMode, chosen = Set.empty), Cmd.None)
    case Msg.ToggleOption(key) =>
      if model.revealed || !model.overlapMode then (model, Cmd.None)
      else
        val chosen = if model.chosen.contains(key) then model.chosen - key else model.chosen + key
        (model.copy(chosen = chosen), Cmd.None)
    case Msg.Submit =>
      model.question match
        case Some(question) if !model.revealed && model.overlapMode =>
          (
            reveal(
              model,
              question,
              model.chosen,
              Quiz.isExactMatch(question.correct, model.chosen)
            ),
            advance
          )
        case _ => (model, Cmd.None)
    case Msg.NextQuestion =>
      if model.revealed then
        model.selected match
          case Some(timeline) =>
            (
              model.copy(
                revealed = false,
                result = None,
                overlapMode = false,
                chosen = Set.empty,
                ready = false
              ),
              roll(timeline.entries)
            )
          case None => (model, Cmd.None)
      else (model, Cmd.None)
    case Msg.ToggleDebug         => (model.copy(debug = !model.debug, debugNote = None), Cmd.None)
    case Msg.SetDebugYear(value) =>
      (model.copy(debugYear = value, debugNote = None), Cmd.None)
    case Msg.DebugRoll =>
      model.selected match
        case None           => (model, Cmd.None)
        case Some(timeline) =>
          model.debugYear.trim.toIntOption match
            case None =>
              (model.copy(debugNote = Some("Enter a valid year.")), Cmd.None)
            case Some(year) if Quiz.matchesForYear(timeline.entries, year).isEmpty =>
              (model.copy(debugNote = Some(s"No entries cover $year.")), Cmd.None)
            case Some(year) =>
              (
                model.copy(
                  revealed = false,
                  result = None,
                  overlapMode = false,
                  chosen = Set.empty,
                  ready = false,
                  debugNote = None
                ),
                rollYear(timeline.entries, year)
              )

  def view(model: Model): Html[Msg] =
    div(cls := "relative mx-auto w-full max-w-lg")(
      model.screen match
        case Screen.Menu    => menuView
        case Screen.Preview => model.selected.map(previewView).getOrElse(menuView)
        case Screen.Quiz    => quizView(model),
      if model.screen == Screen.Quiz then debugToggle(model) else div()()
    )

  private def menuView: Html[Msg] =
    div(cls := "flex flex-col gap-6")(
      div(cls := "space-y-1")(
        h1(cls := "text-2xl font-bold")(text("Timeline Quiz")),
        p(cls := "text-sm text-muted-foreground")(text("Pick a timeline to test your knowledge."))
      ),
      if QuizLibrary.timelines.isEmpty then
        p(cls := "py-12 text-center text-muted-foreground")(text("No timelines found."))
      else div(cls := "flex flex-col gap-3")(QuizLibrary.timelines.map(timelineCard)*)
    )

  private def timelineCard(timeline: QuizTimeline): Html[Msg] =
    Card(
      div(cls := "flex flex-col gap-3 p-4 sm:flex-row sm:items-center sm:p-6")(
        div(
          cls := "flex flex-1 cursor-pointer items-center justify-between gap-3",
          onClick(Msg.SelectTimeline(timeline.id))
        )(
          span(cls := "font-medium")(text(timeline.label)),
          Badge(s"${timeline.minYear}–${timeline.maxYear}", Badge.Variant.Secondary)
        ),
        Button(
          "View",
          Msg.OpenPreview(timeline.id),
          Button.Variant.Outline,
          Button.Size.Sm,
          "w-full sm:w-auto"
        )
      )
    )

  private def previewView(timeline: QuizTimeline): Html[Msg] =
    val entries = timeline.entries.sorted(entryOrdering)
    div(cls := "flex flex-col gap-6")(
      div(cls := "flex")(
        Button("Back to main menu", Msg.BackToMenu, Button.Variant.Outline, Button.Size.Sm)
      ),
      div(cls := "space-y-1")(
        h1(cls := "text-2xl font-bold")(text(timeline.label)),
        p(cls := "text-sm text-muted-foreground")(
          text(
            s"${entries.length} ${if entries.length == 1 then "entry" else "entries"} · ${timeline.minYear}–${timeline.maxYear}"
          )
        )
      ),
      // A divided list rather than a scrolling table: columns fit any screen width, so the period
      // stays visible on phones instead of forcing a horizontal scroll.
      div(cls := "overflow-hidden rounded-lg border")(
        div(
          cls := "grid grid-cols-[1fr_auto] items-baseline gap-3 border-b bg-muted/30 px-3 py-2 text-xs font-medium text-muted-foreground"
        )(text("Name"), text("Period")),
        div(cls := "divide-y")(entries.map(entry =>
          div(cls := "grid grid-cols-[1fr_auto] items-baseline gap-3 px-3 py-3 text-sm")(
            span(cls := "min-w-0 font-medium")(text(entry.name)),
            span(cls := "shrink-0 tabular-nums text-muted-foreground")(
              text(s"${entry.start}–${entry.end}")
            )
          )
        )*)
      )
    )

  private def quizView(model: Model): Html[Msg] =
    model.selected match
      case None           => menuView
      case Some(timeline) =>
        div(cls := "flex flex-col gap-4 pb-10")(
          model.result.map(banner).getOrElse(div()()),
          div(cls := "flex items-baseline justify-between")(
            h1(cls := "text-sm font-medium text-muted-foreground")(text(timeline.label)),
            span(cls := "text-xs text-muted-foreground")(
              text(s"${timeline.minYear}–${timeline.maxYear}")
            )
          ),
          if !model.ready then p(cls := "py-12 text-center text-muted-foreground")(text("Loading…"))
          else
            model.question match
              case None =>
                p(cls := "py-12 text-center text-muted-foreground")(
                  text("No playable years found in this timeline.")
                )
              case Some(question) => questionView(model, question),
          if model.debug then debugBar(model) else div()(),
          footer(model)
        )

  private def questionView(model: Model, question: Question): Html[Msg] =
    val correctKeys = question.correct.map(Quiz.entryKey).toSet
    div(cls := "flex flex-col gap-4")(
      Card(
        div(cls := "px-4 py-8 text-center sm:px-6")(
          p(cls := "text-xs uppercase tracking-widest text-muted-foreground")(text("Year")),
          p(cls := "mt-1 text-5xl font-bold tabular-nums sm:text-6xl")(text(question.year.toString)),
          p(cls := "mt-2 text-sm text-muted-foreground")(
            text("Which entries were active in this year?")
          )
        )
      ),
      div(cls := "grid gap-2")(
        question.options.map(optionButton(model, question, correctKeys, _))*
      ),
      div(cls := "flex flex-col gap-2")(
        Button(
          "Overlap",
          Msg.ToggleOverlap,
          if model.overlapMode then Button.Variant.Secondary else Button.Variant.Outline,
          Button.Size.Default,
          "w-full"
        ),
        if model.overlapMode then
          Button("Submit", Msg.Submit, Button.Variant.Default, Button.Size.Default, "w-full")
        else div()()
      )
    )

  private def optionButton(
      model: Model,
      question: Question,
      correctKeys: Set[String],
      option: QuizEntry
  ): Html[Msg] =
    val key           = Quiz.entryKey(option)
    val isCorrect     = model.revealed && correctKeys.contains(key)
    val isWrongChoice = model.revealed && model.result.exists(_.chosen.contains(key)) && !isCorrect
    val isChecked     = model.overlapMode && model.chosen.contains(key)
    val stateCls      =
      if isCorrect then "text-green-700 ring-2 ring-green-600/60 dark:text-green-400"
      else if isWrongChoice then "text-red-700 ring-2 ring-red-600/60 dark:text-red-400"
      else if model.revealed then "opacity-40"
      else ""
    val check: List[Elem[Msg]] =
      if model.overlapMode then
        List(
          span(
            cls := s"flex size-5 shrink-0 items-center justify-center rounded border ${
                if isChecked then "border-primary bg-primary text-primary-foreground"
                else "border-border"
              }"
          )(if isChecked then Icons.check[Msg]("size-3") else span()())
        )
      else Nil
    Button.withContent(
      if model.overlapMode then Msg.ToggleOption(key) else Msg.Answer(key),
      Button.Variant.Outline,
      Button.Size.Default,
      s"h-auto min-h-12 w-full justify-start gap-3 whitespace-normal px-4 py-3 text-left text-base $stateCls"
    )((check :+ text(option.name))*)

  private def banner(result: Result): Html[Msg] =
    val colorCls =
      if result.ok then "border-green-600/40 bg-green-600/10 text-green-800 dark:text-green-400"
      else "border-red-600/40 bg-red-600/10 text-red-800 dark:text-red-400"
    div(cls := s"rounded-lg border px-4 py-3 text-sm font-medium $colorCls")(
      text(resultText(result))
    )

  private def debugBar(model: Model): Html[Msg] =
    div(cls := "border-t bg-muted/30")(
      div(
        cls := "flex flex-wrap items-center gap-2 py-2",
        onKeyDown[Msg](event => if event.key == "Enter" then Msg.DebugRoll else Msg.Noop)
      )(
        label(cls := "text-xs text-muted-foreground")(text("Year")),
        Input(
          model.debugYear,
          Msg.SetDebugYear.apply,
          placeholder_ = "1630",
          type_ = "number",
          className = "h-9 w-28 tabular-nums"
        ),
        Button("Roll", Msg.DebugRoll, Button.Variant.Default, Button.Size.Sm),
        model.debugNote
          .map(note => span(cls := "text-xs text-muted-foreground")(text(note)))
          .getOrElse(span()())
      )
    )

  private def footer(model: Model): Html[Msg] =
    div(cls := "border-t")(
      div(cls := "flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between")(
        p(cls := "text-sm text-muted-foreground")(
          text(
            s"${model.total} question${if model.total == 1 then "" else "s"} · ${model.score} correct"
          )
        ),
        Button(
          "Back to timelines",
          Msg.BackToMenu,
          Button.Variant.Ghost,
          Button.Size.Sm,
          "w-full sm:w-auto"
        )
      )
    )

  private def debugToggle(model: Model): Html[Msg] =
    div(
      cls := "fixed right-1.5 bottom-1.5 z-20 flex select-none items-center gap-1 text-[10px] text-muted-foreground opacity-30 transition-opacity hover:opacity-80"
    )(
      input(
        cls := "size-3 accent-primary",
        attribute("type", "checkbox"),
        checked := model.debug,
        onInput(_ => Msg.ToggleDebug)
      ),
      span()(text("Debug"))
    )

  private def joinNames(entries: List[QuizEntry]): String =
    entries.map(_.name) match
      case Nil                    => ""
      case one :: Nil             => one
      case first :: second :: Nil => s"$first and $second"
      case names                  => names.init.mkString(", ") + " and " + names.last

  private def resultText(result: Result): String =
    val names = joinNames(result.question.correct)
    if result.ok then s"Success! ${result.question.year} was for $names"
    else
      val periods = result.question.correct.map(e => s"${e.start}-${e.end}").mkString(", ")
      s"Failure! ${result.question.year} was for $names ($periods)"
