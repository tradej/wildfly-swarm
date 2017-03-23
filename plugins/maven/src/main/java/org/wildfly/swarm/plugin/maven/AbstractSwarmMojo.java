/**
 * Copyright 2015-2016 Red Hat, Inc, and individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wildfly.swarm.plugin.maven;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import javax.inject.Inject;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.PlexusContainer;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.wildfly.swarm.fractions.PropertiesUtil;
import org.wildfly.swarm.tools.BuildTool;
import org.wildfly.swarm.tools.maven.MavenArtifactResolvingHelper;
import org.wildfly.swarm.tools.maven.MavenHelper;

/**
 * @author Bob McWhirter
 */
public abstract class AbstractSwarmMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", readonly = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    protected DefaultRepositorySystemSession repositorySystemSession;

    @Parameter(defaultValue = "${project.build.directory}")
    protected String projectBuildDir;

    @Parameter(defaultValue = "${session}", readonly = true)
    protected MavenSession mavenSession;

    @Parameter(alias = "mainClass", property = "swarm.mainClass")
    protected String mainClass;

    @Parameter(alias = "properties")
    protected Properties properties;

    @Parameter(alias = "propertiesFile", property = "swarm.propertiesFile")
    protected String propertiesFile;

    @Parameter(alias = "environment")
    protected Properties environment;

    @Parameter(alias = "environmentFile", property = "swarm.environmentFile")
    protected String environmentFile;

    @Parameter(alias = "modules")
    protected List<String> additionalModules = new ArrayList<>();

    @Parameter(alias = "fractions")
    protected List<String> additionalFractions = new ArrayList<>();

    @Parameter(defaultValue = "when_missing", property = "swarm.detect.mode")
    protected BuildTool.FractionDetectionMode fractionDetectMode;

    @Inject
    protected PlexusContainer plexusContainer;

    private MavenHelper mavenHelper;

    AbstractSwarmMojo() {
        if (this.additionalModules.isEmpty()) {
            this.additionalModules.add("modules");
        }
    }

    protected void initProperties(final boolean withMaven) {
        if (this.properties == null) {
            this.properties = new Properties();
        }

        if (this.propertiesFile != null) {
            try {
                this.properties.putAll(PropertiesUtil.loadProperties(this.propertiesFile));
            } catch (IOException e) {
                getLog().error("Failed to load properties from " + this.propertiesFile, e);
            }
        }

        this.properties.putAll(PropertiesUtil.filteredSystemProperties(this.properties, withMaven));
    }

    protected void initEnvironment() throws MojoFailureException {
        if (this.environment == null) {
            this.environment = new Properties();
        }
        if (this.environmentFile != null) {
            try {
                this.environment.putAll(PropertiesUtil.loadProperties(this.environmentFile));
            } catch (IOException e) {
                getLog().error("Failed to load environment from " + this.environmentFile, e);
            }
        }
    }

    MavenArtifactResolvingHelper mavenArtifactResolvingHelper() throws MojoExecutionException {
        return new MavenArtifactResolvingHelper(mavenHelper());
    }

    protected MavenHelper mavenHelper() throws MojoExecutionException {
        if (mavenHelper == null) {
            try {
                mavenHelper = new MavenHelper().setupContainer(plexusContainer, mavenSession.getSettings());
            } catch (Exception e) {
                throw new MojoExecutionException("Failed to setup MavenHelper", e);
            }
        }

        return mavenHelper;
    }

}
