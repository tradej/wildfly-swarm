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
package org.wildfly.swarm.tools.maven;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.Dependency;
import org.wildfly.swarm.tools.ArtifactResolvingHelper;
import org.wildfly.swarm.tools.ArtifactSpec;

/**
 * @author Bob McWhirter
 */
public class MavenArtifactResolvingHelper implements ArtifactResolvingHelper {

    public MavenArtifactResolvingHelper(MavenHelper mavenHelper) {
        this.mavenHelper = mavenHelper;
    }

    @Override
    public ArtifactSpec resolve(ArtifactSpec spec) {
        if (spec.file == null) {
            spec = mavenHelper.resolveArtifact(spec);
        }

        return spec.file != null ? spec : null;
    }

    @Override
    public Set<ArtifactSpec> resolveAll(Collection<ArtifactSpec> specs, boolean transitive) throws Exception {
        if (specs.isEmpty()) {
            return Collections.emptySet();
        }
        List<Dependency> dependencies;
        if (transitive) {
            dependencies = mavenHelper.collectTransitiveDependencies(specs);
        } else {
            dependencies = specs.stream()
                    .map(spec -> new Dependency(new DefaultArtifact(spec.groupId(),
                                                                    spec.artifactId(),
                                                                    spec.classifier(),
                                                                    spec.type(),
                                                                    spec.version()),
                                                spec.scope))
                    .collect(Collectors.toList());
        }

        mavenHelper.resolveDependenciesInParallel(dependencies);

        return dependencies.stream()
                .filter(dependency -> !"system".equals(dependency.getScope()))
                .map(dependency -> {
                    final Artifact artifact = dependency.getArtifact();
                    return new ArtifactSpec(dependency.getScope(),
                                            artifact.getGroupId(),
                                            artifact.getArtifactId(),
                                            artifact.getVersion(),
                                            artifact.getExtension(),
                                            artifact.getClassifier(),
                                            null);
                })
                .map(this::resolve)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }


    private MavenHelper mavenHelper;
}
