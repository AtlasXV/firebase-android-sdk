// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.gradle.plugins

import com.google.firebase.gradle.bomgenerator.BomGeneratorTask
import com.google.firebase.gradle.plugins.PublishingPlugin.Companion.BUILD_BOM_ZIP_TASK
import com.google.firebase.gradle.plugins.PublishingPlugin.Companion.BUILD_KOTLINDOC_ZIP_TASK
import com.google.firebase.gradle.plugins.PublishingPlugin.Companion.BUILD_MAVEN_ZIP_TASK
import com.google.firebase.gradle.plugins.PublishingPlugin.Companion.FIREBASE_PUBLISH_TASK
import com.google.firebase.gradle.plugins.PublishingPlugin.Companion.GENERATE_BOM_TASK
import com.google.firebase.gradle.plugins.PublishingPlugin.Companion.GENERATE_KOTLINDOC_FOR_RELEASE_TASK
import com.google.firebase.gradle.plugins.PublishingPlugin.Companion.PUBLISH_RELEASING_LIBS_TO_BUILD_TASK
import com.google.firebase.gradle.plugins.semver.ApiDiffer
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.publish.maven.tasks.PublishToMavenRepository
import org.gradle.api.tasks.bundling.Zip
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register

/**
 * Plugin for providing tasks to release [FirebaseLibrary][FirebaseLibraryPlugin] projects.
 *
 * Projects to release are computed via [computeReleasingLibraries]. A multitude of tasks are then
 * registered at the root project.
 *
 * The following pertain specifically to a release:
 * - [CHECK_HEAD_DEPS_TASK][registerCheckHeadDependenciesTask]
 * - [VALIDATE_POM_TASK][registerValidatePomForReleaseTask]
 * - [VALIDATE_PROJECTS_TO_PUBLISH_TASK][registerValidateProjectsToPublishTask]
 * - [BUILD_MAVEN_ZIP_TASK] -> Creates a zip file of the contents of
 * [PUBLISH_RELEASING_LIBS_TO_BUILD_TASK] [registerPublishReleasingLibrariesToBuildDirTask]
 * - [BUILD_KOTLINDOC_ZIP_TASK] -> Creates a zip file of the contents of
 * [GENERATE_KOTLINDOC_FOR_RELEASE_TASK] [registerGenerateKotlindocForReleaseTask]
 * - [FIREBASE_PUBLISH_TASK] -> Runs all the tasks above
 *
 * The following are additional tasks provided- that are either for convenience sake, or are used
 * outside of the standard [FIREBASE_PUBLISH_TASK] workflow (possibly at a later time in the release
 * cycle):
 * - [BUILD_BOM_ZIP_TASK] -> Creates a zip file of the contents of [GENERATE_BOM_TASK]
 * [registerGenerateBomTask]
 * - [RELEASE_GENEATOR_TASK][registerGenerateReleaseConfigFilesTask]
 * - [PUBLISH_RELEASING_LIBS_TO_LOCAL_TASK][registerPublishReleasingLibrariesToMavenLocalTask]
 * - [SEMVER_CHECK_TASK][registerSemverCheckForReleaseTask]
 * - [PUBLISH_ALL_TO_BUILD_TASK][registerPublishAllToBuildDir]
 */
