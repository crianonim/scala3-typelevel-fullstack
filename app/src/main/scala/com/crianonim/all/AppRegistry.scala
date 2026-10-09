package com.crianonim.all

import tyrian.Html

import com.crianonim.tables.TablesApp
import com.crianonim.dnd.DiceRoll
import com.crianonim.timelines.TimelinesApp
import com.crianonim.ui.Preview
import com.crianonim.screept.ScreeptApp
import com.crianonim.gentree.GenTreeApp
import com.crianonim.dialog.DialogApp
import com.crianonim.dialoggame.DialogGameApp
import com.crianonim.shadcn.ShadcnShowcase
import com.crianonim.janscape.JanscapeApp
import com.crianonim.timelinesquiz.TimelinesQuizApp

/** The set of navigable pages. `MainPage` is the landing page and has no `AppEntry`. */
enum Page:
  case MainPage, TablesPage, DiceRollPage, TimelinesPage, PreviewPage, ScreeptPage,
    GenTreePage, DialogPage, DialogGamePage, ShadcnPage, JanscapePage, TimelineQuizPage

/** One entry in the single source of truth for "what apps exist".
  *
  * @param page
  *   the `Page` case this app maps to
  * @param path
  *   the URL path that selects it
  * @param label
  *   the display name, used both for the landing button and the app-frame title
  * @param view
  *   renders the app for a given root model. The child `update` is still lifted in `App.update`.
  */
case class AppEntry(
    page: Page,
    path: String,
    label: String,
    view: Model => Html[Msg]
)

/** Registry of every app. Adding an app means adding one `AppEntry` here and one `Model` field +
  * `Msg.UpdateXxx` / `update` branch in `App.scala` — the router, the landing page and the app-frame
  * title are all derived from this list.
  */
object AppRegistry {

  val apps: List[AppEntry] = List(
    AppEntry(
      Page.TablesPage,
      "/tables",
      "Tables",
      m => TablesApp.view(m.tables).map(Msg.UpdateTablesApp.apply)
    ),
    AppEntry(
      Page.DiceRollPage,
      "/roll",
      "Dice Roll",
      m => DiceRoll.view(m.diceRoll).map(Msg.UpdateDiceRollApp.apply)
    ),
    AppEntry(
      Page.TimelinesPage,
      "/timelines",
      "Timelines",
      m => TimelinesApp.view(m.timelines).map(Msg.UpdateTimelines.apply)
    ),
    AppEntry(
      Page.PreviewPage,
      "/preview",
      "Preview",
      m => Preview.view(m.preview).map(Msg.UpdatePreview.apply)
    ),
    AppEntry(
      Page.ScreeptPage,
      "/screept",
      "Screept",
      m => ScreeptApp.view(m.screept).map(Msg.UpdateScreept.apply)
    ),
    AppEntry(
      Page.GenTreePage,
      "/gentree",
      "GenTree",
      m => GenTreeApp.view(m.genTree).map(Msg.UpdateGenTree.apply)
    ),
    AppEntry(
      Page.DialogPage,
      "/dialog",
      "Dialog",
      m => DialogApp.view(m.dialog).map(Msg.UpdateDialog.apply)
    ),
    AppEntry(
      Page.DialogGamePage,
      "/dialoggame",
      "Dialog Game",
      m => DialogGameApp.view(m.dialogGame).map(Msg.UpdateDialogGame.apply)
    ),
    AppEntry(
      Page.ShadcnPage,
      "/shadcn",
      "Shadcn",
      m => ShadcnShowcase.view(m.shadcn).map(Msg.UpdateShadcn.apply)
    ),
    AppEntry(
      Page.JanscapePage,
      "/janscape",
      "Janscape",
      m => JanscapeApp.view(m.janscape).map(Msg.UpdateJanscape.apply)
    ),
    AppEntry(
      Page.TimelineQuizPage,
      "/timeline-quiz",
      "Timeline Quiz",
      m => TimelinesQuizApp.view(m.timelineQuiz).map(Msg.UpdateTimelineQuiz.apply)
    )
  )

  def byPage(page: Page): Option[AppEntry] =
    apps.find(_.page == page)

  def byPath(path: String): Option[AppEntry] =
    apps.find(_.path == path)

  /** URL path for a page. The landing page is the root; unknown pages fall back to it. */
  def pathFor(page: Page): String =
    byPage(page).map(_.path).getOrElse("/")
}
