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

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.eclipse.aether.repository.RemoteRepository;
import org.wildfly.swarm.fractions.FractionDescriptor;
import org.wildfly.swarm.fractions.FractionList;
import org.wildfly.swarm.spi.api.SwarmProperties;
import org.wildfly.swarm.tools.ArtifactSpec;
import org.wildfly.swarm.tools.DependencyManager;
import org.wildfly.swarm.tools.exec.SwarmExecutor;
import org.wildfly.swarm.tools.exec.SwarmProcess;

/**
 * @author Bob McWhirter
 * @author Ken Finnigan
 */
@Mojo(name = "start",
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME,
        requiresDependencyCollection = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class StartMojo extends AbstractSwarmMojo {

    @Parameter(alias = "stdoutFile", property = "swarm.stdout")
    public File stdoutFile;

    @Parameter(alias = "stderrFile", property = "swarm.stderr")
    public File stderrFile;

    @Parameter(alias = "useUberJar", defaultValue = "${wildfly-swarm.useUberJar}")
    public boolean useUberJar;

    @Parameter(alias = "debug", property = SwarmProperties.DEBUG_PORT)
    public Integer debugPort;

    @Parameter(alias = "jvmArguments", property = "swarm.jvmArguments")
    public List<String> jvmArguments = new ArrayList<>();

    @Parameter(alias = "arguments")
    public List<String> arguments = new ArrayList<>();

    @Parameter(property = "swarm.arguments", defaultValue = "")
    public String argumentsProp;

    boolean waitForProcess;

    @SuppressWarnings({"unchecked", "ThrowableResultOfMethodCallIgnored"})
    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        initProperties(true);
        initEnvironment();

        final SwarmExecutor executor;

        final String packaging = this.project.getPackaging();

        if (this.useUberJar) {
            executor = uberJarExecutor();
        } else if (packaging.equals("war") || packaging.equals("jar")) {
            executor = bareExecutor();
        } else {
            throw new MojoExecutionException("Unsupported packaging: " + this.project.getPackaging());
        }

        executor.withJVMArguments(this.jvmArguments);

        if (this.argumentsProp != null) {
            StringTokenizer args = new StringTokenizer(this.argumentsProp);
            while (args.hasMoreTokens()) {
                this.arguments.add(args.nextToken());
            }
        }

        executor.withArguments(this.arguments);

        final SwarmProcess process;
        try {
            process = executor.withDebug(debugPort)
                    .withProperties(this.properties)
                    .withStdoutFile(this.stdoutFile != null ? this.stdoutFile.toPath() : null)
                    .withStderrFile(this.stderrFile != null ? this.stderrFile.toPath() : null)
                    .withEnvironment(this.environment)
                    .withWorkingDirectory(this.project.getBasedir().toPath())
                    .withProperty("remote.maven.repo",
                                  String.join(",",
                                              this.project.getRemoteProjectRepositories().stream()
                                                      .map(RemoteRepository::getUrl)
                                                      .collect(Collectors.toList())))
                    .execute();

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    // Sleeping for a few millis will give time to shutdown gracefully
                    Thread.sleep(100L);
                    process.stop(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                }
            }));
            int startTimeoutSeconds;
            try {
                startTimeoutSeconds = Integer.valueOf(this.properties.getProperty("start.timeout.seconds", "120"));
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Wrong format of the start timeout!. Integer expected.", nfe);
            }

            process.awaitReadiness(startTimeoutSeconds, TimeUnit.SECONDS);

            if (!process.isAlive()) {
                throw new MojoFailureException("Process failed to start");
            }
            if (process.getError() != null) {
                throw new MojoFailureException("Error starting process", process.getError());
            }

        } catch (IOException e) {
            throw new MojoFailureException("unable to execute", e);
        } catch (InterruptedException e) {
            throw new MojoFailureException("Error waiting for deployment", e);
        }

        List<SwarmProcess> procs = (List<SwarmProcess>) getPluginContext().get("swarm-process");
        if (procs == null) {
            procs = new ArrayList<>();
            getPluginContext().put("swarm-process", procs);
        }
        procs.add(process);

        if (waitForProcess) {
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                try {
                    process.stop(10, TimeUnit.SECONDS);
                } catch (InterruptedException ie) {
                    // Do nothing
                }
            } finally {
                process.destroyForcibly();
            }
        }
    }

    protected SwarmExecutor uberJarExecutor() throws MojoFailureException {
        getLog().info("Starting -swarm.jar");

        String finalName = this.project.getBuild().getFinalName();

        if (finalName.endsWith(".war") || finalName.endsWith(".jar")) {
            finalName = finalName.substring(0, finalName.length() - 4);
        }

        return new SwarmExecutor()
                .withExecutableJar(Paths.get(this.projectBuildDir, finalName + "-swarm.jar"));
    }

    protected SwarmExecutor bareExecutor() throws MojoFailureException {
        final SwarmExecutor executor = new SwarmExecutor()
                .withClassPathEntries(dependencies());

        if (this.mainClass != null) {
            executor.withMainClass(this.mainClass);
        } else {
            executor.withDefaultMainClass();
        }

        return executor;
    }

    private List<Path> dependencies() throws MojoFailureException {
        final List<Path> elements =
                this.project.getDependencyArtifacts()
                        .stream()
                        .filter(a -> !a.getScope().equals("test"))
                        .filter(a -> a.getGroupId().equals(DependencyManager.WILDFLY_SWARM_GROUP_ID))
                        .map(a -> a.getFile().toPath())
                        .collect(Collectors.toList());

        // Explicitly add `container` to ensure it and its dependencies are on classpath
        FractionDescriptor containerDescriptor = FractionList.get()
                .getFractionDescriptor(DependencyManager.WILDFLY_SWARM_GROUP_ID, "container");

        try {
            elements.addAll(mavenArtifactResolvingHelper()
                                    .resolveAll(Collections.singletonList(ArtifactSpec.fromFractionDescriptor(containerDescriptor)))
                                    .stream()
                                    .map(s -> s.file.toPath())
                                    .collect(Collectors.toList()));
        } catch (Exception e) {
            throw new MojoFailureException("failed to resolve fraction dependencies", e);
        }

        // Add Project root to classpath
        elements.add(Paths.get(this.project.getBuild().getDirectory()));

        return elements;
    }
}
