package com.crianonim.timelines

import cats.effect.*
import tyrian.*
import tyrian.Html.*
//import cats.effect.std.Random
import cats.syntax.all.*

import java.time.LocalDate
import java.time.ZoneId
import com.crianonim.ui.*
import com.crianonim.timelines.{Timeline, Viewport}
object TimelinesApp {
  case class Model(
      timelines: List[Timeline],
      viewport: Viewport,
      selectedTimeline: Option[Timeline],
      // Timeline creation form state
      createFormVisible: Boolean = false,
      newTimelineName: String = "",
      selectedPeriodType: String = "point", // "point", "closed", or "started"
      startTimePoint: Option[TimePoint] = None,
      endTimePoint: Option[TimePoint] = None
  )

  enum Msg {
    case Noop
    case ToggleTimelineSelection(timeline: Timeline)
    case UnselectTimeline
    case SetViewportStart
    case SetViewportEnd
    case SetViewportToTimeline
    case SetViewportEndToNow
    case ResetViewport
    // Timeline creation form messages
    case ShowCreateForm
    case HideCreateForm
    case UpdateTimelineName(name: String)
    case SelectPeriodType(periodType: String)
    case UpdateStartTimePoint(tp: Option[TimePoint])
    case UpdateEndTimePoint(tp: Option[TimePoint])
    case CreateTimeline
  }

  def init: Model = Model(
    timelines = Timeline.examples,
    viewport = Viewport.getViewportForTimelines(Timeline.examples),
    selectedTimeline = None
  )

  def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = {
    case Msg.Noop => (model, Cmd.None)
    case Msg.ToggleTimelineSelection(timeline) =>
      val newSelection = model.selectedTimeline match {
        case Some(selected) if selected == timeline => None
        case _                                      => Some(timeline)
      }
      (model.copy(selectedTimeline = newSelection), Cmd.None)
    case Msg.UnselectTimeline =>
      (model.copy(selectedTimeline = None), Cmd.None)
    case Msg.SetViewportStart =>
      model.selectedTimeline match {
        case Some(timeline) =>
          val startTimePoint = Period.minTimePointOfPeriod(timeline.period)
          val startDate      = TimePoint.timePointFloorDate(startTimePoint)
          val newViewport    = model.viewport.copy(start = startDate)
          (model.copy(viewport = newViewport), Cmd.None)
        case None => (model, Cmd.None)
      }
    case Msg.SetViewportEnd =>
      model.selectedTimeline match {
        case Some(timeline) =>
          Period.maxTimePointOfPeriod(timeline.period) match {
            case Some(endTimePoint) =>
              val endDate     = TimePoint.timePointCeilDate(endTimePoint)
              val newViewport = model.viewport.copy(end = endDate)
              (model.copy(viewport = newViewport), Cmd.None)
            case None => (model, Cmd.None) // Started period has no end
          }
        case None => (model, Cmd.None)
      }
    case Msg.SetViewportToTimeline =>
      model.selectedTimeline match {
        case Some(timeline) =>
          val startTimePoint = Period.minTimePointOfPeriod(timeline.period)
          val startDate      = TimePoint.timePointFloorDate(startTimePoint)
          Period.maxTimePointOfPeriod(timeline.period) match {
            case Some(endTimePoint) =>
              val endDate     = TimePoint.timePointCeilDate(endTimePoint)
              val newViewport = Viewport(start = startDate, end = endDate)
              (model.copy(viewport = newViewport), Cmd.None)
            case None => // Started period has no end, only set start
              val newViewport = model.viewport.copy(start = startDate)
              (model.copy(viewport = newViewport), Cmd.None)
          }
        case None => (model, Cmd.None)
      }
    case Msg.SetViewportEndToNow =>
      val today       = LocalDate.now(ZoneId.of("UTC"))
      val newViewport = model.viewport.copy(end = today)
      (model.copy(viewport = newViewport), Cmd.None)
    case Msg.ResetViewport =>
      val newViewport = Viewport.getViewportForTimelines(model.timelines)
      (model.copy(viewport = newViewport), Cmd.None)

    // Timeline creation form handlers
    case Msg.ShowCreateForm =>
      (model.copy(createFormVisible = true), Cmd.None)

    case Msg.HideCreateForm =>
      (
        model.copy(
          createFormVisible = false,
          newTimelineName = "",
          selectedPeriodType = "point",
          startTimePoint = None,
          endTimePoint = None
        ),
        Cmd.None
      )

    case Msg.UpdateTimelineName(name) =>
      (model.copy(newTimelineName = name), Cmd.None)

    case Msg.SelectPeriodType(periodType) =>
      (
        model.copy(
          selectedPeriodType = periodType,
          // Clear end time point when switching to point or started
          endTimePoint = if (periodType == "closed") model.endTimePoint else None
        ),
        Cmd.None
      )

    case Msg.UpdateStartTimePoint(tp) =>
      (model.copy(startTimePoint = tp), Cmd.None)

    case Msg.UpdateEndTimePoint(tp) =>
      (model.copy(endTimePoint = tp), Cmd.None)

    case Msg.CreateTimeline =>
      // Validate form
      val isValid = model.newTimelineName.nonEmpty && model.startTimePoint.isDefined &&
        (model.selectedPeriodType != "closed" || model.endTimePoint.isDefined)

      if (!isValid) {
        (model, Cmd.None) // Don't create if invalid
      } else {
        // Create the period based on selected type
        val period: Option[Period] = model.selectedPeriodType match {
          case "point" =>
            model.startTimePoint.map(Point(_))
          case "closed" =>
            for {
              start <- model.startTimePoint
              end   <- model.endTimePoint
            } yield Closed(start, end)
          case "started" =>
            model.startTimePoint.map(Started(_))
          case _ => None
        }

        period match {
          case Some(p) =>
            // Generate a simple ID using timestamp and random number
            val id = s"tl-${System.currentTimeMillis()}-${scala.util.Random.nextInt(10000)}"
            val newTimeline = Timeline(id, model.newTimelineName, p)
            val updatedTimelines = model.timelines :+ newTimeline
            val newViewport = Viewport.getViewportForTimelines(updatedTimelines)

            (
              model.copy(
                timelines = updatedTimelines,
                viewport = newViewport,
                createFormVisible = false,
                newTimelineName = "",
                selectedPeriodType = "point",
                startTimePoint = None,
                endTimePoint = None
              ),
              Cmd.None
            )
          case None =>
            (model, Cmd.None) // Should not happen if validation is correct
        }
      }
  }

