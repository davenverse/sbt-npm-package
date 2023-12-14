ThisBuild / tlBaseVersion := "0.2" // your current series x.y

ThisBuild / organization := "io.chrisdavenport"
ThisBuild / organizationName := "Christopher Davenport"
ThisBuild / licenses := Seq(License.MIT)
ThisBuild / developers := List(
  tlGitHubDev("christopherdavenport", "Christopher Davenport")
)
ThisBuild / tlCiReleaseBranches := Seq("main")
ThisBuild / tlSonatypeUseLegacyHost := true

ThisBuild / crossScalaVersions := Seq("2.12.18")
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / tlCiMimaBinaryIssueCheck := true
ThisBuild / tlCiScalafmtCheck := true
ThisBuild / tlCiHeaderCheck := false
ThisBuild / tlCiDocCheck := false
ThisBuild / tlCiScalafixCheck := true
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

val circeV = "0.14.6"

// Projects
lazy val `sbt-npm-package` = tlCrossRootProject
  .aggregate(core, gha)

lazy val core = project
  .in(file("core"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-npm-package",
    scriptedBufferLog := false,
    scriptedLaunchOpts ++= Seq(
      "-Dplugin.version=" + version.value,
      "-Dsbt.execute.extrachecks=true" // Avoid any deadlocks.
    ),
    test := {
      (Test / test).value
      scripted.toTask("").value
    },
    addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.14.0"),
    libraryDependencies ++= Seq(
      "io.circe" %% "circe-core" % circeV,
      "io.circe" %% "circe-parser" % circeV
    )
  )

lazy val gha = project
  .in(file("github-actions"))
  .enablePlugins(SbtPlugin)
  .dependsOn(core)
  .settings(
    name := "sbt-npm-package-github-actions",
    scriptedBufferLog := false,
    scriptedLaunchOpts ++= Seq(
      "-Dplugin.version=" + version.value,
      s"-Dscalajs.version=$scalaJSVersion",
      "-Dsbt.execute.extrachecks=true" // Avoid any deadlocks.
    ),
    addSbtPlugin("org.typelevel" % "sbt-typelevel-github-actions" % "0.6.4"),
    test := {
      (Test / test).value
      scripted.toTask("").value
    }
  )
