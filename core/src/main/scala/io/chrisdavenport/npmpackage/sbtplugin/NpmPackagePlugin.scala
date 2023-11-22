package io.chrisdavenport.npmpackage
package sbtplugin

import _root_.io.circe.Json
import cats.syntax.all._
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import org.scalajs.sbtplugin.Stage
import org.scalajs.sbtplugin.Stage.FastOpt
import org.scalajs.sbtplugin.Stage.FullOpt
import sbt.Keys._
import sbt._

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import scala.collection.JavaConverters._
// import scalajsbundler.sbtplugin.ScalaJSBundlerPlugin.autoImport._

object NpmPackagePlugin extends AutoPlugin {

  val npmPackageDirectory = "npm-package"

  override def trigger = noTrigger

  override def requires = org.scalajs.sbtplugin.ScalaJSPlugin &&
    plugins.JvmPlugin

  object autoImport {
    lazy val npmPackageName = settingKey[String]("Name to use for npm package")
    lazy val npmPackageVersion =
      settingKey[String](
        "Version to use for npm package. Must be parseable by node-semver."
      )
    lazy val npmPackageRepository =
      settingKey[Option[String]]("Repository Location for npm package")
    lazy val npmPackageDescription =
      settingKey[String]("Description of this npm package")
    lazy val npmPackageAuthor = settingKey[String]("Author of this npm package")
    lazy val npmPackageLicense =
      settingKey[Option[String]]("License for this npm package")

    lazy val npmPackageREADME =
      settingKey[Option[File]]("README file to use for this npm package")

    /** List of the NPM packages (name and version) your application depends on.
      * You can use [semver](https://docs.npmjs.com/misc/semver) versions:
      *
      * {{{
      *   npmPackageDependencies in Compile += "uuid" -> "~3.0.0"
      * }}}
      *
      * Note that this key must be scoped by a `Configuration` (either `Compile`
      * or `Test`).
      *
      * @group settings
      */
    val npmPackageDependencies: SettingKey[Seq[(String, String)]] =
      settingKey[Seq[(String, String)]](
        "NPM dependencies (libraries that your program uses)"
      )

    /** @group settings */
    val npmPackageDevDependencies: SettingKey[Seq[(String, String)]] =
      settingKey[Seq[(String, String)]](
        "NPM dev dependencies (libraries that the build uses)"
      )

    /** Map of NPM packages (name -> version) to use in case transitive NPM
      * dependencies refer to a same package but with different version numbers.
      * In such a case, this setting defines which version should be used for
      * the conflicting package. Example:
      *
      * {{{
      *   npmPackageResolutions in Compile := Map("react" -> "15.4.1")
      * }}}
      *
      * If several Scala.js projects depend on different versions of `react`,
      * the version `15.4.1` will be picked. But if all the projects depend on
      * the same version of `react`, the version given in `npmResolutions` will
      * be ignored.
      *
      * If different versions of the packages are referred but the package is
      * NOT configured in `npmResolutions`, a version conflict resolution is
      * delegated to npm/yarn. This behavior may reduce a need to configure
      * `npmResolutions` explicitly. E.g. "14.4.2" can be automatically-picked
      * for ">=14.0.0 14.4.2 ^14.4.1".
      *
      * Note that this key must be scoped by a `Configuration` (either `Compile`
      * or `Test`).
      *
      * @group settings
      */
    val npmPackageResolutions: SettingKey[Map[String, String]] =
      settingKey[Map[String, String]](
        "NPM dependencies resolutions in case of conflict"
      )

    /** List of the additional configuration options to include in the generated
      * 'package.json'. Note that package dependencies are automatically
      * generated from `npmDependencies` and `npmDevDependencies` and should
      * '''not''' be specified in this setting.
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
      * Note that this key must be scoped by a `Configuration` (either `Compile`
      * or `Test`).
      *
      * @group settings
      */
    val npmPackageAdditionalNpmConfig: SettingKey[Map[String, Json]] =
      settingKey[Map[String, Json]](
        "Additional option to include in the generated 'package.json'"
      )