  def viewCreateTimelineModal(model: Model): Html[Msg] = {
    Modal.withTitle(
      model.createFormVisible,
      Msg.HideCreateForm,
      "Create New Timeline",
      Modal.Size.Large
    )(
      div(cls := "flex flex-col gap-6")(
        // Timeline name input
        div(cls := "flex flex-col gap-2")(
          div(cls := "font-medium text-sm text-gray-700")(text("Timeline Name")),
          Input.interactive(
            model.newTimelineName,
            Msg.UpdateTimelineName.apply,
            "text"
          )
        ),
        // Period type selector
        div(cls := "flex flex-col gap-2")(
          div(cls := "font-medium text-sm text-gray-700")(text("Period Type")),
          div(cls := "flex gap-4")(
            // Point option
            div(cls := "flex items-center gap-2")(
              input(
                `type` := "radio",
                name := "periodType",
                value := "point",
                id := "period-point",
                checked := model.selectedPeriodType == "point",
                onInput(_ => Msg.SelectPeriodType("point")),
                cls := "cursor-pointer"
              ),
              label(
                `for` := "period-point",
                cls := "cursor-pointer text-sm"
              )(text("Point (single moment)"))
            ),
            // Closed option
            div(cls := "flex items-center gap-2")(
              input(
                `type` := "radio",
                name := "periodType",
                value := "closed",
                id := "period-closed",
                checked := model.selectedPeriodType == "closed",
                onInput(_ => Msg.SelectPeriodType("closed")),
                cls := "cursor-pointer"
              ),
              label(
                `for` := "period-closed",
                cls := "cursor-pointer text-sm"
              )(text("Closed (start and end)"))
            ),
            // Started option
            div(cls := "flex items-center gap-2")(
              input(
                `type` := "radio",
                name := "periodType",
                value := "started",
                id := "period-started",
                checked := model.selectedPeriodType == "started",
                onInput(_ => Msg.SelectPeriodType("started")),
                cls := "cursor-pointer"
              ),
              label(
                `for` := "period-started",
                cls := "cursor-pointer text-sm"
              )(text("Started (ongoing)"))
            )
          )
        ),
        // Date inputs based on period type
        model.selectedPeriodType match {
          case "point" =>
            DateInput.simple(
              "Point in Time",
              model.startTimePoint,
              Msg.UpdateStartTimePoint.apply
            )
          case "closed" =>
            div(cls := "flex flex-col gap-4")(
              DateInput.simple(
                "Start Date",
                model.startTimePoint,
                Msg.UpdateStartTimePoint.apply
              ),
              DateInput.simple(
                "End Date",
                model.endTimePoint,
                Msg.UpdateEndTimePoint.apply
              )
            )
          case "started" =>
            DateInput.simple(
              "Start Date",
              model.startTimePoint,
              Msg.UpdateStartTimePoint.apply
            )
          case _ => div()()
        },
        // Action buttons
        div(cls := "flex gap-3 justify-end pt-4 border-t border-gray-200")(
          Button.secondary("Cancel", Msg.HideCreateForm),
          // Validation: disable create button if form is invalid
          if (model.newTimelineName.nonEmpty && model.startTimePoint.isDefined &&
              (model.selectedPeriodType != "closed" || model.endTimePoint.isDefined)) {
            Button.primary("Create Timeline", Msg.CreateTimeline)
          } else {
            Button.disabledButton("Create Timeline")
          }
        )
      )
    )
  }

