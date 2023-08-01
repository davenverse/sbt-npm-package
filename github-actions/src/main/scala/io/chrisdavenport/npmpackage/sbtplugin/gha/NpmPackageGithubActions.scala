package io.chrisdavenport.npmpackage.sbtplugin.gha

import sbt._
import Keys._
import _root_.org.typelevel.sbt.gha._

object NpmPackageGithubActions extends AutoPlugin {

    override def trigger = allRequirements

    override def requires: Plugins = GitHubActionsPlugin && plugins.JvmPlugin

    import GenerativeKeys._

    object autoImport {
      val npmPackageGHAShouldPublishNPM = settingKey[Boolean]("Whether or not to publish to NPM")
      val npmPackageGHANodeVersion = settingKey[Int]("Node version to use (default 18)")
      val npmPackageGHASetupNode = Def.setting(WorkflowStep.Use(
        UseRef.Public("actions", "setup-node", "v1"),
        Map(
          "node-version" -> s"${npmPackageGHANodeVersion.value}"
        )
      ))
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
        cond = Some("github.event_name != 'pull_request' && (startsWith(github.ref, 'refs/tags/v'))")
      )
    }



    import autoImport._

    override def buildSettings: Seq[Setting[_]] = Seq(
      npmPackageGHAShouldPublishNPM := true,
      npmPackageGHANodeVersion := 18,
      githubWorkflowTargetTags := {
        if (npmPackageGHAShouldPublishNPM.value) {
          val init = githubWorkflowTargetTags.value
          val s = "v*"
          if (init.contains(s)) init else init ++ Seq(s)
        } else githubWorkflowTargetTags.value
      },
      githubWorkflowPublishTargetBranches := {
        if (npmPackageGHAShouldPublishNPM.value) {
          val init = githubWorkflowPublishTargetBranches.value
          val s = RefPredicate.StartsWith(Ref.Tag("v"))
          if (init.contains(s)) init else init ++ Seq(s)
        }else githubWorkflowPublishTargetBranches.value
      },
      githubWorkflowBuildPreamble ++= Seq(npmPackageGHASetupNode.value),
      githubWorkflowPublishPreamble ++= Seq(npmPackageGHASetupNode.value),
      githubWorkflowBuild ++= Seq(npmPackageGHAPackageInstall),
      githubWorkflowPublish := {
        if (npmPackageGHAShouldPublishNPM.value) Seq(npmPackageGHAPublishNPM)
        else Seq()
      }
    )

}
