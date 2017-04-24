package org.wildfly.swarm.tools.maven;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequestPopulator;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.settings.Settings;
import org.codehaus.plexus.ContainerConfiguration;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.classworlds.ClassWorld;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.CollectResult;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.impl.ArtifactResolver;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.LocalArtifactRequest;
import org.eclipse.aether.repository.LocalArtifactResult;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;
import org.jboss.shrinkwrap.resolver.impl.maven.bootstrap.MavenSettingsBuilder;
import org.wildfly.swarm.tools.ArtifactSpec;

/**
 * @author Ken Finnigan
 */
public class MavenHelper {

    MavenHelper setupContainer() throws Exception {
        if (container != null) {
            return this;
        }

        ContainerConfiguration config = setupContainerConfiguration();
        container = new DefaultPlexusContainer(config);
        mavenSettings = new MavenSettingsBuilder().buildDefaultSettings();

        commonSetup();

        return this;
    }

    public MavenHelper setupContainer(PlexusContainer container, Settings settings) throws Exception {
        this.container = container;
        this.mavenSettings = settings;

        commonSetup();

        return this;
    }

    private void commonSetup() throws Exception {
        setupCommonRequest();

        repositorySystemSession = MavenRepositorySystemUtils.newSession();
        repositorySystem = lookup(org.eclipse.aether.RepositorySystem.class);
        artifactResolver = lookup(ArtifactResolver.class);

        jbossRepository = buildRemoteRepository("jboss-public-repository-group", "https://repository.jboss.org/nexus/content/groups/public/");
    }

    public List<Dependency> collectTransitiveDependencies(Collection<ArtifactSpec> specs) throws Exception {
        CollectRequest collect = new CollectRequest();
        collect.setRootArtifact(RepositoryUtils.toArtifact(projectHelper.project().getArtifact()));
        collect.setRequestContext("project");
        collect.setRepositories(projectHelper.project().getRemoteProjectRepositories());

        DependencyManagement depMngt = projectHelper.project().getDependencyManagement();
        if (depMngt != null) {
            for (org.apache.maven.model.Dependency dependency : depMngt.getDependencies()) {
                collect.addManagedDependency(RepositoryUtils.toDependency(dependency, repositorySystemSession.getArtifactTypeRegistry()));
            }
        }

        specs.forEach(spec ->
                              collect.addDependency(new Dependency(new DefaultArtifact(spec.groupId(),
                                                                                       spec.artifactId(),
                                                                                       spec.classifier(),
                                                                                       spec.type(),
                                                                                       spec.version()),
                                                                   spec.scope)));

        CollectResult result = repositorySystem.collectDependencies(repositorySystemSession, collect);
        PreorderNodeListGenerator gen = new PreorderNodeListGenerator();
        result.getRoot().accept(gen);

        return gen.getNodes().stream().map(DependencyNode::getDependency).collect(Collectors.toList());
    }

    public ArtifactSpec resolveArtifact(ArtifactSpec spec) {
        final DefaultArtifact artifact = new DefaultArtifact(spec.groupId(), spec.artifactId(), spec.classifier(),
                                                             spec.type(), spec.version());

        final LocalArtifactResult localResult = repositorySystemSession.getLocalRepositoryManager()
                .find(repositorySystemSession, new LocalArtifactRequest(artifact, projectHelper.project().getRemoteProjectRepositories(), null));
        if (localResult.isAvailable()) {
            spec.file = localResult.getFile();
            return spec;
        } else {
            try {
                final ArtifactResult result = artifactResolver.resolveArtifact(repositorySystemSession,
                                                                               new ArtifactRequest(artifact,
                                                                                                   projectHelper.project().getRemoteProjectRepositories(),
                                                                                                   null));
                if (result.isResolved()) {
                    spec.file = result.getArtifact().getFile();
                    return spec;
                }
            } catch (ArtifactResolutionException e) {
                e.printStackTrace();
            }
        }

        return null;
    }

    /**
     * This is needed to speed up things.
     */
    public void resolveDependenciesInParallel(List<Dependency> nodes) {
        List<ArtifactRequest> artifactRequests = nodes.stream()
                .map(node -> new ArtifactRequest(node.getArtifact(), this.projectHelper.project().getRemoteProjectRepositories(), null))
                .collect(Collectors.toList());

        try {
            artifactResolver.resolveArtifacts(this.repositorySystemSession, artifactRequests);
        } catch (ArtifactResolutionException e) {
            // ignore, error will be printed by resolve(ArtifactSpec)
        }
    }