abstract class PublishingPlugin : Plugin<Project> {
  override fun apply(project: Project) {
    project.gradle.projectsEvaluated {
      val allFirebaseLibraries = project.subprojects.mapNotNull { it.firebaseLibraryOrNull }
      val releasingFirebaseLibraries = computeReleasingLibraries(project, allFirebaseLibraries)
      val releasingProjects = releasingFirebaseLibraries.map { it.project }

      val generateBom = registerGenerateBomTask(project)
      val validatePomForRelease = registerValidatePomForReleaseTask(project, releasingProjects)
      val checkHeadDependencies =
        registerCheckHeadDependenciesTask(project, releasingFirebaseLibraries)
      val validateProjectsToPublish =
        registerValidateProjectsToPublishTask(project, releasingProjects)
      val publishReleasingLibrariesToBuildDir =
        registerPublishReleasingLibrariesToBuildDirTask(project, releasingProjects)
      val generateKotlindocsForRelease =
        registerGenerateKotlindocForReleaseTask(project, releasingFirebaseLibraries)

      registerGenerateReleaseConfigFilesTask(project)
      registerPublishReleasingLibrariesToMavenLocalTask(project, releasingProjects)
      registerSemverCheckForReleaseTask(project, releasingProjects)
      registerPublishAllToBuildDir(project, allFirebaseLibraries)

      val buildMavenZip =
        project.tasks.register<Zip>(BUILD_MAVEN_ZIP_TASK) {
          from(publishReleasingLibrariesToBuildDir)
          archiveFileName.set("m2repository.zip")
          destinationDirectory.set(project.layout.buildDirectory)
        }

      val buildKotlindocZip =
        project.tasks.register<Zip>(BUILD_KOTLINDOC_ZIP_TASK) {
          from(generateKotlindocsForRelease)
          archiveFileName.set("kotlindoc.zip")
          destinationDirectory.set(project.layout.buildDirectory)
        }

      project.tasks.register<Zip>(BUILD_BOM_ZIP_TASK) {
        from(generateBom)
        archiveFileName.set("bom.zip")
        destinationDirectory.set(project.layout.projectDirectory)
      }

      project.tasks.register(FIREBASE_PUBLISH_TASK) {
        dependsOn(
          validateProjectsToPublish,
          checkHeadDependencies,
          // validatePomForRelease, TODO(b/279466888) - Make GmavenHelper testable
          buildMavenZip,
          buildKotlindocZip
        )

        doLast {
          logger.lifecycle(
            "Publishing the following libraries:\n{}",
            releasingProjects.map { it.path }.joinToString("\n")
          )
        }
      }
    }
  }

  /**
   * Figures out what libraries are intended to release.
   *
   * Libraries can be provided either via the `projectsToPublish` property, or a [ReleaseConfig]
   * file.
   *
   * The `projectsToPublish` property takes priority over the [ReleaseConfig]. It expects a comma
   * separated list of the publishing project(s) `artifactId`. It also takes into account
   * [librariesToRelease][FirebaseLibraryExtension.getLibrariesToRelease] -> so there's no need to
   * specify multiple of the same co-releasing libs.
   *
   * The [ReleaseConfig] is a pre-defined set of data for a release. It expects a valid [file]
   * [ReleaseConfig.fromFile], which provides a list of libraries via their [path]
   * [FirebaseLibraryExtension.getPath]. Additionally, it does __NOT__ take into account
   * co-releasing libraries-> meaning libraries that should be releasing alongside one another will
   * need to be individually specified in the [ReleaseConfig], otherwise it will likely cause an
   * error during the release process.
   *
   * Example usage of `projectsToPublish`:
   * ```
   * ./gradlew firebasePublish -PprojectsToPublish="firebase-firestore,firebase-common"
   * ```
   *
   * See [ReleaseConfig.toFile] for example usage of [ReleaseConfig].
   *
   * If both `projectsToPublish` and [ReleaseConfig] are empty, or do not exist- this method will
   * return an empty list.
   */
  private fun computeReleasingLibraries(
    project: Project,
    allFirebaseLibraries: List<FirebaseLibraryExtension>
  ): List<FirebaseLibraryExtension> {
    val releasingLibrariesFromProperty = releasingLibsFromProperty(project)
    if (releasingLibrariesFromProperty != null) {
      return allFirebaseLibraries
        .filter { it.artifactId.get() in releasingLibrariesFromProperty }
        .flatMap { it.librariesToRelease }
        .distinctBy { it.artifactId.get() }
    }

    val releasingLibrariesFromReleseConfig = releasingLibsFromReleaseConfig(project)
    if (releasingLibrariesFromReleseConfig != null) {
      return allFirebaseLibraries.filter { it.path in releasingLibrariesFromReleseConfig }
    }

    return emptyList()
  }

  private fun releasingLibsFromProperty(project: Project) =
    project.provideProperty<String>("projectsToPublish").orNull?.split(",")

  private fun releasingLibsFromReleaseConfig(project: Project) =
    project.layout.projectDirectory
      .file(RELEASE_CONFIG_FILE)
      .asFile
      .takeIf { it.exists() }
      ?.let { ReleaseConfig.fromFile(it).libraries }