  def view(model: Model): Html[Msg] = {
    div(cls := "flex flex-col gap-2 p-10")(
      div(cls := "flex gap-2 text-storm-dust-700 items-center justify-between")(
        div()(text("TimeLines")),
        Button.primary("Add Timeline", Msg.ShowCreateForm, Button.Size.Medium)
      ),
      model.selectedTimeline match {
        case Some(_) =>
          div(cls := "flex gap-2 items-center")(
            Button.secondary("Unselect", Msg.UnselectTimeline, Button.Size.Small),
            Button.secondary("Start viewport", Msg.SetViewportStart, Button.Size.Small),
            Button.secondary("End viewport", Msg.SetViewportEnd, Button.Size.Small),
            Button.secondary("Set viewport", Msg.SetViewportToTimeline, Button.Size.Small)
          )
        case None => div()()
      },
      div(cls := "flex gap-4 items-center text-gray-700")(
        div(cls := "flex gap-2")(
          div(cls := "font-semibold")(text("Viewport Start:")),
          div()(text(model.viewport.start.toString))
        ),
        div(cls := "flex gap-2")(
          div(cls := "font-semibold")(text("Viewport End:")),
          div()(text(model.viewport.end.toString))
        ),
        Button.secondary("Reset Viewport", Msg.ResetViewport, Button.Size.Small),
        Button.secondary("End now", Msg.SetViewportEndToNow, Button.Size.Small)
      ),
      Card.simple()(
        div(cls := "flex flex-col gap-1")(
          model.timelines
            .filter(Viewport.isTimelineInViewport(model.viewport, _))
            .map(viewTimeline(model))*
        )
      ),
      div(cls := "flex flex-col gap-1 p-2")(model.timelines.map(viewTimelineAsText)),
      // Render the create timeline modal
      viewCreateTimelineModal(model)
    )
  }
  def viewTimePoint(t: TimePoint) = text(t.show)

  def viewPeriod(p: Period) = div()(
    p match
      case Point(point) => div(cls := "flex gap-2")(viewTimePoint(point))
      case Closed(start, end) =>
        div(cls := "flex gap-2")(viewTimePoint(start), text(" ->> "), viewTimePoint(end))
      case Started(start) => div(cls := "flex gap-2")(viewTimePoint(start), text(" -->> "))
  )
  def viewTimelineAsText(tl: Timeline) = div(cls := "flex gap-2 p-2")(
    div(cls := "")(text(tl.name)),
    viewPeriod(tl.period)
  )

  def viewBar(bar: TimeLineBar) = {
    val startPercent = bar.start.getOrElse(0f)
    val isOngoing = bar.timeline.period match {
      case Started(_) => true
      case _          => false
    }

    // Use gradient for ongoing timelines, solid color for finished ones
    val background =
      if isOngoing then
        "linear-gradient(to right, #0ea5e9 0%, #0ea5e9 70%, rgba(14, 165, 233, 0) 100%)"
      else "#0ea5e9"

    div(
      styles(
        "position"   -> "absolute",
        "left"       -> (startPercent.toString ++ "%"),
        "width"      -> (bar.length.toString ++ "%"),
        "min-width"  -> "1px",
        "background" -> background
      ),
      title := bar.timeline.name,
      cls   := "h-4"
    )()
  }
  def viewTimeline(model: Model)(tl: Timeline) = {
    val bar        = TimeLineBar.timelineToTimelineBar(model.viewport, tl)
    val isSelected = model.selectedTimeline.contains(tl)
    val containerClasses =
      if isSelected then "flex gap-4 items-center bg-blue-100 p-2 rounded cursor-pointer"
      else "flex gap-4 items-center p-2 hover:bg-gray-100 cursor-pointer"

    div(
      cls := containerClasses,
      onClick(Msg.ToggleTimelineSelection(tl))
    )(
      div(cls := "w-full relative h-4")(
        viewBar(bar)
      )
    )
  }
}
