package org.wildfly.swarm.tools;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.jboss.shrinkwrap.api.Archive;
import org.jboss.shrinkwrap.api.ArchivePath;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.impl.base.GenericArchiveImpl;
import org.jboss.shrinkwrap.impl.base.filter.IncludeRegExpPaths;

/**
 * @author Bob McWhirter
 * @author Ken Finnigan
 */
class WebInfLibFilteringArchive extends GenericArchiveImpl {

    WebInfLibFilteringArchive(Archive<?> archive, DependencyManager dependencyManager) {
        super(archive);
        filter(dependencyManager);
    }

    protected void filter(DependencyManager dependencyManager) {
        Set<ArchivePath> remove = new HashSet<>();
        Set<ArtifactSpec> appDependencies = dependencyManager.getApplicationDependencies();

        Map<String, ArtifactSpec> appArtifacts = appDependencies.stream()
                .collect(Collectors.toMap(a -> a.artifactId() + "-" + a.version() + ".jar", a -> a));

        Collection<String> appArtifactNames = appArtifacts.keySet();

        getArchive().getContent(new IncludeRegExpPaths(""))
                .entrySet()
                .forEach(entry -> {
                    if (appArtifactNames.contains(entry.getKey().get())) {
                        // Artifact should be present
                        appArtifacts.remove(entry.getKey().get());
                    } else {
                        // Artifact should not be present
                        remove.add(entry.getKey());
                    }
                });

        for (ArchivePath each : remove) {
            getArchive().delete(each);
        }

        if (appArtifacts.size() > 0) {
            WebArchive war = getArchive().as(WebArchive.class);
            appArtifacts.values().forEach(a -> war.addAsLibrary(a.file));
        }
    }
}
