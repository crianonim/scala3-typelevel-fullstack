package com.crianonim.shadcn

import tyrian.*
import tyrian.Html.{svg, cls, attribute}
import tyrian.SVG.{path, circle, line, polygon, d, viewBox, fill, stroke, cx, cy, r, x1, x2, y1, y2, points}

/** Minimal inline-SVG icon set (lucide-style, 24x24 stroke icons) used by the shadcn components. */
object Icons {

  private def base[A](extraCls: String)(children: Elem[A]*): Html[A] =
    svg(
      cls               := extraCls,
      viewBox           := "0 0 24 24",
      fill              := "none",
      stroke            := "currentColor",
      attribute("stroke-width", "2"),
      attribute("stroke-linecap", "round"),
      attribute("stroke-linejoin", "round"),
      attribute("xmlns", "http://www.w3.org/2000/svg")
    )(children*)

  def check[A](c: String = "h-4 w-4"): Html[A]        = base(c)(path(d := "M20 6 9 17l-5-5"))
  def chevronDown[A](c: String = "h-4 w-4"): Html[A]  = base(c)(path(d := "m6 9 6 6 6-6"))
  def chevronUp[A](c: String = "h-4 w-4"): Html[A]    = base(c)(path(d := "m18 15-6-6-6 6"))
  def chevronRight[A](c: String = "h-4 w-4"): Html[A] = base(c)(path(d := "m9 18 6-6-6-6"))
  def chevronLeft[A](c: String = "h-4 w-4"): Html[A]  = base(c)(path(d := "m15 18-6-6 6-6"))
  def plus[A](c: String = "h-4 w-4"): Html[A]         = base(c)(path(d := "M5 12h14"), path(d := "M12 5v14"))
  def minus[A](c: String = "h-4 w-4"): Html[A]        = base(c)(path(d := "M5 12h14"))
  def x[A](c: String = "h-4 w-4"): Html[A]            = base(c)(path(d := "M18 6 6 18"), path(d := "m6 6 12 12"))
  def arrowLeft[A](c: String = "h-4 w-4"): Html[A]    = base(c)(path(d := "m12 19-7-7 7-7"), path(d := "M19 12H5"))
  def arrowRight[A](c: String = "h-4 w-4"): Html[A]   = base(c)(path(d := "M5 12h14"), path(d := "m12 5 7 7-7 7"))
  def search[A](c: String = "h-4 w-4"): Html[A] =
    base(c)(circle(cx := "11", cy := "11", r := "8"), path(d := "m21 21-4.3-4.3"))
  def star[A](c: String = "h-4 w-4"): Html[A] =
    base(c)(
      polygon(points := "12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26")
    )
  def ellipsis[A](c: String = "h-4 w-4"): Html[A] =
    base(c)(circle(cx := "12", cy := "12", r := "1"), circle(cx := "19", cy := "12", r := "1"), circle(cx := "5", cy := "12", r := "1"))
  def info[A](c: String = "h-4 w-4"): Html[A] =
    base(c)(circle(cx := "12", cy := "12", r := "10"), path(d := "M12 16v-4"), path(d := "M12 8h.01"))
  def user[A](c: String = "h-4 w-4"): Html[A] =
    base(c)(path(d := "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2"), circle(cx := "12", cy := "7", r := "4"))
  def bell[A](c: String = "h-4 w-4"): Html[A] =
    base(c)(path(d := "M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"), path(d := "M10.3 21a1.94 1.94 0 0 0 3.4 0"))
  def circleIcon[A](c: String = "h-4 w-4"): Html[A] = base(c)(circle(cx := "12", cy := "12", r := "10"))
  def panelLeft[A](c: String = "h-4 w-4"): Html[A] =
    base(c)(
      path(d := "M3 5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2Z"),
      line(x1 := "9", x2 := "9", y1 := "3", y2 := "21")
    )
  def sun[A](c: String = "h-4 w-4"): Html[A] =
    base(c)(
      circle(cx := "12", cy := "12", r := "4"),
      path(d := "M12 2v2"),
      path(d := "M12 20v2"),
      path(d := "m4.93 4.93 1.41 1.41"),
      path(d := "m17.66 17.66 1.41 1.41"),
      path(d := "M2 12h2"),
      path(d := "M20 12h2"),
      path(d := "m6.34 17.66-1.41 1.41"),
      path(d := "m19.07 4.93-1.41 1.41")
    )
  def moon[A](c: String = "h-4 w-4"): Html[A] =
    base(c)(path(d := "M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"))
}
