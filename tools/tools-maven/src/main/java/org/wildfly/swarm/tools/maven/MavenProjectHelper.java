package org.wildfly.swarm.tools.maven;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Dependency;
import org.apache.maven.project.MavenProject;
import org.wildfly.swarm.tools.ArtifactSpec;
import org.wildfly.swarm.tools.DependencyManager;
import org.wildfly.swarm.tools.ProjectHelper;

/**
 * @author Ken Finnigan
 */
public class MavenProjectHelper implements ProjectHelper {
    public MavenProjectHelper(MavenProject project, MavenHelper mavenHelper) {
        this.project = project;
        this.mavenHelper = mavenHelper.setProjectHelper(this);
    }

    @Override
    public void resolveDependencies() {
        if (refreshDependencies) {

            dependenciesLoaded = false;
        }

        if (!dependenciesLoaded) {

            // Retrieve all dependencies not in `test` and `system` scopes
            allDependencies = project.getArtifacts().stream()
                    .filter(a -> !a.getScope().equals("test") && !a.getScope().equals("system"))
                    .map(MavenProjectHelper::toArtifactSpec)
                    .collect(Collectors.toSet());

            //
            List<Dependency> includableDependencies = project.getDependencies().stream()
                    .filter(d -> d.getScope().equals("compile") || d.getScope().equals("runtime"))
                    .collect(Collectors.toList());

            wfSwarmDirectDeps = includableDependencies.stream()
                    .filter(d -> d.getGroupId().equals(DependencyManager.WILDFLY_SWARM_GROUP_ID))
                    .map(MavenProjectHelper::toArtifactSpec)
                    .collect(Collectors.toSet());

            nonWFSwarmDirectDeps = includableDependencies.stream()
                    .filter(d -> !d.getGroupId().equals(DependencyManager.WILDFLY_SWARM_GROUP_ID))
                    .map(MavenProjectHelper::toArtifactSpec)
                    .collect(Collectors.toSet());
        }
    }

    @Override
    public Set<ArtifactSpec> allDependencies() {
        return allDependencies;
    }

    @Override
    public Set<ArtifactSpec> allNonApplicationDependencies() {
        return nonApplicationDependencies;
    }

    @Override
    public Set<ArtifactSpec> applicationDependencies() {
        return applicationDependencies;
    }

    @Override
    public void addDependency(ArtifactSpec dependency) {
        this.project.getDependencies().add(toDependency(dependency));
        this.refreshDependencies = true;
    }

    MavenProject project() {
        return this.project;
    }

    private static ArtifactSpec toArtifactSpec(Dependency dependency) {
        return new ArtifactSpec(dependency.getScope(),
                                dependency.getGroupId(),
                                dependency.getArtifactId(),
                                dependency.getVersion(),
                                dependency.getType(),
                                dependency.getClassifier(),
                                null);
    }

    private static ArtifactSpec toArtifactSpec(Artifact artifact) {
        return new ArtifactSpec(artifact.getScope(),
                                artifact.getGroupId(),
                                artifact.getArtifactId(),
                                artifact.getVersion(),
                                artifact.getType(),
                                artifact.getClassifier(),
                                artifact.getFile());
    }

    private Dependency toDependency(ArtifactSpec artifactSpec) {
        Dependency dep = new Dependency();
        dep.setScope(artifactSpec.scope);
        dep.setGroupId(artifactSpec.groupId());
        dep.setArtifactId(artifactSpec.artifactId());
        dep.setClassifier(artifactSpec.classifier());
        dep.setType(artifactSpec.type());
        dep.setVersion(artifactSpec.version());

        return dep;
    }

    private MavenProject project;

    private MavenHelper mavenHelper;

    private boolean dependenciesLoaded = false;

    private boolean refreshDependencies = false;

    private Set<ArtifactSpec> allDependencies;

    private Set<ArtifactSpec> nonApplicationDependencies;

    private Set<ArtifactSpec> applicationDependencies;
}
