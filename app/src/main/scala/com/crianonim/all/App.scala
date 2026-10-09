package com.crianonim.all

import scala.scalajs.js
import scala.scalajs.js.annotation.*
import cats.effect.*
import tyrian.*
import tyrian.Html.*
import tyrian.Nav
//import io.circe.syntax.*
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

enum Msg {
  case NoMsg
  case NavigateTo(nav: Page)
  case UpdateTablesApp(tMsg: TablesApp.Msg)
  case UpdateDiceRollApp(tMsg: DiceRoll.Msg)
  case UpdateTimelines(tMsg: TimelinesApp.Msg)
  case UpdatePreview(tMsg: Preview.Msg)
  case UpdateScreept(tMsg: ScreeptApp.Msg)
  case UpdateGenTree(tMsg: GenTreeApp.Msg)
  case UpdateDialog(tMsg: DialogApp.Msg)
  case UpdateDialogGame(tMsg: DialogGameApp.Msg)
  case UpdateShadcn(tMsg: ShadcnShowcase.Msg)
  case UpdateJanscape(jMsg: JanscapeApp.Msg)
  case UpdateTimelineQuiz(qMsg: TimelinesQuizApp.Msg)
}

case class Model(
    page: Page,
    tables: TablesApp.Model,
    diceRoll: DiceRoll.Model,
    timelines: TimelinesApp.Model,
    preview: Preview.Model,
    screept: ScreeptApp.Model,
    genTree: GenTreeApp.Model,
    dialog: DialogApp.Model,
    dialogGame: DialogGameApp.Model,
    shadcn: ShadcnShowcase.Model,
    janscape: JanscapeApp.Model,
    timelineQuiz: TimelinesQuizApp.Model
)

@JSExportTopLevel("AllApp")
object App extends TyrianIOApp[Msg, Model] {

  override def router: Location => Msg =
    case loc: Location.Internal =>
      if loc.pathName == "/" then Msg.NavigateTo(Page.MainPage)
      else AppRegistry.byPath(loc.pathName).map(e => Msg.NavigateTo(e.page)).getOrElse(Msg.NoMsg)
    case loc: Location.External =>
      Msg.NoMsg

  override def init(flags: Map[String, String]): (Model, Cmd[IO, Msg]) =
    val tablesModel       = TablesApp.initEmpty
    val diceRollModel     = DiceRoll.init
    val timelinesModel    = TimelinesApp.init
    val previewModel      = Preview.init
    val screeptModel      = ScreeptApp.init
    val genTreeModel      = GenTreeApp.init
    val dialogModel       = DialogApp.init
    val dialogGameModel   = DialogGameApp.init
    val shadcnModel       = ShadcnShowcase.init
    val janscapeModel     = JanscapeApp.init
    val timelineQuizModel = TimelinesQuizApp.init
    (
      Model(
        Page.MainPage,
        tablesModel,
        diceRollModel,
        timelinesModel,
        previewModel,
        screeptModel,
        genTreeModel,
        dialogModel,
        dialogGameModel,
        shadcnModel,
        janscapeModel,
        timelineQuizModel
      ),
      Cmd.Batch(
        DialogApp.initCmd.map(Msg.UpdateDialog.apply),
        DialogGameApp.initCmd.map(Msg.UpdateDialogGame.apply),
        JanscapeApp.initCmd.map(Msg.UpdateJanscape.apply)
      )
    )

  override def view(model: Model): Html[Msg] =
    model.page match
      case Page.MainPage =>
        AppShell.landing(AppRegistry.apps.map(e => (e.label, Msg.NavigateTo(e.page))))
      case page =>
        AppRegistry.byPage(page) match
          case Some(entry) =>
            AppShell.appFrame(entry.label, Msg.NavigateTo(Page.MainPage))(entry.view(model))
          case None =>
            AppShell.landing(AppRegistry.apps.map(e => (e.label, Msg.NavigateTo(e.page))))

  override def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = {
    case Msg.NoMsg                 => (model, Cmd.None)
    case Msg.UpdateTablesApp(tMsg) =>
      val (tablesModel, tablesCmd) = TablesApp.update(model.tables)(tMsg)
      (model.copy(tables = tablesModel), tablesCmd.map(Msg.UpdateTablesApp.apply))
    case Msg.NavigateTo(page) =>
      (model.copy(page = page), Nav.pushUrl(AppRegistry.pathFor(page)))
    case Msg.UpdateDiceRollApp(drMsg) =>
      val (drModel, drCmd) = DiceRoll.update(model.diceRoll)(drMsg)
      (model.copy(diceRoll = drModel), drCmd.map(Msg.UpdateDiceRollApp.apply))
    case com.crianonim.all.Msg.UpdateTimelines(tMsg) =>
      val (tmModel, tlCmd) = TimelinesApp.update(model.timelines)(tMsg)
      (model.copy(timelines = tmModel), tlCmd.map(Msg.UpdateTimelines.apply))
    case Msg.UpdatePreview(pMsg) =>
      val (previewModel, previewCmd) = Preview.update(model.preview)(pMsg)
      (model.copy(preview = previewModel), previewCmd.map(Msg.UpdatePreview.apply))
    case Msg.UpdateScreept(sMsg) =>
      val (screeptModel, screeptCmd) = ScreeptApp.update(model.screept)(sMsg)
      (model.copy(screept = screeptModel), screeptCmd.map(Msg.UpdateScreept.apply))
    case Msg.UpdateGenTree(gMsg) =>
      val (gtModel, gtCmd) = GenTreeApp.update(model.genTree)(gMsg)
      (model.copy(genTree = gtModel), gtCmd.map(Msg.UpdateGenTree.apply))
    case Msg.UpdateDialog(dMsg) =>
      val (dialogModel, dialogCmd) = DialogApp.update(model.dialog)(dMsg)
      (model.copy(dialog = dialogModel), dialogCmd.map(Msg.UpdateDialog.apply))
    case Msg.UpdateDialogGame(dgMsg) =>
      val (dgModel, dgCmd) = DialogGameApp.update(model.dialogGame)(dgMsg)
      (model.copy(dialogGame = dgModel), dgCmd.map(Msg.UpdateDialogGame.apply))
    case Msg.UpdateShadcn(sMsg) =>
      val (shadcnModel, shadcnCmd) = ShadcnShowcase.update(model.shadcn)(sMsg)
      (model.copy(shadcn = shadcnModel), shadcnCmd.map(Msg.UpdateShadcn.apply))
    case Msg.UpdateJanscape(jMsg) =>
      val (janscapeModel, janscapeCmd) = JanscapeApp.update(model.janscape)(jMsg)
      (model.copy(janscape = janscapeModel), janscapeCmd.map(Msg.UpdateJanscape.apply))
    case Msg.UpdateTimelineQuiz(qMsg) =>
      val (timelineQuizModel, timelineQuizCmd) = TimelinesQuizApp.update(model.timelineQuiz)(qMsg)
      (
        model.copy(timelineQuiz = timelineQuizModel),
        timelineQuizCmd.map(Msg.UpdateTimelineQuiz.apply)
      )
  }

  override def subscriptions(model: Model): Sub[IO, Msg] =
    Sub.None
}
