val npmPackageVersion = sys.props.getOrElse(
  "plugin.version",
  sys.error("'plugin.version' environment variable is not set")
)

addSbtPlugin(
  "io.chrisdavenport" % "sbt-npm-package" % npmPackageVersion changing ()
)
