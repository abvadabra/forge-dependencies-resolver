package ru.redenergy.resolver;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.apache.commons.io.FileUtils;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import ru.redenergy.resolver.config.Dependencies;
import ru.redenergy.resolver.config.ResolutionCache;
import ru.redenergy.resolver.maven.MavenProvider;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class Resolver {

    private static RemoteRepository cental = new RemoteRepository.Builder("central", "default", "http://repo1.maven.org/maven2/").build();

    private static final String mainConfigFile = System.getProperty("mainConfig", "dependencies.json");
    private static final String deployFolder = System.getProperty("deployPath", "mods/");

    private ResolutionCache oldCache;
    private ResolutionCache resolutionCache = new ResolutionCache();
    private List<Dependencies> dependenciesList = new ArrayList<>();
    private List<Artifact> resolvedArtifacts = new ArrayList<>();

    public void launch() throws IOException, DependencyResolutionException {
        MavenProvider.instance().addRepository(cental);
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
        JsonReader reader = new JsonReader(new FileReader(new File(mainConfigFile)));
        Dependencies mainConfig = new Gson().fromJson(reader, Dependencies.class);
        dependenciesList.add(mainConfig);
        reader.close();
    }

    private void parseSubmodules() throws IOException {
        if(!Files.exists(Paths.get(deployFolder))) Files.createDirectory(Paths.get(deployFolder));
        for(File module : new File(deployFolder).listFiles()){
            if(module.isFile() && module.getName().endsWith(".zip") || module.getName().endsWith(".jar")){
                JarFile jar = new JarFile(module);
                JarEntry modmeta = jar.getJarEntry("mcmod.info");
                if(modmeta != null) {
                    InputStream entryStream = jar.getInputStream(modmeta);
                    JsonReader reader = new JsonReader(new InputStreamReader(entryStream, "UTF-8"));
                    List<Dependencies> dependencies = parseModMeta(reader);
                    entryStream.close();
                    reader.close();
                    dependenciesList.addAll(dependencies);
                }
                jar.close();
            }
        }
    }

    private void resolveParsedArtifacts() {
        dependenciesList.stream()
                .filter(it -> it.getRepositories() != null)
                .flatMap(it -> it.getRepositories().stream())
                .map(this::toRemote)
                .forEach(MavenProvider.instance()::addRepository);
        dependenciesList.parallelStream() //parallel because resolving transitively is heavy and long running task
                .filter(it -> it.getDependencies() != null)
                .flatMap(it -> it.getDependencies().stream())
                .map(DefaultArtifact::new)
                .flatMap(it -> MavenProvider.instance().resolveTransitively(it).stream())
                .filter(it -> it.getArtifact() != null && it.getArtifact().getFile() != null)
                .map(ArtifactResult::getArtifact)
                .distinct()
                .forEach(this.resolvedArtifacts::add);
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
            if(oldCacheFile.exists()) oldCacheFile.delete();
        }
        File cache = new File(deployFolder, "resolution.cache");
        FileWriter writer = new FileWriter(cache);
        writer.write(new Gson().toJson(resolutionCache));
        writer.close();
    }

    private List<Dependencies> parseModMeta(JsonReader reader){
        JsonElement json = new JsonParser().parse(reader);
        Gson gson = new Gson();
        return StreamSupport.stream(json.getAsJsonArray().spliterator(), false)
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .filter(it -> it.has("maven"))
                .map(it -> it.getAsJsonObject("maven"))
                .map(it -> gson.fromJson(it, Dependencies.class))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }


    private RemoteRepository toRemote(Dependencies.Repository repository) {
        String name = extractPossibleEnv(repository.getName());
        String url = extractPossibleEnv(repository.getUrl());
        String login = extractPossibleEnv(repository.getLogin());
        String password = extractPossibleEnv(repository.getPassword());

        RemoteRepository.Builder remote = new RemoteRepository.Builder(name, "default", url);
        if (login != null || password != null) {
            Authentication authentication = new AuthenticationBuilder()
                    .addUsername(login)
                    .addPassword(password).build();
            remote.setAuthentication(authentication);
        }
        return remote.build();
    }

    private String extractPossibleEnv(String value) {
        if (value == null) return null;
        boolean envVar = value.matches("\\$\\{.*\\}"); //if matches ${value}
        if (envVar) {
            String varName = value.substring(2, value.length() - 1);
            return System.getenv(varName);
        }
        return value;
    }

    public static void main(String[] args) throws IOException, DependencyResolutionException {
        new Resolver().launch();
    }

}
