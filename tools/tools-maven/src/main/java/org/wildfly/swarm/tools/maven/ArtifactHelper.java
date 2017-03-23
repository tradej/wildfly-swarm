package org.wildfly.swarm.tools.maven;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.maven.project.MavenProject;

/**
 * @author Ken Finnigan
 */
public final class ArtifactHelper {
    private ArtifactHelper() {
    }

    public static File artifactFile(MavenProject project) {
        if (project.getArtifact().getFile() != null) {
            return project.getArtifact().getFile();
        }

        String finalName = project.getBuild().getFinalName();

        Path candidate = Paths.get(project.getBuild().getDirectory(), finalName + "." + project.getPackaging());

        if (Files.exists(candidate)) {
            return candidate.toFile();
        }

        return null;
    }
}
