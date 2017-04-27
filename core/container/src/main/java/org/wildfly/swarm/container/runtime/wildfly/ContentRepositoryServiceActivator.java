package org.wildfly.swarm.container.runtime.wildfly;

import org.jboss.msc.service.ServiceActivator;
import org.jboss.msc.service.ServiceActivatorContext;
import org.jboss.msc.service.ServiceRegistryException;

/** This is a hack to overcome the fact wildfly-core installs
 *  a badly-written ContentRepository, and we don't want to have
 *  to PR upstream.
 *
 * @author Bob McWhirter
 */
public class ContentRepositoryServiceActivator implements ServiceActivator {

    private final SwarmContentRepository repository;

    public ContentRepositoryServiceActivator(SwarmContentRepository repository) {
        this.repository = repository;
    }

    @Override
    public void activate(ServiceActivatorContext context) throws ServiceRegistryException {
        SwarmContentRepository.addService(context.getServiceTarget(), repository);
    }
}