    /** Whether to use [[https://yarnpkg.com/ Yarn]] to fetch dependencies
      * instead of `npm`. Yarn has a caching mechanism that makes the process
      * faster.
      *
      * If set to `true`, it requires Yarn 0.22.0+ to be available on the host
      * platform.
      *
      * Defaults to `false`.
      *
      * @group settings
      */
    val npmPackageUseYarn: SettingKey[Boolean] =
      settingKey[Boolean]("Whether to use yarn for updates")

    /** Additional arguments for yarn
      *
      * Defaults to an empty list.
      *
      * @group settings
      */
    val npmPackageYarnExtraArgs = SettingKey[Seq[String]](
      "yarnExtraArgs",
      "Custom arguments for yarn"
    )

    /** Additional arguments for npm
      *
      * Defaults to an empty list.
      *
      * @group settings
      */
    val npmPackageNpmExtraArgs = SettingKey[Seq[String]](
      "npmExtraArgs",
      "Custom arguments for npm"
    )

    val npmPackageOutputFilename: SettingKey[String] =
      settingKey[String]("Output JS File name - i.e. main.js")
    val npmPackageOutputDirectory: SettingKey[File] =
      settingKey[File]("Output Directory for Npm package outputs")

    val npmPackageStage: SettingKey[Stage] =
      settingKey("Stage Action to Use for npm package")

    val npmPackageKeywords: SettingKey[Seq[String]] =
      settingKey("Keywords to place in the npm package")

    val npmPackageNpmrcRegistry: SettingKey[Option[String]] =
      settingKey("npm registry to publish to, defaults to registry.npmjs.org")

    val npmPackageScope: SettingKey[Option[String]] =
      settingKey(
        "Scope to use if you want a limited scope in your npm repository"
      )

    val npmPackageNpmrcAuthEnvironmentalVariable: SettingKey[String] =
      settingKey("Environmental Variable that holds auth information")

    val npmPackageBinaryEnable: SettingKey[Boolean] =
      settingKey("Whether to make this package a binary - defaults to false")

    val npmPackageBinaries: SettingKey[Seq[(String, String)]] =
      settingKey("The name of the binary executable - defaults to project name")

    val npmPackageType: SettingKey[String] =
      settingKey(
        "The type of the package - defaults to 'commonjs' for ModuleKind.CommonJSModule or ModuleKind.NoModule, and 'module' for ModuleKind.ESModule"
      )

    lazy val npmPackageExtraFiles: SettingKey[Seq[File]] =
      settingKey[Seq[File]](
        "Extra files to copy to the Npm package output directory"
      )

    val npmPackage =
      taskKey[Unit]("Creates all files and direcories for the npm package")

    val npmPackageOutputJS = taskKey[File]("Write JS to output directory")
    val npmPackagePackageJson =
      taskKey[File]("Write Npm Package File to Directory")
    val npmPackageWriteREADME = taskKey[File]("Write README to the npm package")
    val npmPackageWriteExtraFiles =
      taskKey[Unit]("Copy extra files to the NPM package output directory")
    val npmPackageInstall =
      taskKey[File]("Install Deps for npm/yarn for the npm package")
    val npmPackagePublish =
      taskKey[File]("Publish for npm/yarn for the npm package")
    val npmPackageNpmrc = taskKey[File]("Write Npmrc File")

    val npmPackageNpmrcAdditionalScopes: SettingKey[Map[String, String]] =
      settingKey[Map[String, String]](
        "Additional Scopes to Set Resolution for in the .npmrc file"
      )
    val npmPackageNpmrcKeySettings: SettingKey[Seq[(String, String, String)]] =
      settingKey[Seq[(String, String, String)]](
        "Key Value Pairs to Set for specific paths"
      )
  }
  import autoImport._

