package com.crianonim.ui

import tyrian.Html.*
import tyrian.*

object Card {

  /** Represents the visual style variant of a card
    *
    * @param Default
    *   Standard card with border and padding
    * @param Elevated
    *   Card with shadow instead of border
    * @param Outlined
    *   Card with thicker border
    */
  enum Variant {
    case Default
    case Elevated
    case Outlined
  }

  /** Represents different padding sizes for the card
    */
  enum Padding {
    case Small
    case Medium
    case Large
  }

  /** Props for configuring the Card component
    *
    * @param variant
    *   Visual style variant
    * @param padding
    *   Padding size for the card content
    * @param customClasses
    *   Additional CSS classes to apply
    */
  case class Props(
      variant: Variant = Variant.Default,
      padding: Padding = Padding.Medium,
      customClasses: String = ""
  )

  private def variantClasses(variant: Variant): String = variant match {
    case Variant.Default =>
      "border border-gray-300 bg-white"
    case Variant.Elevated =>
      "shadow-md bg-white"
    case Variant.Outlined =>
      "border-2 border-gray-400 bg-white"
  }

  private def paddingClasses(padding: Padding): String = padding match {
    case Padding.Small  => "p-4"
    case Padding.Medium => "p-6"
    case Padding.Large  => "p-8"
  }

  private def baseClasses: String = "rounded-lg"

  /** Renders a card with optional header section
    *
    * @param props
    *   Configuration for the card
    * @param header
    *   Optional header content (typically title and description)
    * @param content
    *   Main card content
    * @return
    *   HTML element representing the card
    */
  def apply[A](props: Props)(header: Option[Html[A]], content: Html[A]*): Html[A] = {
    val classes =
      s"$baseClasses ${variantClasses(props.variant)} ${paddingClasses(props.padding)} ${props.customClasses}".trim

    div(cls := "flex flex-col gap-4")(
      div(cls := classes)(
        header match {
          case Some(headerContent) =>
            div(cls := "flex flex-col gap-4")(
              headerContent,
              div()(content*)
            )
          case None =>
            div()(content*)
        }
      )
    )
  }

  /** Creates a card with title and description header
    *
    * @param title
    *   Card title
    * @param description
    *   Card description
    * @param content
    *   Main card content
    * @param variant
    *   Visual style variant
    * @param padding
    *   Padding size
    * @return
    *   HTML element representing the card
    */
  def withHeader[A](
      title: String,
      description: String,
      variant: Variant = Variant.Default,
      padding: Padding = Padding.Medium
  )(content: Html[A]*): Html[A] = {
    val header = div(cls := "flex flex-col gap-1")(
      h2(cls := "text-xl font-semibold")(text(title)),
      p(cls := "text-gray-600")(text(description))
    )
    apply(Props(variant, padding))(Some(header), content*)
  }

  /** Creates a simple card without header
    *
    * @param content
    *   Main card content
    * @param variant
    *   Visual style variant
    * @param padding
    *   Padding size
    * @return
    *   HTML element representing the card
    */
  def simple[A](
      variant: Variant = Variant.Default,
      padding: Padding = Padding.Medium
  )(content: Html[A]*): Html[A] = {
    apply(Props(variant, padding))(None, content*)
  }

  /** Creates an elevated card with shadow
    *
    * @param title
    *   Card title
    * @param description
    *   Card description
    * @param content
    *   Main card content
    * @param padding
    *   Padding size
    * @return
    *   HTML element representing the card
    */
  def elevated[A](
      title: String,
      description: String,
      padding: Padding = Padding.Medium
  )(content: Html[A]*): Html[A] = {
    withHeader(title, description, Variant.Elevated, padding)(content*)
  }

  /** Creates an outlined card with thicker border
    *
    * @param title
    *   Card title
    * @param description
    *   Card description
    * @param content
    *   Main card content
    * @param padding
    *   Padding size
    * @return
    *   HTML element representing the card
    */
  def outlined[A](
      title: String,
      description: String,
      padding: Padding = Padding.Medium
  )(content: Html[A]*): Html[A] = {
    withHeader(title, description, Variant.Outlined, padding)(content*)
  }

  /** Creates a content section within a card (typically for the main content area)
    *
    * @param content
    *   Content to wrap
    * @return
    *   HTML element with appropriate styling for card content
    */
  def contentSection[A](content: Html[A]*): Html[A] = {
    div(cls := "bg-gray-50 p-4 rounded border border-gray-200")(
      content*
    )
  }
}
