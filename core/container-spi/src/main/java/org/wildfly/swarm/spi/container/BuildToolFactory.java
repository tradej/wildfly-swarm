package org.wildfly.swarm.spi.container;

import java.util.ServiceLoader;

import org.wildfly.swarm.tools.BuildTool;

/**
 * @author Ken Finnigan
 */
public final class BuildToolFactory {

    private BuildToolFactory() {
    }

    public static BuildTool getBuilder() throws Exception {
        ServiceLoader<BuildToolCreator> creators = ServiceLoader.load(BuildToolCreator.class);

        BuildToolCreator creator = null;

        for (BuildToolCreator buildToolCreator : creators) {
            if (buildToolCreator.canExecute()) {
                if (creator == null) {
                    creator = buildToolCreator;
                } else {
                    throw new Exception("Multiple BuildToolCreators are able to execute!");
                }
            }
        }

        if (creator == null) {
            throw new Exception("No BuildToolCreator instances found.");
        }

        return creator.getBuilder();
    }
}
