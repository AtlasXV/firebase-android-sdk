package com.google.firebase.gradle.plugins.publish;

import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;

/** weiping@atlasv.com 2022/11/16 */
public class AtlasvPublishConfig {
  public static String REPO_URL = "https://maven.pkg.github.com/AtlasXV/android-libs";

  public static String getRepoUserName(Project project) {
    return project.findProperty("GPR_USR").toString();
  }

  public static String getRepoPassword(Project project) {
    return project.findProperty("GPR_KEY").toString();
  }

  public static void configPublishRepo(Project project, MavenArtifactRepository repo) {
    repo.setUrl(REPO_URL);
    repo.setName("BuildDir");
    repo.getCredentials().setUsername(getRepoUserName(project));
    repo.getCredentials().setPassword(getRepoPassword(project));
  }
}