  override def globalSettings: Seq[Setting[_]] = Seq(
  )

  override def projectSettings: Seq[Setting[_]] = Seq(
    npmPackageName := {
      val s = npmPackageScope.value.map(_ + "/")
      val n = name.value
      s"${s.getOrElse("")}$n"
    },
    npmPackageAdditionalNpmConfig := {
      npmPackageNpmrcRegistry.value.fold(Map[String, Json]())(s =>
        Map("publishConfig" -> Json.obj("registry" -> Json.fromString(s)))
      )
    },
    npmPackageBinaries := Seq(
      (npmPackageName.value, npmPackageOutputFilename.value)
    ),
    npmPackageVersion := version.value
  ) ++ inConfig(Compile)(perConfigSettings)

  override def buildSettings: Seq[Setting[_]] = Seq(
    npmPackageDescription := "NPM Package Created By sbt-npm-package",
    npmPackageAuthor := "Unknown",
    npmPackageLicense := licenses.value.map(_._1).headOption,
    npmPackageRepository := remoteIdentifier,
    npmPackageDependencies := Seq(),
    npmPackageDevDependencies := Seq(),
    npmPackageResolutions := Map(),
    npmPackageOutputFilename := "main.js",
    npmPackageStage := Stage.FastOpt,
    npmPackageUseYarn := false,
    npmPackageNpmExtraArgs := Seq.empty,
    npmPackageYarnExtraArgs := Seq.empty,
    npmPackageKeywords := Seq.empty,
    npmPackageNpmrcRegistry := None,
    npmPackageScope := None,
    npmPackageNpmrcAuthEnvironmentalVariable := "NPM_TOKEN",
    npmPackageBinaryEnable := false,
    npmPackageExtraFiles := Seq.empty,
    npmPackageREADME := {
      val path = file("README.md")
      if (java.nio.file.Files.exists(path.toPath())) Option(path)
      else Option.empty[File]
    },
    npmPackageNpmrcAdditionalScopes := Map.empty,
    npmPackageNpmrcKeySettings := Seq(
      (
        "//registry.npmjs.org/",
        "_authToken",
        "${" ++ npmPackageNpmrcAuthEnvironmentalVariable.value ++ "}"
      )
    )
  )

