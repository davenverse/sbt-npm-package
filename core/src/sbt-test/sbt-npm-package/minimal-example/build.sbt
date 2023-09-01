enablePlugins(NpmPackagePlugin)

ThisBuild / crossScalaVersions := Seq("2.12.18", "2.13.6")

name := "minimal-example"

npmPackageAuthor := "Christopher Davenport"
npmPackageDescription := "Does Something Eventually"

libraryDependencies += "org.http4s" %%% "http4s-ember-server" % "1.0.0-M24"

licenses := Seq("MIT" -> url("http://opensource.org/licenses/MIT"))
