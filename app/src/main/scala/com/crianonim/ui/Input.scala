package com.crianonim.ui


import tyrian.Html.*
import tyrian.*

object Input {
  def interactive[A](value_ : String, msg: String => A, type_ : String = "text"): Html[A] =
    input(onChange(msg), value :=  value_ , cls := "p-2 w-[inherit] rounded border border-storm-dust-500", `type` := type_)
}
