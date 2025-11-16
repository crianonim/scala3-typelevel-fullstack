package com.crianonim.ui

import cats.effect.IO
import org.scalajs.dom
import org.scalajs.dom.{Event, FileReader}
import tyrian.Html.*
import tyrian.*
import tyrian.Cmd

import scala.scalajs.js

object FileInput {

  /** Creates a Cmd that reads a file and emits a message with its content
    * @param file The file to read
    * @param onContent Callback that receives the file content (or empty string on error)
    */
  def readFileCmd[A](file: dom.File)(onContent: String => A): Cmd[IO, A] =
    Cmd.Run[IO, Either[Throwable, String], A](
      IO.async_[Either[Throwable, String]] { cb =>
        val reader = new FileReader()

        reader.onload = (_: Event) =>
          cb(Right(Right(reader.result.asInstanceOf[String])))

        reader.onerror = (_: Event) =>
          cb(Right(Left(new Exception("Failed to read file"))))

        reader.readAsText(file)
      }
    ) {
      case Right(content) => onContent(content)
      case Left(_)        => onContent("")
    }

  /** Creates a file input component that emits a message when file is selected
    * Use with readFileCmd to actually read the file content
    * @param onFileSelected Message constructor that receives the file object
    * @param accept Optional file type filter (e.g., ".json", "application/json")
    * @param label Optional label text to display
    * @param id Optional element ID
    */
  def apply[A](
      onFileSelected: dom.File => A,
      accept: String = ".json",
      label: Option[String] = None,
      id: String = s"file-input-${scala.util.Random.nextInt(100000)}"
  ): Html[A] = {

    val labelElement = label.map { text =>
      Html.label(`for` := id, cls := "block text-sm font-medium text-gray-700 mb-2")(
        text
      )
    }

    val fileInput = input(
      `type` := "file",
      Attribute("id", id),
      Attribute("accept", accept),
      cls := "block w-full text-sm text-gray-500 file:mr-4 file:py-2 file:px-4 file:rounded-md file:border-0 file:text-sm file:font-semibold file:bg-blue-50 file:text-blue-700 hover:file:bg-blue-100 cursor-pointer border border-gray-300 rounded-md p-1",
      onChange { _ =>
        // Get the file from the input element via direct DOM access
        val inputElement = dom.document.getElementById(id).asInstanceOf[dom.HTMLInputElement]
        val files = inputElement.files

        if (files != null && files.length > 0) {
          onFileSelected(files(0))
        } else {
          onFileSelected(null) // or handle the empty case differently
        }
      }
    )

    Html.div(cls := "file-input-wrapper")(
      (labelElement.toList :+ fileInput)*
    )
  }

  /** Simpler file input without label */
  def simple[A](onFileSelected: dom.File => A, accept: String = ".json"): Html[A] =
    apply(onFileSelected, accept, None)
}
