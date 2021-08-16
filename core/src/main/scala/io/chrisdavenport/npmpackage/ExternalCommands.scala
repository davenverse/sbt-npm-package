package io.chrisdavenport.npmpackage

import sbt.Logger
import java.io.{InputStream, File}
import scala.sys.process.Process
import scala.sys.process.BasicIO
import scala.sys.process.ProcessLogger

import java.io.File

import sbt._

/**
  * Attempts to smoothen platform-specific differences when invoking commands.
  *
  * @param name Name of the command to run
  */
class ExternalCommand(name: String) {

  /**
    * Runs the command `cmd`
    * @param args Command arguments
    * @param workingDir Working directory of the process
    * @param logger Logger
    */
  def run(args: String*)(workingDir: File, logger: Logger): Unit =
    ExternalCommand.Commands.run(cmd ++: args, workingDir, logger)

  private val cmd = sys.props("os.name").toLowerCase match {
    case os if os.contains("win") => Seq("cmd", "/c", name)
    case _                        => Seq(name)
  }

}

object Npm extends ExternalCommand("npm")

object Yarn extends ExternalCommand("yarn")

object ExternalCommand {
  private val yarnOptions = List("--non-interactive", "--mutex", "network")

  private def syncLockfile(
    lockFileName: String,
    baseDir: File,
    installDir: File,
    logger: Logger
  )(
      command: => Unit
  ): Unit = {
    val sourceLockFile = baseDir / lockFileName
    val targetLockFile = installDir / lockFileName

    if (sourceLockFile.exists()) {
      logger.info("Using lockfile " + sourceLockFile)
      IO.copyFile(sourceLockFile, targetLockFile)
    }

    command

    if (targetLockFile.exists()) {
      logger.debug("Wrote lockfile to " + sourceLockFile)
      IO.copyFile(targetLockFile, sourceLockFile)
    }
  }

  // private def syncYarnLockfile(
  //   baseDir: File,
  //   installDir: File,
  //   logger: Logger
  // )(
  //   command: => Unit
  // ): Unit = {
  //   syncLockfile("yarn.lock", baseDir, installDir, logger)(command)
  // }

  // private def syncNpmLockfile(
  //   baseDir: File,
  //   installDir: File,
  //   logger: Logger
  // )(
  //   command: => Unit
  // ): Unit = {
  //   syncLockfile("package-lock.json", baseDir, installDir, logger)(command)
  // }

  /**
    * Locally install NPM packages
    *
    * @param baseDir The (sub-)project directory which contains yarn.lock
    * @param installDir The directory in which to install the packages
    * @param useYarn Whether to use yarn or npm
    * @param logger sbt logger
    * @param npmExtraArgs Additional arguments to pass to npm
    * @param npmPackages Packages to install (e.g. "webpack", "webpack@2.2.1")
    */
  def addPackages(baseDir: File,
                  installDir: File,
                  useYarn: Boolean,
                  logger: Logger,
                  npmExtraArgs: Seq[String],
                  yarnExtraArgs: Seq[String])(npmPackages: String*): Unit =
    if (useYarn) {
      ///syncYarnLockfile(baseDir, installDir, logger) {
        Yarn.run("add" +: (yarnOptions ++ yarnExtraArgs ++ npmPackages): _*)(
          installDir,
          logger)
      // }
    } else {
      // syncNpmLockfile(baseDir, installDir, logger) {
        Npm.run("install" +: (npmPackages ++ npmExtraArgs): _*)(installDir, logger)
      // }
    }

  def install(baseDir: File,
              installDir: File,
              useYarn: Boolean,
              logger: Logger,
              npmExtraArgs: Seq[String],
              yarnExtraArgs: Seq[String]): Unit =
    if (useYarn) {
      // syncYarnLockfile(baseDir, installDir, logger) {
        Yarn.run("install" +: (yarnOptions ++ yarnExtraArgs): _*)(installDir,
                                                                  logger)
      // }
    } else {
      // syncNpmLockfile(baseDir, installDir, logger) {
        Npm.run("install" +: npmExtraArgs: _*)(installDir, logger)
      // }
    }

  def publish(
    baseDir: File,
    installDir: File,
    useYarn: Boolean,
    logger: Logger,
    npmExtraArgs: Seq[String],
    yarnExtraArgs: Seq[String]
  ) = 
    if (useYarn) {
      // syncYarnLockfile(baseDir, installDir, logger) {
        Yarn.run("publish" +: (yarnOptions ++ yarnExtraArgs): _*)(installDir,
                                                                  logger)
      // }
    } else {
      // syncNpmLockfile(baseDir, installDir, logger) {
        Npm.run("publish" +: npmExtraArgs: _*)(installDir, logger)
      // }
    }

  object Commands {

    def run[A](cmd: Seq[String], cwd: File, logger: Logger, outputProcess: InputStream => A): Either[String, Option[A]] = {
      val toErrorLog = (is: InputStream) => {
        scala.io.Source.fromInputStream(is).getLines.foreach(msg => logger.error(msg))
        is.close()
      }

      // Unfortunately a var is the only way to capture the result
      var result: Option[A] = None
      def outputCapture(o: InputStream): Unit = {
        result = Some(outputProcess(o))
        o.close()
        ()
      }

      logger.debug(s"Command: ${cmd.mkString(" ")}")
      val process = Process(cmd, cwd)
      val processIO = BasicIO.standard(false).withOutput(outputCapture).withError(toErrorLog)
      val code: Int = process.run(processIO).exitValue()
      if (code != 0) {
        Left(s"Non-zero exit code: $code")
      } else {
        Right(result)
      }
    }

    def run(cmd: Seq[String], cwd: File, logger: Logger): Unit = {
      val toInfoLog = (is: InputStream) => scala.io.Source.fromInputStream(is).getLines.foreach(msg => logger.info(msg))
      run(cmd, cwd, logger, toInfoLog).fold(sys.error, _ => ())
    }

    def start(cmd: Seq[String], cwd: File, logger: Logger): Process =
      Process(cmd, cwd).run(toProcessLogger(logger))

    private def toProcessLogger(logger: Logger): ProcessLogger =
      ProcessLogger(msg => logger.info(msg), msg => logger.error(msg))

  }
}


