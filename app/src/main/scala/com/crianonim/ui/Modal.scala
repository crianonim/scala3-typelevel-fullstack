package com.crianonim.ui

import tyrian.Html.*
import tyrian.*
import tyrian.SVG.*

object Modal {

  /** Props for configuring the Modal component
    *
    * @param isOpen
    *   Whether the modal is visible
    * @param onClose
    *   Message to send when modal should be closed
    * @param title
    *   Optional title for the modal
    * @param size
    *   Size variant for the modal content
    */
  case class Props[A](
      isOpen: Boolean,
      onClose: A,
      title: Option[String] = None,
      size: Size = Size.Medium
  )

  /** Size variants for the modal content area
    */
  enum Size {
    case Small
    case Medium
    case Large
    case ExtraLarge
  }

  private def sizeClasses(size: Size): String = size match {
    case Size.Small      => "max-w-md"
    case Size.Medium     => "max-w-lg"
    case Size.Large      => "max-w-2xl"
    case Size.ExtraLarge => "max-w-4xl"
  }

  /** Renders a modal overlay with centered content
    *
    * @param props
    *   Configuration for the modal
    * @param content
    *   Content to display inside the modal
    * @return
    *   HTML element representing the modal
    */
  def apply[A](props: Props[A])(content: Html[A]*): Html[A] = {
    if (!props.isOpen) {
      div()()
    } else {
      // Backdrop overlay
      div(
        cls := "fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50 p-4"
      )(
        // Modal content container
        div(
          cls := s"bg-white rounded-lg shadow-xl w-full ${sizeClasses(props.size)} max-h-[90vh] overflow-y-auto"
        )(
          // Header with optional title and close button
          div(cls := "sticky top-0 bg-white border-b border-gray-200 px-6 py-4 flex justify-between items-center")(
            props.title match {
              case Some(title) =>
                h2(cls := "text-xl font-semibold text-gray-900")(text(title))
              case None =>
                div()()
            },
            button(
              cls := "text-gray-400 hover:text-gray-600 transition-colors",
              onClick(props.onClose),
              attribute("type", "button"),
              attribute("aria-label", "Close modal")
            )(
              // X icon (unicode symbol)
              div(cls := "text-2xl leading-none")(text("×"))
            )
          ),
          // Modal body content
          div(cls := "px-6 py-4")(
            content*
          )
        )
      )
    }
  }

  /** Creates a simple modal with title
    *
    * @param isOpen
    *   Whether the modal is visible
    * @param onClose
    *   Message to send when modal should be closed
    * @param title
    *   Title for the modal
    * @param size
    *   Size variant
    * @param content
    *   Content to display
    * @return
    *   HTML element representing the modal
    */
  def withTitle[A](
      isOpen: Boolean,
      onClose: A,
      title: String,
      size: Size = Size.Medium
  )(content: Html[A]*): Html[A] = {
    apply(Props(isOpen, onClose, Some(title), size))(content*)
  }

  /** Creates a simple modal without title
    *
    * @param isOpen
    *   Whether the modal is visible
    * @param onClose
    *   Message to send when modal should be closed
    * @param size
    *   Size variant
    * @param content
    *   Content to display
    * @return
    *   HTML element representing the modal
    */
  def simple[A](
      isOpen: Boolean,
      onClose: A,
      size: Size = Size.Medium
  )(content: Html[A]*): Html[A] = {
    apply(Props(isOpen, onClose, None, size))(content*)
  }
}
