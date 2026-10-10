package com.crianonim.ui


import tyrian.Html.*
import tyrian.*

object Input {
  def interactive[A](
      value_ : String,
      msg: String => A,
      type_ : String = "text",
      className: String = ""
  ): Html[A] =
    input(
      onChange(msg),
      value := value_,
      cls := s"p-2 w-[inherit] rounded border border-storm-dust-500 $className".trim,
      `type` := type_
    )
}
