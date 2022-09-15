package io.chrisdavenport.npmpackage.sbtplugin.gha

import sbt._
import Keys._
import _root_.org.typelevel.sbt.gha._

class NpmPackageGithubActions extends AutoPlugin {

    override def trigger = allRequirements

    override def requires: Plugins = GitHubActionsPlugin && plugins.JvmPlugin

    import GenerativeKeys._

    object autoImport {
      val npmPackageGHAShouldPublish = settingKey[Boolean]("Whether or not to publish java/sjs artifacts")
      val npmPackageGHAShouldPublishNPM = settingKey[Boolean]("Whether or not to publish to NPM")
      val npmPackageGHASetupNode = WorkflowStep.Use(
        UseRef.Public("actions", "setup-node", "v1"),
        Map(
          "node-version" -> "16"
        )
      )
      val npmPackageGHAPackageInstall = WorkflowStep.Sbt(
        List("npmPackageInstall"),
        name = Some("Install artifacts to npm")
      )
      val npmPackageGHAPublishNPM = WorkflowStep.Sbt(
        List("npmPackageNpmrc", "npmPackagePublish"),
        name = Some("Publish artifacts to npm"),
        env = Map(
          "NPM_TOKEN" -> "${{ secrets.NPM_TOKEN }}" // https://docs.npmjs.com/using-private-packages-in-a-ci-cd-workflow#set-the-token-as-an-environment-variable-on-the-cicd-server
        ),
      )

    }



    import autoImport._

    override def buildSettings: Seq[Setting[_]] = Seq(
      // githubWorkflowTargetTags := Seq("v*"),
      // githubWorkflowPublishTargetBranches := Seq(
      //   RefPredicate.StartsWith(Ref.Tag("v")),
      // ),
      npmPackageGHAShouldPublish := false,
      npmPackageGHAShouldPublishNPM := true,
      githubWorkflowBuildPreamble ++= Seq(npmPackageGHASetupNode),
      githubWorkflowPublishPreamble ++= Seq(npmPackageGHASetupNode),
      githubWorkflowBuild ++= Seq(npmPackageGHAPackageInstall),
      githubWorkflowPublish := {
        val publishSteps = {
          if (npmPackageGHAShouldPublish.value)
            Seq(WorkflowStep.Sbt(List("+publish"), name = Some("Publish project")))
          else Seq()
        }
        val publishNpmSteps = {
          if (npmPackageGHAShouldPublishNPM.value) Seq(npmPackageGHAPublishNPM)
          else Seq()
        }
        publishSteps ++ publishNpmSteps
      }
    )

}
