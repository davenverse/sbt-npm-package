package io.chrisdavenport.npmpackage
package sbtplugin


import sbt._
import Keys._
import _root_.io.circe.Json
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import org.scalajs.sbtplugin.Stage
import org.scalajs.sbtplugin.Stage.FastOpt
import org.scalajs.sbtplugin.Stage.FullOpt
import java.nio.file.Files
// import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport._

object NpmPackagePlugin extends AutoPlugin {

  val npmPackageDirectory = "npm-package"

  override def trigger = noTrigger

  override def requires = org.scalajs.sbtplugin.ScalaJSPlugin  &&
    plugins.JvmPlugin

  object autoImport {
    lazy val npmPackageName = settingKey[String]("Name to use for npm package")
    lazy val npmPackageVersion = settingKey[String]("Version to use for npm package")
    lazy val npmPackageRepository = settingKey[Option[String]]("Repository Location for npm package")
    lazy val npmPackageDescription = settingKey[String]("Description of this npm package")
    lazy val npmPackageAuthor = settingKey[String]("Author of this npm package")
    lazy val npmPackageLicense = settingKey[Option[String]]("License for this npm package")

    lazy val npmPackageREADME = settingKey[Option[File]]("README file to use for this npm package")

    /**
      * List of the NPM packages (name and version) your application depends on.
      * You can use [semver](https://docs.npmjs.com/misc/semver) versions:
      *
      * {{{
      *   npmPackageDependencies in Compile += "uuid" -> "~3.0.0"
      * }}}
      *
      * Note that this key must be scoped by a `Configuration` (either `Compile` or `Test`).
      *
      * @group settings
      */
    val npmPackageDependencies: SettingKey[Seq[(String, String)]] =
      settingKey[Seq[(String, String)]]("NPM dependencies (libraries that your program uses)")

    /** @group settings */
    val npmPackageDevDependencies: SettingKey[Seq[(String, String)]] =
      settingKey[Seq[(String, String)]]("NPM dev dependencies (libraries that the build uses)")

    /**
      * Map of NPM packages (name -> version) to use in case transitive NPM dependencies
      * refer to a same package but with different version numbers. In such a
      * case, this setting defines which version should be used for the conflicting
      * package. Example:
      *
      * {{{
      *   npmPackageResolutions in Compile := Map("react" -> "15.4.1")
      * }}}
      *
      * If several Scala.js projects depend on different versions of `react`, the version `15.4.1`
      * will be picked. But if all the projects depend on the same version of `react`, the version
      * given in `npmResolutions` will be ignored.
      *
      * If different versions of the packages are referred but the package is NOT configured in `npmResolutions`,
      * a version conflict resolution is delegated to npm/yarn. This behavior may reduce a need to configure
      * `npmResolutions` explicitly. E.g. "14.4.2" can be automatically-picked for ">=14.0.0 14.4.2 ^14.4.1".
      *
      * Note that this key must be scoped by a `Configuration` (either `Compile` or `Test`).
      *
      * @group settings
      */
    val npmPackageResolutions: SettingKey[Map[String, String]] =
      settingKey[Map[String, String]]("NPM dependencies resolutions in case of conflict")

    /**
      * List of the additional configuration options to include in the generated 'package.json'.
      * Note that package dependencies are automatically generated from `npmDependencies` and
      * `npmDevDependencies` and should '''not''' be specified in this setting.
      *
      * {{{
      *   import scalajsbundler.util.JSON._
      *   npmPackageAdditionalNpmConfig in Compile := Map(
      *     "other"       -> obj(
      *       "value0" -> bool(true),
      *       "value1" -> obj(
      *         "foo" -> str("bar")
      *       )
      *     )
      *   )
      * }}}
      *
      * Note that this key must be scoped by a `Configuration` (either `Compile` or `Test`).
      *
      * @group settings
      */
    val npmPackageAdditionalNpmConfig: SettingKey[Map[String, Json]] =
      settingKey[Map[String, Json]]("Additional option to include in the generated 'package.json'")

    val npmPackageOutputDirectory: SettingKey[File] = 
      settingKey[File]("Output Directory for Npm package outputs")

