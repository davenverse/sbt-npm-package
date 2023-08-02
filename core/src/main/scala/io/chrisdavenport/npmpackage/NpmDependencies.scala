package io.chrisdavenport.npmpackage

import _root_.io.circe._
import _root_.io.circe.syntax._
import cats.syntax.all._
import sbt._

import java.io.BufferedInputStream
import java.io.FileInputStream
import java.util.zip.ZipInputStream

import NpmDependencies.Dependencies

/** NPM dependencies, for each configuration. This information can not be
  * included in the pom.xml, so we serialize for each Scala.js artifact into an
  * additional file in the artifact .jar.
  *
  * We need to read this information from other deps to build a correct deps for
  * our package
  */
case class NpmDependencies(
    compileDependencies: Dependencies,
    testDependencies: Dependencies,
    compileDevDependencies: Dependencies,
    testDevDependencies: Dependencies
) {

  /** Merge operator */
  def ++(that: NpmDependencies): NpmDependencies =
    NpmDependencies(
      compileDependencies ++ that.compileDependencies,
      testDependencies ++ that.testDependencies,
      compileDevDependencies ++ that.compileDevDependencies,
      testDevDependencies ++ that.testDevDependencies
    )
}

object NpmDependencies {

  /** Name of the file containing the NPM dependencies */
  val manifestFileName = "NPM_DEPENDENCIES"

  type Dependencies = List[(String, String)]

  implicit val decoder = Decoder.instance[NpmDependencies](h =>
    (
      h.downField("compile-dependencies")
        .as[List[TupledMap[String]]]
        .map(_.map(_.tupled)),
      h.downField("test-dependencies")
        .as[List[TupledMap[String]]]
        .map(_.map(_.tupled)),
      h.downField("compile-devDependencies")
        .as[List[TupledMap[String]]]
        .map(_.map(_.tupled)),
      h.downField("test-devDependencies")
        .as[List[TupledMap[String]]]
        .map(_.map(_.tupled))
    ).mapN(NpmDependencies.apply)
  )

  implicit val encoder = Encoder.instance[NpmDependencies](npm =>
    Json.obj(
      "compile-dependencies" -> npm.compileDependencies
        .map(TupledMap.fromTuple)
        .asJson,
      "test-dependencies" -> npm.compileDependencies
        .map(TupledMap.fromTuple)
        .asJson,
      "compile-devDependencies" -> npm.compileDependencies
        .map(TupledMap.fromTuple)
        .asJson,
      "test-devDependencies" -> npm.compileDependencies
        .map(TupledMap.fromTuple)
        .asJson
    )
  )

  /** @param cp
    *   Classpath
    * @return
    *   All the NPM dependencies found in the given classpath
    */
  def collectFromClasspath(cp: Def.Classpath): NpmDependencies =
    (
      for {
        cpEntry <- Attributed.data(cp) if cpEntry.exists
        results <-
          if (cpEntry.isFile && cpEntry.name.endsWith(".jar")) {
            val stream = new ZipInputStream(
              new BufferedInputStream(new FileInputStream(cpEntry))
            )
            try
              Iterator
                .continually(stream.getNextEntry())
                .takeWhile(_ != null)
                .filter(_.getName == NpmDependencies.manifestFileName)
                .map(_ =>
                  parser
                    .parse(IO.readStream(stream))
                    .flatMap(_.as[NpmDependencies])
                    .fold[NpmDependencies](throw _, identity(_))
                )
                .to(Seq)
            finally
              stream.close()
          } else if (cpEntry.isDirectory) {
            for {
              (file, _) <- Path.selectSubpaths(
                cpEntry,
                new ExactFilter(NpmDependencies.manifestFileName)
              )
            } yield parser
              .parse(IO.read(file))
              .flatMap(_.as[NpmDependencies])
              .fold[NpmDependencies](throw _, identity(_))
          } else sys.error(s"Illegal classpath entry: ${cpEntry.absolutePath}")
      } yield results
    ).fold(NpmDependencies(Nil, Nil, Nil, Nil))(_ ++ _)

  /** Writes the given dependencies into a manifest file
    */
  def writeManifest(
      npmDependencies: NpmDependencies,
      classDirectory: File
  ): File = {
    val manifestFile = classDirectory / manifestFileName
    IO.write(
      manifestFile,
      Encoder[NpmDependencies].apply(npmDependencies).noSpaces
    )
    manifestFile
  }

}
