package org.wildfly.swarm.spi.container;

import org.wildfly.swarm.tools.BuildTool;

/**
 * @author Ken Finnigan
 */
public interface BuildToolCreator {
    boolean canExecute();

    BuildTool getBuilder() throws Exception;
}
