package io.chrisdavenport.npmpackage


import sbt._
import _root_.io.circe.Json
import _root_.io.circe.syntax._
import java.nio.file.Files

object PackageFile {

  @deprecated("Use `writePackageJson` with `type` instead", "0.1.1")
  def writePackageJson(
    targetDir: File,
    name: String,
    version: String,
    description: String,
    repository: Option[String],
    author: String,
    license: Option[String],
    keywords: List[String],
    filename: String,
    npmDependencies: Seq[(String, String)],
    npmDevDependencies: Seq[(String, String)],
    npmResolutions: Map[String, String],
    additionalNpmConfig: Map[String, Json],
    enableBinary: Boolean,
    binaryArtifacts: Seq[(String, String)],
    fullClasspath: Seq[Attributed[File]],
    configuration: Configuration,
    streams: Keys.TaskStreams
  ): File = writePackageJson(
    targetDir,
    name,
    version,
    description,
    repository,
    author,
    license,
    keywords,
    filename,
    npmDependencies,
    npmDevDependencies,
    npmResolutions,
    additionalNpmConfig,
    enableBinary,
    binaryArtifacts,
    "commonjs",
    fullClasspath,
    configuration,
    streams
  )

  def writePackageJson(
    targetDir: File,
    name: String,
    version: String,
    description: String,
    repository: Option[String],
    author: String,
    license: Option[String],
    keywords: List[String],
    filename: String,
    npmDependencies: Seq[(String, String)],
    npmDevDependencies: Seq[(String, String)],
    npmResolutions: Map[String, String],
    additionalNpmConfig: Map[String, Json],
    enableBinary: Boolean,
    binaryArtifacts: Seq[(String, String)],
    `type`: String,
    fullClasspath: Seq[Attributed[File]],
    configuration: Configuration,
    streams: Keys.TaskStreams
  ): File = {
    val packageJsonFile = targetDir / "package.json"
    if (Files.exists(packageJsonFile.toPath())) Files.delete(packageJsonFile.toPath()) else ()
    write(
      streams.log,
      packageJsonFile,
      name,
      version,
      description,
      repository,
      author,
      license,
      keywords,
      filename,
      npmDependencies,
      npmDevDependencies,
      npmResolutions,
      additionalNpmConfig,
      enableBinary,
      binaryArtifacts,
      `type`,
      fullClasspath,
      configuration
    )
    streams.log.info(s"Wrote package.json File At - $packageJsonFile")
    packageJsonFile
  }

  private def write(
    log: Logger,
    targetFile: File,
    name: String,
    version: String,
    description: String,
    repository: Option[String],
    author: String,
    license: Option[String],
    keywords: List[String],
    filename: String,
    npmDependencies: Seq[(String, String)],
    npmDevDependencies: Seq[(String, String)],
    npmResolutions: Map[String, String],
    additionalNpmConfig: Map[String, Json],
    enableBinary: Boolean,
    binaryArtifacts: Seq[(String, String)],
    `type`: String,
    fullClasspath: Seq[Attributed[File]],
    currentConfiguration: Configuration,
  ): Unit = {
    val packageJson = 
      json(
        log,
        name,
        version,
        description,
        repository,
        author,
        license,
        keywords,
        filename,
        npmDependencies,
        npmDevDependencies,
        npmResolutions,
        additionalNpmConfig,
        enableBinary,
        binaryArtifacts,
        `type`,
        fullClasspath,
        currentConfiguration
      )

    log.debug(s"Writing 'package.json'\n${packageJson.spaces2}")
    IO.write(targetFile, packageJson.spaces2)
  }

  private def json(
    log: Logger,
    name: String,
    version: String, 
    description: String,
    repository: Option[String],
    author: String,
    license: Option[String],
    keywords: List[String],
    filename: String,
    npmDependencies: Seq[(String, String)],
    npmDevDependencies: Seq[(String, String)],
    npmResolutions: Map[String, String],
    additionalNpmConfig: Map[String, Json],
    enableBinary: Boolean,
    binaryArtifacts: Seq[(String, String)],
    `type`: String,
    fullClasspath: Seq[Attributed[File]],
    currentConfiguration: Configuration,
  ): Json = {
        val npmManifestDependencies = NpmDependencies.collectFromClasspath(fullClasspath)
    val dependencies =
      npmDependencies ++ (
        if (currentConfiguration == Compile) npmManifestDependencies.compileDependencies
        else npmManifestDependencies.testDependencies
      )
    val devDependencies =
      npmDevDependencies ++ (
        if (currentConfiguration == Compile) npmManifestDependencies.compileDevDependencies
        else npmManifestDependencies.testDevDependencies
      )

    val binary = 
      Json.obj(
        "bin" -> {
          if (enableBinary) Json.obj(
            binaryArtifacts.map{ case (k, v) => k -> v.asJson}:_*
          ) else Json.Null
        }
      )
    
    val packageJson = 
      Json.obj(additionalNpmConfig.toSeq:_*).deepMerge(
        Json.obj(
          "name" -> name.asJson,
          "description" -> description.asJson,
          "version" -> version.asJson,
          "type" -> `type`.asJson,
          "repository" -> repository.map(repo => Json.obj(
            "type" -> "git".asJson,
            "url" -> repo.asJson,
          )).asJson,
          "author" -> author.asJson,
          "license" -> license.asJson,
          "main" -> filename.asJson,
          "dependencies" -> Json.obj(
            resolveDependencies(dependencies, npmResolutions, log).map{ case (a, a2) => (a, a2.asJson)}:_*
          ),
          "devDependencies" -> Json.obj(
            resolveDependencies(devDependencies, npmResolutions, log).map{ case (a, a2) => (a, a2.asJson)}:_*
          ),
          "keywords" -> keywords.asJson,
        ).deepMerge(binary).dropNullValues
      )
    packageJson
  }


  /**
    * Resolves multiple occurrences of a dependency to a same package.
    *
    *  - If all the occurrences refer to the same version, pick this one ;
    *  - If they refer to different versions, pick the one defined in `resolutions` (or fail
    *    if there is no such resolution).
    *
    * @return The resolved dependencies
    * @param dependencies The dependencies to resolve
    * @param resolutions The resolutions to use in case of conflict (they will be ignored if there are no conflicts)
    * @param log Logger
    */
  private def resolveDependencies(
    dependencies: Seq[(String, String)],
    resolutions: Map[String, String],
    log: Logger
  ): List[(String, String)] ={
    val resolvedDependencies =
      dependencies
        .groupBy { case (name, version) => name }
        .mapValues(_.map(_._2).distinct)
        .foldRight(List.empty[(String, String)]) { case ((name, versions), result) =>
          val resolvedDependency =
            versions match {
              case Seq(single) =>
                name -> single
              case _ =>
                val resolution = resolutions.get(name) match {
                  case Some(v) => v
                  case None => versions.mkString(" ")
                }
                name -> resolution
            }
          resolvedDependency :: result
        }

    // Add a warning in case a resolution was defined but not used because the corresponding
    // dependency was not in conflict.
    val unusedResolutions =
      resolutions.filter { case (name, resolution) =>
        resolvedDependencies.exists { case (n, v) => n == name && v != resolution }
      }
    if (unusedResolutions.nonEmpty) {
      log.warn(s"Unused resolutions: $unusedResolutions")
    }

    log.debug(s"Resolved the following dependencies: $resolvedDependencies")

    resolvedDependencies
  }
}
