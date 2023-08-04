val scalaJSVersion = sys.props.getOrElse(
  "scalajs.version",
  sys.error("'scalajs.version' environment variable is not defined")
)
val npmPackageVersion = sys.props.getOrElse(
  "plugin.version",
  sys.error("'plugin.version' environment variable is not set")
)

addSbtPlugin("org.scala-js" % "sbt-scalajs" % scalaJSVersion changing ())

addSbtPlugin(
  "io.chrisdavenport" % "sbt-npm-package" % npmPackageVersion changing ()
)