  /**
   * Registers the [GENERATE_BOM_TASK] task.
   *
   * Generates a BOM for a release, although it relies on gmaven to be updated- so it should be
   * invoked manually later on in the release process.
   *
   * @see BomGeneratorTask
   */
  private fun registerGenerateBomTask(project: Project) =
    project.tasks.register<BomGeneratorTask>(GENERATE_BOM_TASK)

  /**
   * Registers the [VALIDATE_POM_TASK] task.
   *
   * A collection of [PomValidator] for each releasing project.
   *
   * Ensures that pom dependencies are not accidently downgraded.
   */
  private fun registerValidatePomForReleaseTask(
    project: Project,
    releasingProjects: List<Project>
  ) =
    project.tasks.register(VALIDATE_POM_TASK) {
      for (releasingProject in releasingProjects) {
        val pomValidatorTask = releasingProject.tasks.named("isPomDependencyValid")

        dependsOn(pomValidatorTask)
      }
    }

  /**
   * Registers the [CHECK_HEAD_DEPS_TASK] task.
   *
   * Ensures that project level dependencies are included in the release.
   *
   * @see [CheckHeadDependencies]
   */
  private fun registerCheckHeadDependenciesTask(
    project: Project,
    releasingLibraries: List<FirebaseLibraryExtension>
  ) =
    project.tasks.register<CheckHeadDependencies>(CHECK_HEAD_DEPS_TASK) {
      projectsToPublish.set(releasingLibraries)
    }

  /**
   * Registers the [VALIDATE_PROJECTS_TO_PUBLISH_TASK] task.
   *
   * Ensures that there are releasing projects provided, to avoid potential false positive
   * edge-cases.
   *
   * @throws GradleException if no releasing projects are found.
   */
  // TODO(b/280320915): Remove doLast when Gradle + IDEA fix task configuration avoidance bug
  private fun registerValidateProjectsToPublishTask(
    project: Project,
    releasingProjects: List<Project>
  ) =
    project.tasks.register(VALIDATE_PROJECTS_TO_PUBLISH_TASK) {
      doLast {
        if (releasingProjects.isEmpty()) {
          throw GradleException(
            "No projects to release. " +
              "Ensure you've specified the projectsToPublish parameter, " +
              "or have a valid $RELEASE_CONFIG_FILE file at the root directory."
          )
        }
      }
    }

  /**
   * Registers the [PUBLISH_RELEASING_LIBS_TO_BUILD_TASK] task.
   *
   * A collection of [publishMavenAarPublicationToBuildDirRepository][PublishToMavenRepository] for
   * each releasing project.
   *
   * The artifacts are provided in a repository under the [buildDir][Project.getBuildDir] of the
   * provided [project].
   *
   * This task outputs the final repository directory.
   */
  private fun registerPublishReleasingLibrariesToBuildDirTask(
    project: Project,
    releasingProjects: List<Project>
  ) =
    project.tasks.register(PUBLISH_RELEASING_LIBS_TO_BUILD_TASK) {
      for (releasingProject in releasingProjects) {
        val publishTask =
          releasingProject.tasks.named<PublishToMavenRepository>(
            "publishMavenAarPublicationToBuildDirRepository"
          )

        dependsOn(publishTask)
        outputs.file(publishTask.map { it.repository.url })
      }
    }

  /**
   * Registers the [GENERATE_KOTLINDOC_FOR_RELEASE_TASK] task.
   *
   * A collection of [kotlindoc][DackkaPlugin] for each releasing library.
   *
   * Outputs a directory containing all the documentation generated for releasing projects.
   */
  private fun registerGenerateKotlindocForReleaseTask(
    project: Project,
    releasingLibraries: List<FirebaseLibraryExtension>
  ) =
    project.tasks.register(GENERATE_KOTLINDOC_FOR_RELEASE_TASK) {
      for (releasingLibrary in releasingLibraries) {
        val kotlindocTask = releasingLibrary.project.tasks.named("kotlindoc")

        dependsOn(kotlindocTask)

        if (releasingLibrary.publishJavadoc) {
          outputs.dir(kotlindocTask.map { it.outputs.files }).optional()
        }
      }
    }

