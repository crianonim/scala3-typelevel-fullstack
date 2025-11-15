package com.crianonim.ui

import tyrian.Html.*
import tyrian.*

object Button {

  enum Variant {
    case Primary
    case Secondary
    case Disabled
  }

  enum Size {
    case Small
    case Medium
    case Large
  }

  private def variantClasses(variant: Variant): String = variant match {
    case Variant.Primary =>
      "bg-blue-600 text-white border-blue-600 hover:bg-blue-700 hover:border-blue-700 active:bg-blue-800 active:border-blue-800 shadow-sm"
    case Variant.Secondary =>
      "bg-white text-gray-700 border-gray-300 hover:bg-gray-50 hover:border-gray-400 active:bg-gray-100 active:border-gray-500"
    case Variant.Disabled =>
      "bg-gray-200 text-gray-400 border-gray-300 cursor-not-allowed"
  }

  private def sizeClasses(size: Size): String = size match {
    case Size.Small  => "px-3 py-1.5 text-sm"
    case Size.Medium => "px-4 py-2 text-base"
    case Size.Large  => "px-6 py-3 text-lg"
  }

  private def baseClasses: String =
    "font-medium rounded-md border transition-colors duration-150 ease-in-out focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2"

  def apply[A](
      label: String,
      msg: A,
      variant: Variant = Variant.Primary,
      size: Size = Size.Medium
  ): Html[A] = {
    val classes = s"$baseClasses ${variantClasses(variant)} ${sizeClasses(size)}"
    val attrs = variant match {
      case Variant.Disabled => List(cls := classes, attribute("disabled", "true"))
      case _                => List(onClick(msg), cls := classes)
    }
    button(attrs*)(label)
  }

  // Convenience methods for common button types
  def primary[A](label: String, msg: A, size: Size = Size.Medium): Html[A] =
    apply(label, msg, Variant.Primary, size)

  def secondary[A](label: String, msg: A, size: Size = Size.Medium): Html[A] =
    apply(label, msg, Variant.Secondary, size)

  def disabledButton[A](label: String, size: Size = Size.Medium): Html[A] =
    button(
      cls := s"$baseClasses ${variantClasses(Variant.Disabled)} ${sizeClasses(size)}",
      attribute("disabled", "true")
    )(label)

  // Legacy method for backwards compatibility
  def interactive[A](label: String, msg: A): Html[A] =
    apply(label, msg, Variant.Primary, Size.Medium)
}
