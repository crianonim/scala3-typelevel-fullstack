package com.crianonim.gentree

import cats.effect.*
import tyrian.*
import tyrian.Html.*
import cats.syntax.all.*
import org.scalajs.dom
import io.circe.syntax.*
import io.circe.parser.*
import scala.scalajs.js

import com.crianonim.ui.*
import com.crianonim.timelines.TimePoint
import com.crianonim.gentree.Person

object GenTreeApp {
  case class Model(
      people: List[Person],
      selectedPersonId: Option[String],
      // Add/Edit person form state
      addFormVisible: Boolean = false,
      editingPersonId: Option[String] = None,
      formName: String = "",
      formDescription: String = "",
      formBorn: Option[TimePoint] = None,
      formDied: Option[TimePoint] = None,
      formMotherId: Option[String] = None,
      formFatherId: Option[String] = None,
      // Import/Export modal state
      importExportModalVisible: Boolean = false,
      importError: Option[String] = None
  )

  enum Msg {
    case Noop
    case SelectPerson(id: String)
    case UnselectPerson
    // Add/Edit form
    case ShowAddForm
    case ShowEditForm(id: String)
    case HideAddForm
    case UpdateFormName(name: String)
    case UpdateFormDescription(desc: String)
    case UpdateFormBorn(tp: Option[TimePoint])
    case UpdateFormDied(tp: Option[TimePoint])
    case UpdateFormMother(id: String)
    case UpdateFormFather(id: String)
    case SubmitPerson
    // Import/Export
    case ShowImportExportModal
    case HideImportExportModal
    case ExportPeople
    case FileSelected(file: org.scalajs.dom.File)
    case FileContentLoaded(content: String)
    case ClearImportError
  }

  def init: Model = Model(
    people = List.empty,
    selectedPersonId = None
  )

