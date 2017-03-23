package org.wildfly.swarm.tools.maven;

import java.io.File;
import java.util.function.Consumer;

import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.descriptor.MojoDescriptor;
import org.apache.maven.plugin.descriptor.Parameter;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingResult;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.wildfly.swarm.spi.container.BuildToolCreator;
import org.wildfly.swarm.tools.BuildTool;

/**
 * @author Ken Finnigan
 */
public class MavenBuildToolCreator implements BuildToolCreator {
    @Override
    public boolean canExecute() {
        this.pomFile = new File(System.getProperty("user.dir"), "pom.xml");
        return this.pomFile.exists() && this.pomFile.isFile();
    }

    @Override
    public BuildTool getBuilder() throws Exception {
        MavenHelper maven = new MavenHelper().setupContainer();

        ProjectBuilder projectBuilder = maven.lookup(ProjectBuilder.class);
        ProjectBuildingResult result = projectBuilder.build(this.pomFile, maven.newProjectBuildingRequest());

        MavenProject project = result.getProject();

        File projectArtifactFile = ArtifactHelper.artifactFile(project);
        if (projectArtifactFile == null || !projectArtifactFile.exists()) {
            // Build project artifact if it's not there.
            createProjectArtifact();
        }

        BuildTool builder = new BuildTool(new MavenArtifactResolvingHelper(maven), new MavenProjectHelper(project, maven))
                .bundleDependencies(false);

        Plugin wfsPlugin = project.getBuildPlugins().stream()
                .filter(p -> p.getArtifactId().equals("wildfly-swarm-plugin"))
                .findFirst()
                .orElse(null);

        PluginDescriptor pluginDescriptor = null;

        if (wfsPlugin != null) {
            pluginDescriptor = maven.lookup(BuildPluginManager.class).loadPlugin(wfsPlugin, project.getRemotePluginRepositories(), maven.repositorySystemSession());
        }

        setupBuildToolProperties("mainClass", pluginDescriptor, wfsPlugin, builder::mainClass);
        setupBuildToolProperties("fractionDetectionMode", pluginDescriptor, wfsPlugin, builder::fractionDetectionMode);
        setupBuildToolProperties("additionalModules", pluginDescriptor, wfsPlugin, builder::additionalModules);
        setupBuildToolProperties("additionalFractions", pluginDescriptor, wfsPlugin, builder::fraction);

        //TODO Handle setting `properties` and `propertiesFile` onto BuildTool.

        // Repackage war if needed
        if (project.getPackaging().equals("war")) {
            builder.repackageWar(projectArtifactFile);
        }

        return builder;
    }

    private void createProjectArtifact() {

    }

    private <T> void setupBuildToolProperties(String name, PluginDescriptor descriptor, Plugin plugin, Consumer<T> setter) throws Exception {
        if (descriptor != null) {
            MojoDescriptor mojoDescriptor = descriptor.getMojo("package");
            if (mojoDescriptor != null) {
                Parameter param = mojoDescriptor.getParameterMap().get(name);
                if (param != null && param.getDefaultValue() != null) {
                    setter.accept((T) param.getDefaultValue());
                }
            }
        }

        if (plugin != null) {
            PluginExecution pluginExec = plugin.getExecutionsAsMap().get("package");
            if (pluginExec != null) {
                Xpp3Dom configNode = (Xpp3Dom) pluginExec.getConfiguration();
                Xpp3Dom node = configNode.getChild(name);
                if (node != null && node.getValue() != null) {
                    setter.accept((T) node.getValue());
                }
            }
        }
    }

    private File pomFile = null;
}
