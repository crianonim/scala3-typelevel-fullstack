package com.crianonim.janscape

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

enum SkillName:
  case Mining, Combat, Smithing, Forestry

val SkillNames: List[SkillName] = SkillName.values.toList

case class Skill(level: Int, xp: Int)

object Skill:
  val starting: Skill = Skill(level = 1, xp = 0)

  given Encoder[Skill] = deriveEncoder
  given Decoder[Skill] = deriveDecoder

/** The four skills a character carries, keyed the way the TypeScript original does: one named field
  * per skill, so adding a skill is a compile error wherever it is read.
  */
case class Skills(mining: Skill, combat: Skill, smithing: Skill, forestry: Skill):
  def apply(name: SkillName): Skill = name match
    case SkillName.Mining   => mining
    case SkillName.Combat   => combat
    case SkillName.Smithing => smithing
    case SkillName.Forestry => forestry

  def updated(name: SkillName, skill: Skill): Skills = name match
    case SkillName.Mining   => copy(mining = skill)
    case SkillName.Combat   => copy(combat = skill)
    case SkillName.Smithing => copy(smithing = skill)
    case SkillName.Forestry => copy(forestry = skill)

object Skills:
  val starting: Skills =
    Skills(Skill.starting, Skill.starting, Skill.starting, Skill.starting)

  given Decoder[Skills] = Decoder.instance { c =>
    def skill(name: String): Decoder.Result[Skill] =
      c.downField(name).as[Option[Skill]].map(_.getOrElse(Skill.starting))

    for
      mining   <- skill("mining")
      combat   <- skill("combat")
      smithing <- skill("smithing")
      forestry <- skill("forestry")
    yield Skills(mining, combat, smithing, forestry)
  }

  given Encoder[Skills] = deriveEncoder

/** Save-format bound rather than a balance number, so it lives here next to the schema that
  * enforces it. The progression curve itself is tunable and lives in config/game.json as
  * skills.xpPerLevelSquared.
  */
val MaxLevel: Int = 99

case class LevelProgress(
    level: Int,
    xp: Int,
    xpIntoLevel: Double,
    xpForNextLevel: Double,
    percent: Double
)

case class XpAward(skill: Skill, levelsGained: Int)

/** The tunable side of the skill curve: skills.xpPerLevelSquared in config/game.json. */
case class SkillsConfig(xpPerLevelSquared: Double):
  def issues(path: String): List[Issue] =
    Issue.when(s"$path.xpPerLevelSquared", xpPerLevelSquared > 0, "must be > 0")

object SkillsConfig:
  given Decoder[SkillsConfig] = io.circe.generic.semiauto.deriveDecoder

object Xp:
  def clampLevel(level: Int): Int =
    math.min(MaxLevel, math.max(1, level))

  def xpForLevel(level: Int, skills: SkillsConfig): Double =
    val steps = clampLevel(level) - 1
    skills.xpPerLevelSquared * steps * steps

  def levelFromXp(xp: Int, skills: SkillsConfig): Int =
    if xp < 0 then 1
    else math.min(MaxLevel, math.floor(math.sqrt(xp / skills.xpPerLevelSquared)).toInt + 1)

  def levelProgress(xp: Int, skills: SkillsConfig): LevelProgress =
    val total = math.max(0, xp)
    val level = levelFromXp(total, skills)

    if level >= MaxLevel then
      LevelProgress(level = level, xp = total, xpIntoLevel = 0, xpForNextLevel = 0, percent = 1)
    else
      val floor          = xpForLevel(level, skills)
      val xpIntoLevel    = total - floor
      val xpForNextLevel = xpForLevel(level + 1, skills) - floor
      LevelProgress(
        level = level,
        xp = total,
        xpIntoLevel = xpIntoLevel,
        xpForNextLevel = xpForNextLevel,
        percent = xpIntoLevel / xpForNextLevel
      )

  /** XP is the source of truth; level is always re-derived from it so the two can never drift
    * apart, however the save was edited.
    */
  def grantXp(skill: Skill, amount: Int, skills: SkillsConfig): XpAward =
    val xp    = skill.xp + math.max(0, amount)
    val level = levelFromXp(xp, skills)
    XpAward(Skill(level, xp), levelsGained = level - skill.level)
