package io.chrisdavenport.npmpackage

import java.io.File
import java.nio.file.Files
import sbt._
import java.nio.charset.StandardCharsets

object NpmConfig {
  // @myscope:registry=https://mycustomregistry.example.org/:_authToken=\\${NPM_TOKEN}
  private val defaultRegistry = "//registry.npmjs.org/"

  def writeNpmrc(
    targetDir: File,
    scope: Option[String],
    customRegistry: Option[String],
    environmentVariableForAuth: String,
    log: Logger,
  ): File = {
    val targetFile = targetDir / ".npmrc"

    if (Files.exists(targetDir.toPath())) ()
    else Files.createDirectories(targetDir.toPath())
    Files.deleteIfExists(targetFile.toPath())

    val output = fileContent(scope, customRegistry, environmentVariableForAuth)

    Files.write(targetFile.toPath(), output.getBytes(StandardCharsets.UTF_8))
    
    log.info(s"Wrote $targetFile with content: $output")

    targetFile
  }

  def fileContent(
    scope: Option[String],
    customRegistry: Option[String],
    environmentVariableForAuth: String
  ): String = {
    val scopeComponent = scope.fold("")(s => s"@$s:")
    val registryComponent = customRegistry.fold(defaultRegistry)(reg => s"registry=$reg")
    val authComponent = ":_authToken=${" ++ environmentVariableForAuth ++ "}"
    
    scopeComponent ++ registryComponent ++ authComponent
  }

}