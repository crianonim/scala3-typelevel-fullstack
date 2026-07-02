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
import com.crianonim.ui.SectionTabs
import com.crianonim.screept.ScreeptApp
import com.crianonim.gentree.GenTreeApp
import com.crianonim.dialog.DialogApp
import com.crianonim.dialoggame.DialogGameApp
import com.crianonim.shadcn.ShadcnShowcase
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
    shadcn: ShadcnShowcase.Model
)

@JSExportTopLevel("AllApp")
object App extends TyrianIOApp[Msg, Model] {

  private def pageToPath(page: Page): String = page match {
    case Page.MainPage      => "/"
    case Page.TablesPage    => "/tables"
    case Page.DiceRollPage  => "/roll"
    case Page.TimelinesPage => "/timelines"
    case Page.PreviewPage   => "/preview"
    case Page.ScreeptPage   => "/screept"
    case Page.GenTreePage   => "/gentree"
    case Page.DialogPage    => "/dialog"
    case Page.DialogGamePage => "/dialoggame"
    case Page.ShadcnPage     => "/shadcn"
  }

  private def pageToTabId(page: Page): String = page match {
    case Page.MainPage      => "main"
    case Page.TablesPage    => "tables"
    case Page.DiceRollPage  => "roll"
    case Page.TimelinesPage => "timelines"
    case Page.PreviewPage   => "preview"
    case Page.ScreeptPage   => "screept"
    case Page.GenTreePage   => "gentree"
    case Page.DialogPage    => "dialog"
    case Page.DialogGamePage => "dialoggame"
    case Page.ShadcnPage     => "shadcn"
  }

  private def tabIdToPage(tabId: String): Page = tabId match {
    case "main"      => Page.MainPage
    case "tables"    => Page.TablesPage
    case "roll"      => Page.DiceRollPage
    case "timelines" => Page.TimelinesPage
    case "preview"   => Page.PreviewPage
    case "screept"   => Page.ScreeptPage
    case "gentree"   => Page.GenTreePage
    case "dialog"    => Page.DialogPage
    case "dialoggame" => Page.DialogGamePage
    case "shadcn"    => Page.ShadcnPage
    case _           => Page.MainPage
  }

  override def router: Location => Msg =
    case loc: Location.Internal =>
      loc.pathName match
        case "/"          => Msg.NavigateTo(Page.MainPage)
        case "/tables"    => Msg.NavigateTo(Page.TablesPage)
        case "/roll"      => Msg.NavigateTo(Page.DiceRollPage)
        case "/timelines" => Msg.NavigateTo(Page.TimelinesPage)
        case "/preview"   => Msg.NavigateTo(Page.PreviewPage)
        case "/screept"   => Msg.NavigateTo(Page.ScreeptPage)
        case "/gentree"   => Msg.NavigateTo(Page.GenTreePage)
        case "/dialog"    => Msg.NavigateTo(Page.DialogPage)
        case "/dialoggame" => Msg.NavigateTo(Page.DialogGamePage)
        case "/shadcn"    => Msg.NavigateTo(Page.ShadcnPage)
        case _            => Msg.NoMsg
    case loc: Location.External =>
      Msg.NoMsg

  override def init(flags: Map[String, String]): (Model, Cmd[IO, Msg]) =
    val tablesModel    = TablesApp.initEmpty
    val diceRollModel  = DiceRoll.init
    val timelinesModel = TimelinesApp.init
    val previewModel   = Preview.init
    val screeptModel   = ScreeptApp.init
    val genTreeModel   = GenTreeApp.init
    val dialogModel    = DialogApp.init
    val dialogGameModel = DialogGameApp.init
    val shadcnModel     = ShadcnShowcase.init
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
        shadcnModel
      ),
      Cmd.Batch(
        DialogApp.initCmd.map(Msg.UpdateDialog.apply),
        DialogGameApp.initCmd.map(Msg.UpdateDialogGame.apply)
      )
    )

  override def view(model: Model): Html[Msg] =
    div(cls := "flex flex-col gap-2 p-10")(
      SectionTabs(
        SectionTabs.Props(
          tabs = List(
            SectionTabs.TabItem("main", "Mains"),
            SectionTabs.TabItem("tables", "X Tables"),
            SectionTabs.TabItem("roll", "Roll"),
            SectionTabs.TabItem("timelines", "Timelines 2"),
            SectionTabs.TabItem("preview", "Preview"),
            SectionTabs.TabItem("screept", "Screept"),
            SectionTabs.TabItem("gentree", "GenTree"),
            SectionTabs.TabItem("dialog", "Dialog"),
            SectionTabs.TabItem("dialoggame", "Dialog Game"),
            SectionTabs.TabItem("shadcn", "Shadcn")
          ),
          activeTabId = pageToTabId(model.page),
          onTabClick = tabId => Msg.NavigateTo(tabIdToPage(tabId))
        )
      ),
      div(cls := "mt-4")(
        model.page match {
          case Page.MainPage      => div()("APP")
          case Page.TablesPage    => TablesApp.view(model.tables).map(Msg.UpdateTablesApp.apply)
          case Page.DiceRollPage  => DiceRoll.view(model.diceRoll).map(Msg.UpdateDiceRollApp.apply)
          case Page.TimelinesPage => TimelinesApp.view(model.timelines).map(Msg.UpdateTimelines.apply)
          case Page.PreviewPage   => Preview.view(model.preview).map(Msg.UpdatePreview.apply)
          case Page.ScreeptPage   => ScreeptApp.view(model.screept).map(Msg.UpdateScreept.apply)
          case Page.GenTreePage   => GenTreeApp.view(model.genTree).map(Msg.UpdateGenTree.apply)
          case Page.DialogPage    => DialogApp.view(model.dialog).map(Msg.UpdateDialog.apply)
          case Page.DialogGamePage =>
            DialogGameApp.view(model.dialogGame).map(Msg.UpdateDialogGame.apply)
          case Page.ShadcnPage =>
            ShadcnShowcase.view(model.shadcn).map(Msg.UpdateShadcn.apply)
        }
      )
    )

  override def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = {
    case Msg.NoMsg => (model, Cmd.None)
    case Msg.UpdateTablesApp(tMsg) =>
      val (tablesModel, tablesCmd) = TablesApp.update(model.tables)(tMsg)
      (model.copy(tables = tablesModel), tablesCmd.map(Msg.UpdateTablesApp.apply))
    case Msg.NavigateTo(page) =>
      (model.copy(page = page), Nav.pushUrl(pageToPath(page)))
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
  }

  override def subscriptions(model: Model): Sub[IO, Msg] =
    Sub.None
}

enum Page:
  case MainPage, TablesPage, DiceRollPage, TimelinesPage, PreviewPage, ScreeptPage,
    GenTreePage, DialogPage, DialogGamePage, ShadcnPage