    val npmPackageStage: SettingKey[Stage] = 
      settingKey("Stage Action to Use for npm package")

    val npmPackage = taskKey[Unit]("Creates all files and direcories for the npm package")

    val npmPackageOutputJS = taskKey[File]("Write JS to output directory")
    val npmPackagePackageJson = taskKey[File]("Write Npm Package File to Directory")
    val npmPackageWriteREADME = taskKey[File]("Write README to the npm package")

  }
  import autoImport._

  override def globalSettings: Seq[Setting[_]] = Seq(
  )

  override def projectSettings: Seq[Setting[_]] = Seq(
    npmPackageName := name.value,
    npmPackageVersion := version.value,
    npmPackageDescription := "NPM Package Created By sbt-npm-package",
    npmPackageAuthor := "Unknown",
    npmPackageLicense := licenses.value.map(_._1).headOption,
    npmPackageRepository := remoteIdentifier,
    npmPackageDependencies := Seq(),
    npmPackageDevDependencies := Seq(),
    npmPackageResolutions := Map(),
    npmPackageAdditionalNpmConfig := Map(),
    npmPackageOutputDirectory := crossTarget.value / npmPackageDirectory,
    npmPackageStage := Stage.FastOpt,
    npmPackageREADME := {
      val path = file("README.md")
      if (java.nio.file.Files.exists(path.toPath())) Option(path)
      else Option.empty[File]
    },
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
  ) ++
    inConfig(Compile)(perConfigSettings) ++
    inConfig(Test)(perConfigSettings)

  override def buildSettings: Seq[Setting[_]] = Seq(
  )

  lazy val perConfigSettings = 
    Def.settings(
      npmPackagePackageJson := {
        PackageFile.writePackageJson(
          npmPackageOutputDirectory.value,
          npmPackageName.value,
          npmPackageVersion.value,
          npmPackageDescription.value,
          npmPackageRepository.value,
          npmPackageAuthor.value,
          npmPackageLicense.value,
          npmPackageDependencies.value,
          npmPackageDevDependencies.value,
          npmPackageResolutions.value,
          npmPackageAdditionalNpmConfig.value,
          dependencyClasspath.value,
          configuration.value,
          streams.value
        )
      },
      npmPackageOutputJS := Def.taskDyn{
        val outputTask = npmPackageStage.value match {
          case FastOpt => (configuration / fastOptJS).taskValue
          case FullOpt => (configuration / fullOptJS).taskValue
        }
        Def.task{
          val output = outputTask.value.data
          val from = output.toPath()
          val targetDir = npmPackageOutputDirectory.value
          val target = (targetDir / "main.js")
          val targetPath = target.toPath

          if (Files.exists(targetPath)) Files.delete(targetPath) else ()
          Files.copy(from, targetPath)
          streams.value.log.info(s"Wrote $from to $targetPath")
          target
        }
      }.value,

      npmPackageWriteREADME := {
        val from = npmPackageREADME.value.map(_.toPath())
        val targetDir = npmPackageOutputDirectory.value
        val target = (targetDir / "README.md")
        val targetPath = target.toPath
        val log = streams.value.log
        from match {
          case Some(from) => 
            if (Files.exists(targetPath)) Files.delete(targetPath) else ()
            Files.copy(from, targetPath)
            log.info(s"Wrote $from to $targetPath")
            target
          case None =>  
            log.warn(s"Source File For README missing $from")

            val readmeText = s"""# ${npmPackageName.value}
            |
            |${npmPackageDescription}""".stripMargin

            Files.write(targetPath, readmeText.getBytes())
            log.info(s"Wrote custom file for readme to $targetPath")
          
            target
        }
      },

      npmPackage := {
        val a = npmPackageOutputJS.value
        val b = npmPackagePackageJson.value
        val c = npmPackageWriteREADME.value
        void(a,b,c)
      }

    )


  private val remoteIdentifier: Option[String] = {
    import scala.sys.process._
    try {
      val remote = List("git", "ls-remote", "--get-url", "origin").!!.trim()
      if (remote.isEmpty()) None
      else Some(remote)
    } catch {
      case scala.util.control.NonFatal(_) => None
    }
  } 

  private def void(a: Any*): Unit = (a, ())._2

}