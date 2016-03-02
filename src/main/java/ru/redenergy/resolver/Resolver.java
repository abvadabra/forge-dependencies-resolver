package ru.redenergy.resolver;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import ru.redenergy.resolver.config.ResolutionCache;
import ru.redenergy.resolver.domain.Dependencies;
import ru.redenergy.resolver.domain.DomainArtifact;
import ru.redenergy.resolver.domain.DomainRepository;
import ru.redenergy.resolver.domain.ResolveDSLParser;
import ru.redenergy.resolver.maven.MavenProvider;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Resolver {

    private static RemoteRepository central = new RemoteRepository.Builder("central", "default", "http://repo1.maven.org/maven2/").build();

    private static final String mainConfigFile = System.getProperty("mainConfig", "dependencies.groovy");
    private static final String deployFolder = System.getProperty("deployPath", "mods/");

    private ResolutionCache oldCache;
    private ResolutionCache resolutionCache = new ResolutionCache();
    private List<Dependencies> dependenciesList = new ArrayList<>();
    private List<Artifact> resolvedArtifacts = new ArrayList<>();

    public void launch() throws IOException, DependencyResolutionException {
        MavenProvider.instance().addRepository(central);
        parseMainConfig();
        parseSubmodules();
        resolveParsedArtifacts();
        parseOldCache();
        generateNewCache();
        removeUnusedArtifacts();
        deployNewArtifacts();
        recreateCache();
        System.out.println("WARNING: Do not remove `resolution.cache` file in mods/ directory!");
    }

    private void parseMainConfig() throws IOException {
        Dependencies mainConfig = (Dependencies) new ResolveDSLParser().shell().evaluate(new File(mainConfigFile));
        dependenciesList.add(mainConfig);
    }

    private void parseSubmodules() throws IOException {
        if(!Files.exists(Paths.get(deployFolder))) Files.createDirectory(Paths.get(deployFolder));
        for(File module : FileUtils.listFiles(new File(deployFolder), new String[]{"zip", "jar"}, false)){
            JarFile jar = new JarFile(module);
            JarEntry modmeta = jar.getJarEntry("dependencies.groovy");
            if(modmeta != null) {
                InputStream entryStream = jar.getInputStream(modmeta);
                InputStreamReader streamReader = new InputStreamReader(entryStream, "UTF-8");
                Dependencies dependencies = (Dependencies) new ResolveDSLParser().shell().evaluate(streamReader);
                streamReader.close();
                entryStream.close();
                dependenciesList.add(dependencies);
            }
            jar.close();
        }
    }

    private void resolveParsedArtifacts() {
        dependenciesList.stream()
                .filter(it -> it.getRepositories() != null)
                .flatMap(it -> it.getRepositories().stream())
                .map(this::toRemote)
                .forEach(MavenProvider.instance()::addRepository);
        List<DomainArtifact> artifacts = dependenciesList.stream()
                .filter(it -> it.getDependencies() != null)
                .flatMap(it -> it.getDependencies().stream())
                .collect(Collectors.toList());

        List<DomainArtifact> transitive = artifacts.stream().filter(DomainArtifact::isTransitive).collect(Collectors.toList());
        List<DomainArtifact> nonTransitive = artifacts.stream().filter(it -> !it.isTransitive()).collect(Collectors.toList());

        transitive.parallelStream()
                .map(it -> Pair.of(it, new DefaultArtifact(it.getId())))
                .map(it -> Pair.of(it.getLeft(), MavenProvider.instance().resolveTransitively(it.getRight())))
                .flatMap(it -> it.getValue().stream()
                        .filter(that -> !checkExcludes(it.getKey(), that))
                        .collect(Collectors.toList()).stream())
                .filter(it -> it.getArtifact() != null && it.getArtifact().getFile() != null)
                .map(ArtifactResult::getArtifact)
                .distinct()
                .forEach(this.resolvedArtifacts::add);

        nonTransitive.parallelStream()
                .map(DomainArtifact::getId)
                .map(DefaultArtifact::new)
                .map(MavenProvider.instance()::resolve)
                .filter(it -> it.getArtifact().getFile() != null)
                .map(ArtifactResult::getArtifact)
                .distinct()
                .forEach(this.resolvedArtifacts::add);
    }

    private boolean checkExcludes(DomainArtifact artifactConfig, ArtifactResult resolvedArtifact) {
        String[] excludes = artifactConfig.getExcludes();
        if(excludes == null || resolvedArtifact.getArtifact() == null) return false;
        Artifact resolved = resolvedArtifact.getArtifact();
        return Stream.of(excludes)
                .map(DefaultArtifact::new)
                .anyMatch(it -> it.getGroupId().equalsIgnoreCase(resolved.getGroupId()) &&
                        it.getArtifactId().equalsIgnoreCase(resolved.getArtifactId()) &&
                        it.getClassifier().equalsIgnoreCase(resolved.getClassifier()));
    }

    private void generateNewCache() {
        resolvedArtifacts.stream()
                .map(it -> it.getFile().getName())
                .forEach(resolutionCache.getArtifacts()::add);
    }

    private void parseOldCache() throws IOException {
        File oldCache = new File(deployFolder, "resolution.cache");
        if(!oldCache.exists()) return;
        JsonReader reader = new JsonReader(new FileReader(oldCache));
        this.oldCache = new Gson().fromJson(reader, ResolutionCache.class);
        reader.close();
    }

    private void removeUnusedArtifacts() throws IOException {
        if(oldCache == null) return;

        List<String> unusedArtifacts = new ArrayList<>(oldCache.getArtifacts());
        unusedArtifacts.removeAll(resolutionCache.getArtifacts());
        for(String artifact : unusedArtifacts){
            FileUtils.forceDelete(new File(deployFolder, artifact));
            System.out.println("Removing " + artifact + " from deploy directory which is no longer in use");
        }

    }

    private void deployNewArtifacts() throws IOException {
        for(Artifact artifact : resolvedArtifacts){
            if(!Files.exists(Paths.get(deployFolder))) Files.createDirectory(Paths.get(deployFolder));
            FileUtils.copyFile(artifact.getFile(), new File(deployFolder, artifact.getFile().getName()));
            System.out.println(artifact + " successfully resolved and deployed");
        }
    }

    private void recreateCache() throws IOException {
        if(oldCache != null){
            File oldCacheFile = new File(deployFolder, "resolution.cache");
            if(oldCacheFile.exists()) FileUtils.forceDelete(oldCacheFile);
        }
        File cache = new File(deployFolder, "resolution.cache");
        FileWriter writer = new FileWriter(cache);
        writer.write(new Gson().toJson(resolutionCache));
        writer.close();
    }

    private RemoteRepository toRemote(DomainRepository repository) {
        String name = repository.getName();
        String url = repository.getUrl();
        String login = repository.getLogin();
        String password = repository.getPassword();

        RemoteRepository.Builder remote = new RemoteRepository.Builder(name, "default", url);
        if (login != null || password != null) {
            Authentication authentication = new AuthenticationBuilder()
                    .addUsername(login)
                    .addPassword(password).build();
            remote.setAuthentication(authentication);
        }
        return remote.build();
    }
    public static void main(String[] args) throws IOException, DependencyResolutionException {
        new Resolver().launch();
    }

}