  def update(model: Model): Msg => (Model, Cmd[IO, Msg]) = {
    case Msg.Noop => (model, Cmd.None)

    case Msg.SelectPerson(id) =>
      val newSelection =
        if (model.selectedPersonId.contains(id)) None else Some(id)
      (model.copy(selectedPersonId = newSelection), Cmd.None)

    case Msg.UnselectPerson =>
      (model.copy(selectedPersonId = None), Cmd.None)

    // Add/Edit form handlers
    case Msg.ShowAddForm =>
      (model.copy(addFormVisible = true, editingPersonId = None), Cmd.None)

    case Msg.ShowEditForm(id) =>
      findPerson(model.people, id) match {
        case Some(person) =>
          (
            model.copy(
              addFormVisible = true,
              editingPersonId = Some(id),
              formName = person.name,
              formDescription = person.description.getOrElse(""),
              formBorn = Some(person.born),
              formDied = person.died,
              formMotherId = person.motherId,
              formFatherId = person.fatherId
            ),
            Cmd.None
          )
        case None => (model, Cmd.None)
      }

    case Msg.HideAddForm =>
      (
        model.copy(
          addFormVisible = false,
          editingPersonId = None,
          formName = "",
          formDescription = "",
          formBorn = None,
          formDied = None,
          formMotherId = None,
          formFatherId = None
        ),
        Cmd.None
      )

    case Msg.UpdateFormName(name) =>
      (model.copy(formName = name), Cmd.None)

    case Msg.UpdateFormDescription(desc) =>
      (model.copy(formDescription = desc), Cmd.None)

    case Msg.UpdateFormBorn(tp) =>
      (model.copy(formBorn = tp), Cmd.None)

    case Msg.UpdateFormDied(tp) =>
      (model.copy(formDied = tp), Cmd.None)

    case Msg.UpdateFormMother(id) =>
      (model.copy(formMotherId = if (id.isEmpty) None else Some(id)), Cmd.None)

    case Msg.UpdateFormFather(id) =>
      (model.copy(formFatherId = if (id.isEmpty) None else Some(id)), Cmd.None)

    case Msg.SubmitPerson =>
      if (model.formName.nonEmpty && model.formBorn.isDefined) {
        val desc =
          if (model.formDescription.isEmpty) None
          else Some(model.formDescription)
        val updatedPeople = model.editingPersonId match {
          case Some(editId) =>
            model.people.map { p =>
              if (p.id == editId)
                p.copy(
                  name = model.formName,
                  description = desc,
                  born = model.formBorn.get,
                  died = model.formDied,
                  motherId = model.formMotherId,
                  fatherId = model.formFatherId
                )
              else p
            }
          case None =>
            val id =
              s"p-${System.currentTimeMillis()}-${scala.util.Random.nextInt(10000)}"
            model.people :+ Person(
              id = id,
              name = model.formName,
              description = desc,
              born = model.formBorn.get,
              died = model.formDied,
              motherId = model.formMotherId,
              fatherId = model.formFatherId
            )
        }
        (
          model.copy(
            people = updatedPeople,
            addFormVisible = false,
            editingPersonId = None,
            formName = "",
            formDescription = "",
            formBorn = None,
            formDied = None,
            formMotherId = None,
            formFatherId = None
          ),
          Cmd.None
        )
      } else {
        (model, Cmd.None)
      }

    // Import/Export handlers
    case Msg.ShowImportExportModal =>
      (model.copy(importExportModalVisible = true, importError = None), Cmd.None)

    case Msg.HideImportExportModal =>
      (model.copy(importExportModalVisible = false, importError = None), Cmd.None)

    case Msg.ExportPeople =>
      val json = model.people.asJson.spaces2
      val downloadCmd = Cmd.SideEffect[IO, Unit](
        IO {
          val blob = new dom.Blob(
            js.Array(json),
            dom.BlobPropertyBag(`type` = "application/json")
          )
          val url  = dom.URL.createObjectURL(blob)
          val link = dom.document.createElement("a").asInstanceOf[dom.HTMLAnchorElement]
          link.href = url
          link.download = "gentree.json"
          dom.document.body.appendChild(link)
          link.click()
          dom.document.body.removeChild(link)
          dom.URL.revokeObjectURL(url)
        }
      )
      (model, downloadCmd)

    case Msg.FileSelected(file) =>
      (model, FileInput.readFileCmd(file)(Msg.FileContentLoaded.apply))

    case Msg.FileContentLoaded(content) =>
      if (content.isEmpty) {
        (model.copy(importError = Some("Failed to read file")), Cmd.None)
      } else {
        decode[List[Person]](content) match {
          case Right(people) =>
            if (people.isEmpty) {
              (model.copy(importError = Some("No people found in file")), Cmd.None)
            } else {
              (
                model.copy(
                  people = people,
                  importExportModalVisible = false,
                  importError = None,
                  selectedPersonId = None
                ),
                Cmd.None
              )
            }
          case Left(error) =>
            (
              model.copy(importError = Some(s"Invalid JSON format: ${error.getMessage}")),
              Cmd.None
            )
        }
      }

    case Msg.ClearImportError =>
      (model.copy(importError = None), Cmd.None)
  }

  private def formatTimePoint(tp: TimePoint): String =
    cats.Show[TimePoint].show(tp)

  private def findPerson(people: List[Person], id: String): Option[Person] =
    people.find(_.id == id)

  private def viewPersonListItem(model: Model)(person: Person): Html[Msg] = {
    val isSelected = model.selectedPersonId.contains(person.id)
    val containerCls =
      if (isSelected)
        "flex flex-col gap-1 p-3 cursor-pointer bg-blue-100 border-b border-gray-200"
      else
        "flex flex-col gap-1 p-3 cursor-pointer hover:bg-gray-50 border-b border-gray-200"

    div(
      cls := containerCls,
      onClick(Msg.SelectPerson(person.id))
    )(
      div(cls := "font-medium text-gray-900")(text(person.name)),
      div(cls := "text-xs text-gray-500")(
        text(s"b. ${formatTimePoint(person.born)}")
      )
    )
  }

