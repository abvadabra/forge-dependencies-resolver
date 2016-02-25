package ru.redenergy.resolver;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.Authentication;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.util.repository.AuthenticationBuilder;
import ru.redenergy.resolver.config.Dependencies;
import ru.redenergy.resolver.maven.MavenProvider;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Resolver {

    private static final String mainConfigFile = System.getProperty("mainConfig", "dependencies.json");
    private static final String deployFolder = System.getProperty("deployPath", "mods/");

    private Dependencies mainConfig;

    public void launch() throws IOException, DependencyResolutionException {
        loadMainConfig(new File(mainConfigFile));
        processDependencies(mainConfig);
        loadModsDeps(new File(deployFolder));
    }

    private void loadMainConfig(File file) throws FileNotFoundException {
        mainConfig = new Gson().fromJson(new JsonReader(new FileReader(file)), Dependencies.class);
    }

    private void processDependencies(Dependencies dependencies) throws DependencyResolutionException, IOException {
        if(dependencies.getRepositories() != null) {
            for (Dependencies.Repository repository : dependencies.getRepositories()) {
                MavenProvider.instance().addRepository(toRemote(repository));
            }
        }

        if(dependencies.getDependencies() != null) {
            for (String art : dependencies.getDependencies()) {
                Artifact artifact = new DefaultArtifact(extractPossibleEnv(art));
                List<ArtifactResult> results = MavenProvider.instance().resolveTransitively(artifact);
                deployArtifacts(results);
            }
        }
    }

    private void deployArtifacts(List<ArtifactResult> artifacts) throws IOException {
        Paths.get(deployFolder).toFile().mkdir();
        List<ArtifactResult> deployable = artifacts.stream().filter(it -> it.getArtifact().getFile() != null && it.getArtifact().getFile().exists()).collect(Collectors.toList());
        for(ArtifactResult result : deployable){
            Path from = result.getArtifact().getFile().toPath();
            Path to = Paths.get(deployFolder).resolve(result.getArtifact().getFile().getName());
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
            System.out.println(result.getArtifact() + " resolved");
        }
    }


    private void loadModsDeps(File modsFolder) throws IOException, DependencyResolutionException {
        List<Dependencies> dependenciesList = new ArrayList<>();
        for(File file : modsFolder.listFiles()){
            if(file.isFile()){
                ZipFile zip = new ZipFile(file);
                ZipEntry entry = zip.getEntry("mcmod.info");
                if(entry == null) continue;
                BufferedReader reader = new BufferedReader(new InputStreamReader(zip.getInputStream(entry), "UTF-8"));
                Dependencies dependencies = parseDependenciesMeta(new JsonReader(reader));
                if(dependencies != null) dependenciesList.add(dependencies);
            }
        }
        for(Dependencies dependencies: dependenciesList) processDependencies(dependencies);
    }

    private Dependencies parseDependenciesMeta(JsonReader reader){
        JsonElement json = new JsonParser().parse(reader);
        for(JsonElement jsonElement : json.getAsJsonArray()){
            JsonElement maven = jsonElement.getAsJsonObject().getAsJsonObject("maven");
            if(maven != null) {
                return new Gson().fromJson(maven, Dependencies.class);
            }
        }
        return null;
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
