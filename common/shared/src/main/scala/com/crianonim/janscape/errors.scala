package com.crianonim.janscape

import io.circe.{CursorOp, DecodingFailure}

/** A single validation complaint, rendered like the zod original: a dotted path plus a message. */
case class Issue(path: String, message: String)

object Issue:
  def when(path: String, condition: Boolean, message: String): List[Issue] =
    if condition then Nil else List(Issue(path, message))

/** Every way the game systems can fail.
  *
  * The TypeScript original throws plain strings from the middle of a transition; here a failure is
  * a value, so the caller keeps the character it already had and the UI decides what to show.
  */
enum GameError:
  case NotJson
  case InvalidSave(issues: List[Issue])
  case InvalidConfig(issues: List[Issue])
  case InvalidAction(what: String)

  def message: String = this match
    case NotJson =>
      "That file does not contain valid JSON."
    case InvalidSave(issues) =>
      "That is not a valid character save. " + issues
        .map {
          case Issue("", m)   => m
          case Issue(path, m) => s"$path: $m"
        }
        .mkString(" ")
    case InvalidConfig(issues) =>
      "Invalid game config:\n  " + issues
        .map(issue => s"${if issue.path.isEmpty then "<root>" else issue.path}: ${issue.message}")
        .mkString("\n  ")
    case InvalidAction(what) =>
      what

/** A decode failure as a dotted path plus the parser's message, so a bad save or config names the
  * field that broke rather than the whole document.
  */
private[janscape] def describe(failure: DecodingFailure): String =
  val path = failure.history
    .collect { case CursorOp.DownField(key) => key }
    .reverse
    .mkString(".")
  if path.isEmpty then failure.message else s"$path: ${failure.message}"
