package org.wildfly.swarm.tools;

import java.util.Set;

/**
 * @author Ken Finnigan
 */
public interface ProjectHelper {

    /**
     * Resolve dependencies of the project if they haven't been resolved yet,
     * or dependencies have been added since they were last resolved.
     */
    void resolveDependencies();

    /**
     * All direct and transitive dependencies defined by a Project excluding `test` scope.
     *
     * @return Set of direct and transitive dependencies.
     */
    Set<ArtifactSpec> allDependencies();

    /**
     * All direct and transitive dependencies defined by a Project excluding `test` scope
     * and application dependencies.
     *
     * @return Set of direct and transitive non application dependencies.
     */
    Set<ArtifactSpec> allNonApplicationDependencies();

    /**
     * Only application specific dependencies, and their transitives, are returned.
     * Any WildFly Swarm dependency defined in a project is removed before resolution.
     *
     * @return Set of application related dependencies.
     */
    Set<ArtifactSpec> applicationDependencies();

    /**
     * Add Dependency to project for resolution.
     * Invalidate resolved dependencies for later re-resolution.
     *
     * @param dependency Dependency to be added.
     */
    void addDependency(ArtifactSpec dependency);

}
