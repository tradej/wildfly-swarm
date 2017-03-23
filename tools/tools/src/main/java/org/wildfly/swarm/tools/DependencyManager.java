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
package org.wildfly.swarm.tools;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import org.wildfly.swarm.bootstrap.env.FractionManifest;
import org.wildfly.swarm.bootstrap.env.WildFlySwarmManifest;

/**
 * @author Bob McWhirter
 * @author Ken Finnigan
 * @author Heiko Braun
 */
public class DependencyManager {

    public static final String WILDFLY_SWARM_GROUP_ID = "org.wildfly.swarm";

    private static final String WILDFLY_SWARM_BOOTSTRAP_ARTIFACT_ID = "bootstrap";

    private static final String JBOSS_MODULES_GROUP_ID = "org.jboss.modules";

    private static final String JBOSS_MODULES_ARTIFACT_ID = "jboss-modules";

    DependencyManager(ProjectHelper projectHelper) {
        this.projectHelper = projectHelper;
    }

    void addAdditionalModule(Path module) {
        try {
            analyzeModuleDependencies(new ModuleAnalyzer(module));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static boolean isExplodedBootstrap(ArtifactSpec dependency) {
        if (dependency.groupId().equals(JBOSS_MODULES_GROUP_ID) && dependency.artifactId().equals(JBOSS_MODULES_ARTIFACT_ID)) {
            return true;
        }
        if (dependency.groupId().equals(WILDFLY_SWARM_GROUP_ID) && dependency.artifactId().equals(WILDFLY_SWARM_BOOTSTRAP_ARTIFACT_ID)) {
            return true;
        }
        return false;
    }

    static Stream<ModuleAnalyzer> findModuleXmls(File file) {
        List<ModuleAnalyzer> analyzers = new ArrayList<>();
        try (JarFile jar = new JarFile(file)) {

            Enumeration<JarEntry> entries = jar.entries();

            while (entries.hasMoreElements()) {
                JarEntry each = entries.nextElement();
                String name = each.getName();

                if (name.startsWith("modules/") && name.endsWith("module.xml")) {
                    try (InputStream in = jar.getInputStream(each)) {
                        analyzers.add(new ModuleAnalyzer(in));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return analyzers.stream();
    }

    public Set<ArtifactSpec> getUberJarDependencies() {
        return projectHelper.allNonApplicationDependencies();
    }

    public Set<ArtifactSpec> getApplicationDependencies() {
        return Collections.unmodifiableSet(projectHelper.applicationDependencies());
    }

    ArtifactSpec findWildFlySwarmBootstrapJar() {
        return findArtifact(WILDFLY_SWARM_GROUP_ID, WILDFLY_SWARM_BOOTSTRAP_ARTIFACT_ID, null, JAR, null, false);
    }

    ArtifactSpec findJBossModulesJar() {
        return findArtifact(JBOSS_MODULES_GROUP_ID, JBOSS_MODULES_ARTIFACT_ID, null, JAR, null, false);
    }

    ArtifactSpec findArtifact(String groupId, String artifactId, String version, String packaging, String classifier) {
        return findArtifact(groupId, artifactId, version, packaging, classifier, true);
    }

    ArtifactSpec findArtifact(String groupId, String artifactId, String version, String packaging, String classifier, boolean includeTestScope) {
        for (ArtifactSpec each : projectHelper.allDependencies()) {
            if (groupId != null && !groupId.equals(each.groupId())) {
                continue;
            }

            if (artifactId != null && !artifactId.equals(each.artifactId())) {
                continue;
            }

            if (version != null && !version.equals(each.version())) {
                continue;
            }

            if (packaging != null && !packaging.equals(each.type())) {
                continue;
            }

            if (classifier != null && !classifier.equals(each.classifier())) {
                continue;
            }

            if (!includeTestScope && each.scope.equals("test")) {
                continue;
            }

            return each;
        }

        return null;
    }

    void setProjectAsset(ProjectAsset projectAsset) {
        if (!this.applicationManifest.isHollow()) {
            this.projectAsset = projectAsset;
            this.applicationManifest.setAsset(this.projectAsset.getName());
        }
    }

    WildFlySwarmManifest getWildFlySwarmManifest() {
        return this.applicationManifest;
    }

    Set<ArtifactSpec> getModuleDependencies() {
        return moduleDependencies;
    }

    DependencyManager analyzeDependencies() throws Exception {
        projectHelper.resolveDependencies();

        analyzeFractionManifests();

        projectHelper.applicationDependencies()
                .forEach(e -> this.applicationManifest.addDependency(e.mavenGav()));

        analyzeModuleDependencies();

        return this;
    }

    void addDependency(ArtifactSpec dependency) {
        projectHelper.addDependency(dependency);
    }

    private void analyzeModuleDependencies() {
        projectHelper.allDependencies()
                .stream()
                .filter(e -> e.type().equals(JAR))
                .map(e -> e.file)
                .flatMap(DependencyManager::findModuleXmls)
                .forEach(this::analyzeModuleDependencies);

    }

    private void analyzeModuleDependencies(ModuleAnalyzer analyzer) {
        this.moduleDependencies.addAll(analyzer.getDependencies());
    }

    private void analyzeFractionManifests() {
        projectHelper.allDependencies()
                .stream()
                .map(e -> fractionManifest(e.file))
                .filter(manifest -> manifest != null)
                .forEach(manifest -> {
                    String module = manifest.getModule();
                    if (module != null) {
                        this.applicationManifest.addBootstrapModule(module);
                    }
                });

        projectHelper.allDependencies()
                .stream()
                .filter(e -> isFractionJar(e.file) || isConfigApiModulesJar(e.file))
                .forEach((artifact) -> {
                    this.applicationManifest.addBootstrapArtifact(artifact.mavenGav());
                });

    }

    protected boolean isConfigApiModulesJar(File file) {
        if (file == null) {
            return false;
        }

        try (JarFile jar = new JarFile(file)) {
            return jar.getEntry("wildfly-swarm-modules.conf") != null;
        } catch (IOException e) {
            // ignore
        }
        return false;

    }

    public static boolean isFractionJar(File file) {
        if (file == null) {
            return false;
        }

        try (JarFile jar = new JarFile(file)) {
            return jar.getEntry(FractionManifest.CLASSPATH_LOCATION) != null;
        } catch (IOException e) {
            // ignore
        }
        return false;
    }

    protected FractionManifest fractionManifest(File file) {
        try (JarFile jar = new JarFile(file)) {
            ZipEntry entry = jar.getEntry(FractionManifest.CLASSPATH_LOCATION);
            if (entry != null) {
                try (InputStream in = jar.getInputStream(entry)) {
                    return new FractionManifest(in);
                }
            }
        } catch (IOException e) {
            // ignore
        }
        return null;
    }

    private static final String JAR = "jar";

    private final WildFlySwarmManifest applicationManifest = new WildFlySwarmManifest();

    private final Set<ArtifactSpec> moduleDependencies = new HashSet<>();

    private ProjectAsset projectAsset;

    private ProjectHelper projectHelper;
}