  private def viewPersonDetail(model: Model, person: Person): Html[Msg] = {
    Card.simple(Card.Variant.Elevated, Card.Padding.Medium)(
      div(cls := "flex flex-col gap-4")(
        div(cls := "flex justify-between items-center")(
          div(cls := "text-xl font-semibold text-gray-900")(text(person.name)),
          Button.secondary("Edit", Msg.ShowEditForm(person.id), Button.Size.Small)
        ),
        person.description match {
          case Some(desc) =>
            div(cls := "text-gray-600")(text(desc))
          case None => div()()
        },
        div(cls := "flex flex-col gap-2")(
          div(cls := "flex gap-2")(
            div(cls := "font-medium text-gray-700")(text("Born:")),
            div(cls := "text-gray-600")(text(formatTimePoint(person.born)))
          ),
          person.died match {
            case Some(d) =>
              div(cls := "flex gap-2")(
                div(cls := "font-medium text-gray-700")(text("Died:")),
                div(cls := "text-gray-600")(text(formatTimePoint(d)))
              )
            case None => div()()
          }
        ),
        div(cls := "flex flex-col gap-2 pt-2 border-t border-gray-200")(
          div(cls := "font-medium text-gray-700")(text("Parents")),
          person.motherId match {
            case Some(mid) =>
              findPerson(model.people, mid) match {
                case Some(mother) =>
                  div(cls := "flex gap-2 items-center")(
                    div(cls := "text-gray-500 text-sm")(text("Mother:")),
                    div(
                      cls := "text-blue-600 cursor-pointer hover:underline",
                      onClick(Msg.SelectPerson(mid))
                    )(text(mother.name))
                  )
                case None =>
                  div(cls := "flex gap-2")(
                    div(cls := "text-gray-500 text-sm")(text("Mother:")),
                    div(cls := "text-gray-400 italic")(text("Unknown"))
                  )
              }
            case None => div()()
          },
          person.fatherId match {
            case Some(fid) =>
              findPerson(model.people, fid) match {
                case Some(father) =>
                  div(cls := "flex gap-2 items-center")(
                    div(cls := "text-gray-500 text-sm")(text("Father:")),
                    div(
                      cls := "text-blue-600 cursor-pointer hover:underline",
                      onClick(Msg.SelectPerson(fid))
                    )(text(father.name))
                  )
                case None =>
                  div(cls := "flex gap-2")(
                    div(cls := "text-gray-500 text-sm")(text("Father:")),
                    div(cls := "text-gray-400 italic")(text("Unknown"))
                  )
              }
            case None => div()()
          }
        )
      )
    )
  }

  private def viewPersonFormModal(model: Model): Html[Msg] = {
    val isEditing    = model.editingPersonId.isDefined
    val modalTitle   = if (isEditing) "Edit Person" else "Add Person"
    val submitLabel  = if (isEditing) "Save Changes" else "Add Person"
    Modal.withTitle(
      model.addFormVisible,
      Msg.HideAddForm,
      modalTitle,
      Modal.Size.Large
    )(
      div(cls := "flex flex-col gap-6")(
        // Name input
        div(cls := "flex flex-col gap-2")(
          div(cls := "font-medium text-sm text-gray-700")(text("Name")),
          Input.interactive(
            model.formName,
            Msg.UpdateFormName.apply,
            "text"
          )
        ),
        // Description input
        div(cls := "flex flex-col gap-2")(
          div(cls := "font-medium text-sm text-gray-700")(
            text("Description (optional)")
          ),
          Input.interactive(
            model.formDescription,
            Msg.UpdateFormDescription.apply,
            "text"
          )
        ),
        // Born date input
        DateInput.simple(
          "Born",
          model.formBorn,
          Msg.UpdateFormBorn.apply
        ),
        // Died date input
        DateInput.simple(
          "Died (optional)",
          model.formDied,
          Msg.UpdateFormDied.apply
        ),
        // Mother selector
        {
          val candidates =
            model.people.filterNot(p => model.editingPersonId.contains(p.id)).sortBy(_.name)
          div(cls := "flex flex-col gap-2")(
            div(cls := "font-medium text-sm text-gray-700")(
              text("Mother (optional)")
            ),
            select(
              cls := "px-3 py-2 rounded border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none",
              onChange(Msg.UpdateFormMother.apply)
            )(
              (option(value := "")(text("-- None --")) ::
                candidates.map { p =>
                  if (model.formMotherId.contains(p.id))
                    option(value := p.id, selected := true)(text(p.name))
                  else
                    option(value := p.id)(text(p.name))
                })*
            )
          )
        },
        // Father selector
        {
          val candidates =
            model.people.filterNot(p => model.editingPersonId.contains(p.id)).sortBy(_.name)
          div(cls := "flex flex-col gap-2")(
            div(cls := "font-medium text-sm text-gray-700")(
              text("Father (optional)")
            ),
            select(
              cls := "px-3 py-2 rounded border border-gray-300 focus:border-blue-500 focus:ring-1 focus:ring-blue-500 outline-none",
              onChange(Msg.UpdateFormFather.apply)
            )(
              (option(value := "")(text("-- None --")) ::
                candidates.map { p =>
                  if (model.formFatherId.contains(p.id))
                    option(value := p.id, selected := true)(text(p.name))
                  else
                    option(value := p.id)(text(p.name))
                })*
            )
          )
        },
        // Action buttons
        div(cls := "flex gap-3 justify-end pt-4 border-t border-gray-200")(
          Button.secondary("Cancel", Msg.HideAddForm),
          if (model.formName.nonEmpty && model.formBorn.isDefined)
            Button.primary(submitLabel, Msg.SubmitPerson)
          else
            Button.disabledButton(submitLabel)
        )
      )
    )
  }

