package ru.redenergy.resolver.maven;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;

import java.util.ArrayList;
import java.util.List;

public class MavenProvider {

    private static final String REPO_PATH = "dependencies/";

    private static MavenProvider INSTANCE;

    private final List<RemoteRepository> repositoryList = new ArrayList<>();

    private RepositorySystem system;

    private final DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

    public MavenProvider(){
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        system = locator.getService(RepositorySystem.class);
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, new LocalRepository(REPO_PATH)));
    }

    public ArtifactResult resolve(Artifact artifact) {
        ArtifactRequest request = new ArtifactRequest().setRepositories(repositoryList).setArtifact(artifact);
        try {
            return system.resolveArtifact(session, request);
        } catch (ArtifactResolutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    public List<ArtifactResult> resolveTransitively(Artifact artifact) {
        CollectRequest collectionRequest = new CollectRequest().setRoot(new Dependency(artifact, JavaScopes.COMPILE));
        repositoryList.forEach(collectionRequest::addRepository);
        DependencyRequest request = new DependencyRequest(collectionRequest, DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE));
        try {
            return system.resolveDependencies(session, request).getArtifactResults();
        } catch (DependencyResolutionException e) {
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    public void addRepository(RemoteRepository repository){
        this.repositoryList.add(repository);
    }

    public static MavenProvider instance(){
        if(INSTANCE == null){
            INSTANCE = new MavenProvider();
        }
        return INSTANCE;
    }

}