    MavenHelper setProjectHelper(MavenProjectHelper helper) {
        this.projectHelper = helper;

        // Setup JBoss Repo remote repo if not present
        if (this.projectHelper.project().getRemoteProjectRepositories().stream().noneMatch(r -> r.getUrl().equals(jbossRepository.getUrl()))) {
            this.projectHelper.project().getRemoteProjectRepositories().add(jbossRepository);
        }

        return this;
    }

    public RemoteRepository buildRemoteRepository(String id, String url) {
        RemoteRepository.Builder builder = new RemoteRepository.Builder(id, "default", url);

        RepositoryPolicy policy = new RepositoryPolicy();
        builder.setReleasePolicy(policy);
        builder.setSnapshotPolicy(policy);

        RemoteRepository tempRepo = builder.build();

        builder = new RemoteRepository.Builder(tempRepo);

        // Handle authentication
        Authentication auth = repositorySystemSession.getAuthenticationSelector().getAuthentication(tempRepo);
        builder.setAuthentication(auth);

        // Handle mirror
        RemoteRepository.Builder mirrorBuilder = new RemoteRepository.Builder(repositorySystemSession.getMirrorSelector().getMirror(tempRepo));
        mirrorBuilder.setAuthentication(auth);
        builder.addMirroredRepository(mirrorBuilder.build());

        // Handle proxy
        builder.setProxy(repositorySystemSession.getProxySelector().getProxy(tempRepo));

        return builder.build();
    }

    public ProjectBuildingRequest newProjectBuildingRequest() {
        DefaultProjectBuildingRequest projectBuildingRequest = new DefaultProjectBuildingRequest();
        projectBuildingRequest.setLocalRepository(commonRequest.getLocalRepository());
        projectBuildingRequest.setRepositorySession(repositorySystemSession);
        projectBuildingRequest.setSystemProperties(commonRequest.getSystemProperties());
        projectBuildingRequest.setUserProperties(commonRequest.getUserProperties());
        projectBuildingRequest.setRemoteRepositories(commonRequest.getRemoteRepositories());
        projectBuildingRequest.setPluginArtifactRepositories(commonRequest.getPluginArtifactRepositories());
        projectBuildingRequest.setActiveProfileIds(commonRequest.getActiveProfiles());
        projectBuildingRequest.setInactiveProfileIds(commonRequest.getInactiveProfiles());
        projectBuildingRequest.setProfiles(commonRequest.getProfiles());
        projectBuildingRequest.setProcessPlugins(true);
        projectBuildingRequest.setResolveDependencies(true);
        return projectBuildingRequest;
    }

    public <T> T lookup(Class<T> clazz) throws Exception {
        return this.container.lookup(clazz);
    }

    private ArtifactRepository localRepository() throws Exception {
        return lookup(RepositorySystem.class).createLocalRepository(new File(localRepositoryPath()));
    }

    private String localRepositoryPath() {
        String path = mavenSettings.getLocalRepository();

        if (path == null) {
            path = RepositorySystem.defaultUserLocalRepository.getAbsolutePath();
        }

        return path;
    }

    private void setupCommonRequest() throws Exception {
        if (commonRequest != null) {
            return;
        }

        commonRequest = new DefaultMavenExecutionRequest();
        lookup(MavenExecutionRequestPopulator.class).populateFromSettings(commonRequest, mavenSettings);

        commonRequest.setLocalRepository(localRepository());
        commonRequest.setLocalRepositoryPath(commonRequest.getLocalRepository().getBasedir());
        commonRequest.setSystemProperties(System.getProperties());
    }

    private ContainerConfiguration setupContainerConfiguration() throws Exception {
        ClassWorld classWorld = new ClassWorld("plexus.core", ClassWorld.class.getClassLoader());

        return new DefaultContainerConfiguration()
                .setClassWorld(classWorld)
                .setRealm(classWorld.getRealm("plexus.core"))
                .setClassPathScanning(PlexusConstants.SCANNING_INDEX)
                .setAutoWiring(true)
                .setName("maven");
    }

    private PlexusContainer container;

    private Settings mavenSettings;

    private MavenExecutionRequest commonRequest;

    private org.eclipse.aether.RepositorySystem repositorySystem;

    private RepositorySystemSession repositorySystemSession;

    private MavenProjectHelper projectHelper;

    private RemoteRepository jbossRepository;

    private ArtifactResolver artifactResolver;
}