  private def viewImportExportModal(model: Model): Html[Msg] = {
    Modal.withTitle(
      model.importExportModalVisible,
      Msg.HideImportExportModal,
      "Import / Export People",
      Modal.Size.Large
    )(
      div(cls := "flex flex-col gap-6")(
        // Export Section
        Card.simple(Card.Variant.Outlined, Card.Padding.Medium)(
          div(cls := "flex flex-col gap-4")(
            div(cls := "flex flex-col gap-2")(
              div(cls := "text-lg font-semibold text-gray-800")(
                text("Export People")
              ),
              div(cls := "text-sm text-gray-600")(
                text(s"Export all ${model.people.length} people to a JSON file")
              )
            ),
            Button.primary(
              "Download gentree.json",
              Msg.ExportPeople,
              Button.Size.Medium
            )
          )
        ),
        // Import Section
        Card.simple(Card.Variant.Outlined, Card.Padding.Medium)(
          div(cls := "flex flex-col gap-4")(
            div(cls := "flex flex-col gap-2")(
              div(cls := "text-lg font-semibold text-gray-800")(
                text("Import People")
              ),
              div(cls := "text-sm text-gray-600")(
                div()(text("Upload a JSON file to replace all current people.")),
                div(cls := "text-orange-600 font-medium mt-1")(
                  text("Warning: This will replace all existing people!")
                )
              )
            ),
            FileInput(
              Msg.FileSelected.apply,
              accept = ".json,application/json",
              label = Some("Choose JSON file")
            ),
            model.importError match {
              case Some(error) =>
                div(cls := "bg-red-50 border border-red-200 rounded-md p-3")(
                  div(cls := "flex items-start gap-2")(
                    div(cls := "text-red-600 font-semibold")(
                      text("Import Error:")
                    ),
                    div(cls := "text-red-700 text-sm flex-1")(text(error))
                  ),
                  div(cls := "mt-2")(
                    Button.secondary(
                      "Clear Error",
                      Msg.ClearImportError,
                      Button.Size.Small
                    )
                  )
                )
              case None =>
                div()()
            }
          )
        ),
        // Close button
        div(cls := "flex justify-end pt-4 border-t border-gray-200")(
          Button.secondary(
            "Close",
            Msg.HideImportExportModal,
            Button.Size.Medium
          )
        )
      )
    )
  }

  def view(model: Model): Html[Msg] = {
    div(cls := "flex flex-col gap-4")(
      // Header
      div(cls := "flex justify-between items-center")(
        div(cls := "text-lg font-semibold text-gray-700")(text("GenTree")),
        div(cls := "flex gap-2")(
          Button.secondary(
            "Import/Export",
            Msg.ShowImportExportModal,
            Button.Size.Medium
          ),
          Button.primary("Add Person", Msg.ShowAddForm, Button.Size.Medium)
        )
      ),
      // Two-panel layout
      div(cls := "flex gap-4")(
        // Left: scrollable person list
        div(cls := "w-1/3 max-h-[70vh] overflow-y-auto border rounded")(
          if (model.people.isEmpty)
            div(cls := "p-4 text-gray-500 italic")(
              text("No people added yet")
            )
          else
            div()(
              model.people.sortBy(_.name).map(viewPersonListItem(model))*
            )
        ),
        // Right: detail panel
        div(cls := "w-2/3")(
          model.selectedPersonId.flatMap(id =>
            findPerson(model.people, id)
          ) match {
            case Some(person) => viewPersonDetail(model, person)
            case None =>
              div(cls := "text-gray-500 italic p-4")(
                text("Select a person to view details")
              )
          }
        )
      ),
      // Modals
      viewPersonFormModal(model),
      viewImportExportModal(model)
    )
  }
}
