package com.crianonim.janscape

/** The effort check's verdict: whether a roll of 1..rollMax clears the threshold. */
case class FatigueCheck(success: Boolean, roll: Int, threshold: Int)

case class RestOutcome(character: Character, turns: Int, fatigue: Int)

object Fatigue:

  /** The effort a body at `fatigue` puts up: fatigue raised to the configured exponent, so the cost
    * of acting climbs faster than the fatigue itself. At the shipped numbers a roll of 1-100 faces
    * fatigue squared — nothing fails below 2, and at the cap of 10 only a perfect roll gets
    * through.
    */
  def fatigueThreshold(fatigue: Int, play: PlayConfig): Int =
    val clamped = math.min(play.fatigue.max, math.max(0, fatigue))
    math.pow(clamped, play.fatigue.exponent).toInt

  /** The roll is drawn by the caller so a swing's randomness stays in one place; a miss costs the
    * turns it took and nothing else — no node, no XP, no fatigue.
    */
  def checkFatigue(fatigue: Int, play: PlayConfig, roll: Int): FatigueCheck =
    val threshold = fatigueThreshold(fatigue, play)
    FatigueCheck(success = roll >= threshold, roll = roll, threshold = threshold)

  /** The share of swings that land, for telling the player what they are working against rather
    * than making them discover it. Rolls run from 1, so the count of rolls that clear the threshold
    * starts at the threshold itself and a rested character lands every swing.
    */
  def landChance(fatigue: Int, play: PlayConfig): Double =
    val clears = math.max(1, fatigueThreshold(fatigue, play))
    math.min(1, math.max(0, (play.fatigue.rollMax - clears + 1).toDouble / play.fatigue.rollMax))

  /** Resting recovers one level of fatigue for a fixed turn cost, so the economy stays stable no
    * matter how tired the character got.
    */
  def rest(character: Character, play: PlayConfig): RestOutcome =
    RestOutcome(
      character = Character.addFatigue(
        Character.addTurns(character, play.rest.turns),
        -1,
        play.fatigue.max
      ),
      turns = play.rest.turns,
      fatigue = math.max(0, character.fatigue - 1)
    )

  def canRest(character: Character): Boolean = character.fatigue > 0