  /**
   * Registers the [RELEASE_GENEATOR_TASK] task.
   *
   * Creates a config file that keeps track of what libraries are releasing. To learn more about the
   * file's format see [ReleaseConfig].
   *
   * @see [ReleaseGenerator]
   */
  private fun registerGenerateReleaseConfigFilesTask(project: Project) =
    project.tasks.register<ReleaseGenerator>(RELEASE_GENEATOR_TASK) {
      currentRelease.convention(project.provideProperty("currentRelease"))
      pastRelease.convention(project.provideProperty("pastRelease"))
      printReleaseConfig.convention(project.provideProperty("printOutput"))

      releaseConfigFile.convention(project.layout.projectDirectory.file(RELEASE_CONFIG_FILE))
      releaseReportFile.convention(project.layout.projectDirectory.file(RELEASE_REPORT_FILE))
    }

  /**
   * Registers the [PUBLISH_RELEASING_LIBS_TO_LOCAL_TASK] task.
   *
   * A collection of [publishMavenAarPublicationToMavenLocal][PublishToMavenRepository] for each
   * releasing project.
   *
   * Allows users to do local testing of releasing libraries, pulling the artifacts via the maven
   * local repository.
   */
  private fun registerPublishReleasingLibrariesToMavenLocalTask(
    project: Project,
    releasingProjects: List<Project>
  ) =
    project.tasks.register(PUBLISH_RELEASING_LIBS_TO_LOCAL_TASK) {
      for (releasingProject in releasingProjects) {
        val publishTask = releasingProject.tasks.named("publishMavenAarPublicationToMavenLocal")

        dependsOn(publishTask)
      }
    }

  /**
   * Registers the [SEMVER_CHECK_TASK] task.
   *
   * A collection of [ApiDiffer] for each releasing project.
   *
   * Ensures that the version of a releasing project aligns with the API. That is, if the API shows
   * signs of a major or minor bump- the version is bumped as expected.
   */
  private fun registerSemverCheckForReleaseTask(
    project: Project,
    releasingProjects: List<Project>
  ) =
    project.tasks.register(SEMVER_CHECK_TASK) {
      for (releasingProject in releasingProjects) {
        val semverCheckTask = releasingProject.tasks.named("semverCheck")

        dependsOn(semverCheckTask)
      }
    }

  /**
   * Registers the [PUBLISH_ALL_TO_BUILD_TASK] task.
   *
   * A collection of [publishMavenAarPublicationToBuildDirRepository][PublishToMavenRepository] for
   * __ALL__ firebase library projects.
   *
   * The artifacts are provided in a repository under the [buildDir][Project.getBuildDir] of the
   * provided [project].
   */
  private fun registerPublishAllToBuildDir(
    project: Project,
    allFirebaseLibraries: List<FirebaseLibraryExtension>
  ) =
    project.tasks.register(PUBLISH_ALL_TO_BUILD_TASK) {
      for (firebaseLibrary in allFirebaseLibraries) {
        val publishTask =
          firebaseLibrary.project.tasks.named("publishMavenAarPublicationToBuildDirRepository")

        dependsOn(publishTask)
      }
    }

  companion object {
    const val RELEASE_CONFIG_FILE = "release.json"
    const val RELEASE_REPORT_FILE = "release_report.md"

    const val GENERATE_BOM_TASK = "generateBom"
    const val VALIDATE_PROJECTS_TO_PUBLISH_TASK = "validateProjectsToPublish"
    const val SEMVER_CHECK_TASK = "semverCheckForRelease"
    const val RELEASE_GENEATOR_TASK = "generateReleaseConfig"
    const val VALIDATE_POM_TASK = "validatePomForRelease"
    const val PUBLISH_RELEASING_LIBS_TO_BUILD_TASK = "publishReleasingLibrariesToBuildDir"
    const val PUBLISH_RELEASING_LIBS_TO_LOCAL_TASK = "publishReleasingLibrariesToMavenLocal"
    const val GENERATE_KOTLINDOC_FOR_RELEASE_TASK = "generateKotlindocForRelease"
    const val CHECK_HEAD_DEPS_TASK = "checkHeadDependencies"
    const val BUILD_MAVEN_ZIP_TASK = "buildMavenZip"
    const val BUILD_KOTLINDOC_ZIP_TASK = "buildKotlindocZip"
    const val BUILD_BOM_ZIP_TASK = "buildBomZip"
    const val FIREBASE_PUBLISH_TASK = "firebasePublish"
    const val PUBLISH_ALL_TO_BUILD_TASK = "publishAllToBuildDir"

    const val BUILD_DIR_REPOSITORY_DIR = "m2repository"
  }
}
