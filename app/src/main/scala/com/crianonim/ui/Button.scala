package com.crianonim.ui

import tyrian.Html.*
import tyrian.*

object Button {
  def interactive[A](label: String, msg: A): Html[A] =
    button(onClick(msg), cls := "p-2 rounded border border-storm-dust-900")(label)
}
