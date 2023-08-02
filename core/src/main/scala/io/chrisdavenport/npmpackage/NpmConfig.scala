package io.chrisdavenport.npmpackage

import sbt._

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

object NpmConfig {

  def writeNpmrc(
      targetDir: File,
      registries: List[(String, String)], // Scope and registry targets
      credentialConfigs: List[
        (String, String, String)
      ], // Path followed by key value pairs to be set.
      log: Logger
  ): File = {
    val targetFile = targetDir / ".npmrc"

    if (Files.exists(targetDir.toPath())) ()
    else Files.createDirectories(targetDir.toPath())
    Files.deleteIfExists(targetFile.toPath())

    val output = fileContent(registries, credentialConfigs)

    Files.write(targetFile.toPath(), output.getBytes(StandardCharsets.UTF_8))

    log.info(s"Wrote $targetFile with content: $output")

    targetFile
  }

  // @myscope:registry=https://mycustomregistry.example.org/:_authToken=\\${NPM_TOKEN}
  // So this file is constructed by a set of registrys defined for scopes.
  // And for you to be able to set custom kv pairs for any of the defined registry urls.

  //
  // registries
  // This is necessary for any custom scope dependency resolutions.
  // scope -> registry *
  // keys
  // registry -> key -> value *

  def customRegistry(scope: String, registry: String): String =
    s"$scope:registry=$registry"

  def credentials(path: String, key: String, value: String): String =
    s"$path:$key=$value"

  def fileContent(
      registries: List[(String, String)], // Scope and registry targets
      credentialConfigs: List[
        (String, String, String)
      ] // Path followed by key value pairs to be set.
  ): String = {
    val registryString = registries
      .map { case (scope, registry) => customRegistry(scope, registry) }
      .mkString("\n")
    val credentialString = credentialConfigs
      .map { case (path, key, value) =>
        credentials(path, key, value)
      }
      .mkString("\n")

    registryString ++ "\n" ++ credentialString
  }

}
