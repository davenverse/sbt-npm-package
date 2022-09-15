
val scalaJSVersion = sys.env.getOrElse("SCALAJS_VERSION", "1.8.0")

ThisBuild / crossScalaVersions := Seq("2.12.14")
ThisBuild / versionScheme := Some("early-semver")

ThisBuild / githubWorkflowBuild := Seq(
  WorkflowStep.Sbt(List("test"))
)

val catsV = "2.6.1"
val catsEffectV = "3.1.1"
val fs2V = "3.0.6"
val circeV = "0.14.1"


// Projects
lazy val `sbt-npm-package` = project.in(file("."))
  .disablePlugins(MimaPlugin)
  .enablePlugins(NoPublishPlugin)
  .aggregate(core, gha)

lazy val core = project.in(file("core"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-npm-package",
    scriptedBufferLog := false,
    scriptedLaunchOpts ++= Seq(
    "-Dplugin.version=" + version.value,
    s"-Dscalajs.version=$scalaJSVersion",
    "-Dsbt.execute.extrachecks=true" // Avoid any deadlocks.
    ),
    test := {
      (Test / test).value
      scripted.toTask("").value
    },
    addSbtPlugin("org.scala-js" % "sbt-scalajs" % scalaJSVersion),
    libraryDependencies ++= Seq(
      "io.circe"                    %% "circe-core"                 % circeV,
      "io.circe"                    %% "circe-parser"               % circeV,
    )
  )

lazy val gha = project.in(file("github-actions"))
  .enablePlugins(SbtPlugin)
  .settings(
    name := "sbt-npm-package-github-actions",
    scriptedBufferLog := false,
    scriptedLaunchOpts ++= Seq(
    "-Dplugin.version=" + version.value,
    s"-Dscalajs.version=$scalaJSVersion",
    "-Dsbt.execute.extrachecks=true" // Avoid any deadlocks.
    ),

    addSbtPlugin("org.typelevel" % "sbt-typelevel-github-actions" % "0.4.12"),

    test := {
      (Test / test).value
      scripted.toTask("").value
    },
  )