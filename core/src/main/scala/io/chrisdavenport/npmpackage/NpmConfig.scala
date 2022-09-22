package io.chrisdavenport.npmpackage

import java.io.File
import java.nio.file.Files
import sbt._
import java.nio.charset.StandardCharsets
import org.http4s._
import org.http4s.syntax.all._
import sbtplugin.NpmPackagePlugin.autoImport.AuthType

object NpmConfig {
  // @myscope:registry=https://mycustomregistry.example.org/
  // //mycustomregistry.example.org/:_authToken=\\${NPM_TOKEN}
  private val defaultRegistry = uri"https://registry.npmjs.org/"

  def writeNpmrc(
    targetDir: File,
    scope: Option[String],
    customRegistry: Option[String],
    environmentVariableForAuth: String,
    log: Logger,
    authType: AuthType,
  ): File = {
    val targetFile = targetDir / ".npmrc"

    if (Files.exists(targetDir.toPath())) ()
    else Files.createDirectories(targetDir.toPath())
    Files.deleteIfExists(targetFile.toPath())

    val output = fileContent(scope, customRegistry.map(Uri.unsafeFromString), environmentVariableForAuth, authType)

    Files.write(targetFile.toPath(), output.getBytes(StandardCharsets.UTF_8))
    
    log.info(s"Wrote $targetFile with content: $output")

    targetFile
  }

  def fileContent(
    scope: Option[String],
    customRegistry: Option[Uri],
    environmentVariableForAuth: String,
    authType: AuthType,
  ): String = {
    val scopeRegistry = scope.map(s => s"@$s:registry=${customRegistry.getOrElse(defaultRegistry)}").getOrElse("")
    val registryForAuth = customRegistry.getOrElse(defaultRegistry).copy(scheme = None).toString()

    s"""$scopeRegistry
       |$registryForAuth:$authType=$${$environmentVariableForAuth}
       |""".stripMargin
  }

}