  lazy val perConfigSettings = Def.settings(
    npmPackageOutputDirectory := crossTarget.value / npmPackageDirectory,
    npmPackageType := {
      if (scalaJSLinkerConfig.value.moduleKind == ModuleKind.ESModule) "module"
      else "commonjs"
    },
    scalaJSLinkerConfig := {
      val c = scalaJSLinkerConfig.value
      val hashbang =
        if (npmPackageBinaryEnable.value)
          "#!/usr/bin/env node\n"
        else
          ""
      c.withModuleKind(ModuleKind.CommonJSModule)
        .withJSHeader(s"${hashbang}${c.jsHeader}")
    },
    npmPackagePackageJson := {
      PackageFile.writePackageJson(
        npmPackageOutputDirectory.value,
        npmPackageName.value,
        npmPackageVersion.value,
        npmPackageDescription.value,
        npmPackageRepository.value,
        npmPackageAuthor.value,
        npmPackageLicense.value,
        npmPackageKeywords.value.toList,
        npmPackageOutputFilename.value,
        npmPackageDependencies.value,
        npmPackageDevDependencies.value,
        npmPackageResolutions.value,
        npmPackageAdditionalNpmConfig.value,
        npmPackageBinaryEnable.value,
        npmPackageBinaries.value,
        npmPackageType.value,
        dependencyClasspath.value,
        configuration.value,
        streams.value
      )
    },
    npmPackageOutputJS := Def.taskDyn {
      val outputTask = npmPackageStage.value match {
        case FastOpt => (configuration / fastOptJS).taskValue
        case FullOpt => (configuration / fullOptJS).taskValue
      }
      Def.task {
        val output = outputTask.value.data
        val from = output.toPath()
        val fromSourceMap = from.resolveSibling(from.getFileName() + ".map")
        val targetDir = npmPackageOutputDirectory.value
        val target = targetDir / npmPackageOutputFilename.value
        val targetPath = target.toPath
        val targetSourceMapPath =
          targetPath.resolveSibling(targetPath.getFileName() + ".map")

        if (Files.exists(targetDir.toPath())) ()
        else Files.createDirectories(targetDir.toPath())

        val lines = Files.readAllLines(from).asScala.map { l =>
          if (l.startsWith("//# sourceMappingURL="))
            s"//# sourceMappingURL=${targetSourceMapPath.getFileName()}\n"
          else l
        }
        Files.write(targetPath, lines.asJava)
        streams.value.log.info(s"Wrote $from to $targetPath")
        if (fromSourceMap.toFile().exists()) {
          Files.copy(
            fromSourceMap,
            targetSourceMapPath,
            StandardCopyOption.REPLACE_EXISTING
          )
        } else ()
        target
      }
    }.value,
    npmPackageWriteREADME := {
      val from = npmPackageREADME.value
      val targetDir = npmPackageOutputDirectory.value
      val target = targetDir / "README.md"
      val targetPath = target.toPath
      val log = streams.value.log
      if (Files.exists(targetDir.toPath())) ()
      else Files.createDirectories(targetDir.toPath())
      from match {
        case Some(fromF) =>
          val from = fromF.toPath()
          Files.copy(from, targetPath, StandardCopyOption.REPLACE_EXISTING)
          log.info(s"Wrote $from to $targetPath")
          target
        case None =>
          log.warn(s"Source File For README missing $from")
          target
      }
    },
    npmPackageInstall := {
      val output = npmPackageOutputDirectory.value
      if (Files.exists(output.toPath())) ()
      else Files.createDirectories(output.toPath())
      val _ = npmPackage.value
      ExternalCommand.install(
        baseDirectory.value,
        output,
        npmPackageUseYarn.value,
        streams.value.log,
        npmPackageNpmExtraArgs.value,
        npmPackageYarnExtraArgs.value
      )
      output
    },
    npmPackagePublish := {
      val _ = npmPackageInstall.value
      val output = npmPackageOutputDirectory.value
      ExternalCommand.publish(
        baseDirectory.value,
        output,
        npmPackageUseYarn.value,
        streams.value.log,
        npmPackageNpmExtraArgs.value,
        npmPackageYarnExtraArgs.value
      )
      output
    },
    npmPackageNpmrc := {
      val ourScope = npmPackageScope.value
      val ourReg = npmPackageNpmrcRegistry.value
      val ourScopes =
        (ourScope, ourReg).tupled.fold(List.empty[(String, String)])(_ :: Nil)
      val registries = npmPackageNpmrcAdditionalScopes.value.toList ++ ourScopes
      val keys = npmPackageNpmrcKeySettings.value

      NpmConfig.writeNpmrc(
        npmPackageOutputDirectory.value,
        registries,
        keys.toList,
        streams.value.log
      )
    },
    npmPackageWriteExtraFiles := {
      val targetDir = npmPackageOutputDirectory.value
      val log = streams.value.log

      if (Files.exists(targetDir.toPath())) ()
      else Files.createDirectories(targetDir.toPath())

      npmPackageExtraFiles.value.foreach { from =>
        val targetPath = targetDir / from.name
        IO.copy(Seq(from -> targetPath), CopyOptions().withOverwrite(true))
        log.info(s"Wrote $from to $targetPath")
      }
    },
    npmPackage := {
      val b = npmPackagePackageJson.value
      val a = npmPackageOutputJS.value
      val c = npmPackageWriteREADME.value
      val d = npmPackageWriteExtraFiles.value
      void(a, b, c, d)
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
