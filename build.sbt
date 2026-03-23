ThisBuild / version := "1.0.1"

lazy val org           = "com.crianonim"
lazy val scala3Version = "3.6.4"

// Fix deprecated -Ykind-projector warning by using -Xkind-projector
ThisBuild / scalacOptions ++= Seq(
  "-Xkind-projector"
)
///////////////////////////////////////////////////////////////////////////////////////////////////////////
// Common - contains domain model
///////////////////////////////////////////////////////////////////////////////////////////////////////////

lazy val munitVersion = "1.2.4"

lazy val core = (crossProject(JSPlatform, JVMPlatform) in file("common"))
  .settings(
    name         := "common",
    scalaVersion := scala3Version,
    organization := org,
    libraryDependencies ++= Seq(
      "io.circe"          %%% "circe-core"      % circeVersion,
      "io.circe"          %%% "circe-parser"    % circeVersion,
      "io.circe"          %%% "circe-generic"   % circeVersion,
      "com.lihaoyi"       %%% "fastparse"       % "3.1.1",
      "org.typelevel"     %%% "cats-effect"     % catsEffectVersion,
      "io.github.cquiroz" %%% "scala-java-time" % "2.6.0",
      "org.scalameta"     %%% "munit"           % munitVersion % Test,
      "org.typelevel"     %%% "munit-cats-effect" % "2.2.0"    % Test
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )
  .jvmSettings(
    // add here if necessary
  )
  .jsSettings(
    // Add JS-specific settings here
  )

///////////////////////////////////////////////////////////////////////////////////////////////////////////
// Frontend
///////////////////////////////////////////////////////////////////////////////////////////////////////////

lazy val tyrianVersion    = "0.14.0" // Latest available for Scala 3.6.4
lazy val circeVersion     = "0.14.15"
lazy val circeFs2Version  = "0.14.1"

lazy val app = (project in file("app"))
  .enablePlugins(ScalaJSPlugin)
  .settings(
    name         := "app",
    scalaVersion := scala3Version,
    organization := org,
    libraryDependencies ++= Seq(
      "io.indigoengine" %%% "tyrian-io"     % tyrianVersion,
      "io.circe"        %%% "circe-core"    % circeVersion,
      "io.circe"        %%% "circe-parser"  % circeVersion,
      "io.circe"        %%% "circe-generic" % circeVersion
    ),
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
    semanticdbEnabled := true,
    autoAPIMappings   := true
  )
  .dependsOn(core.js)

lazy val catsEffectVersion          = "3.7.0"
lazy val http4sVersion              = "0.23.33"
lazy val doobieVersion              = "1.0.0-RC12"
lazy val pureConfigVersion          = "0.17.10"
lazy val log4catsVersion            = "2.8.0"
lazy val tsecVersion                = "0.5.0"
lazy val scalaTestVersion           = "3.2.19"
lazy val scalaTestCatsEffectVersion = "1.8.0"
lazy val testContainerVersion       = "1.21.4"
lazy val logbackVersion             = "1.5.32"
lazy val slf4jVersion               = "2.0.17"
lazy val javaMailVersion            = "1.6.2" // Use Jakarta Mail 2.1.5 for Spring 6+ / javax.mail for Spring 5
lazy val stripeVersion              = "30.2.0"

ThisBuild / assemblyMergeStrategy := {
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case x =>
    val oldStrategy = (ThisBuild / assemblyMergeStrategy).value
    oldStrategy(x)
}

lazy val server = (project in file("server"))
  .settings(
    name         := "server",
    scalaVersion := scala3Version,
    organization := org,
    libraryDependencies ++= Seq(
      "org.typelevel"         %% "cats-effect"         % catsEffectVersion,
      "org.http4s"            %% "http4s-dsl"          % http4sVersion,
      "org.http4s"            %% "http4s-ember-server" % http4sVersion,
      "org.http4s"            %% "http4s-circe"        % http4sVersion,
      "io.circe"              %% "circe-generic"       % circeVersion,
      "io.circe"              %% "circe-fs2"           % circeFs2Version,
      "org.tpolecat"          %% "doobie-core"         % doobieVersion,
      "org.tpolecat"          %% "doobie-hikari"       % doobieVersion,
      "org.tpolecat"          %% "doobie-postgres"     % doobieVersion,
      "org.tpolecat"          %% "doobie-scalatest"    % doobieVersion    % Test,
      "com.github.pureconfig" %% "pureconfig-core"     % pureConfigVersion,
      "org.typelevel"         %% "log4cats-slf4j"      % log4catsVersion,
      "org.slf4j"              % "slf4j-simple"        % slf4jVersion,
      "io.github.jmcardon"    %% "tsec-http4s"         % tsecVersion,
      "com.sun.mail"           % "javax.mail"          % javaMailVersion,
      "com.stripe"             % "stripe-java"         % stripeVersion,
      "org.typelevel"         %% "log4cats-noop"       % log4catsVersion  % Test,
      "org.scalatest"         %% "scalatest"           % scalaTestVersion % Test,
      "org.typelevel"     %% "cats-effect-testing-scalatest" % scalaTestCatsEffectVersion % Test,
      "org.testcontainers" % "testcontainers"                % testContainerVersion       % Test,
      "org.testcontainers" % "postgresql"                    % testContainerVersion       % Test,
      "ch.qos.logback"     % "logback-classic"               % logbackVersion             % Test
    ),
    Compile / mainClass  := Some("com.crianonim.tables.Application"),
    assembly / mainClass := Some("com.crianonim.tables.Application")
  )
  .settings(
  )
  .dependsOn(core.jvm)
