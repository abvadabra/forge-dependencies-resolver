package ru.redenergy.resolver.domain;

import groovy.lang.Closure;
import groovy.lang.Script;
import org.apache.commons.beanutils.BeanUtils;

import java.util.ArrayList;
import java.util.List;

public abstract class ResolveBaseDslScript extends Script {

    Dependencies dependencies = new Dependencies();
    Object activeObject = null;
    List<DomainRepository> repositoryList = new ArrayList<>();
    DomainRepository repository = new DomainRepository();
    List<DomainArtifact> artifactsList = new ArrayList<>();
    DomainArtifact artifact = new DomainArtifact();

    public List<DomainRepository> repositories(Closure<List<DomainRepository>> closure){
        activeObject = repositoryList;
        closure.call();
        return repositoryList;
    }

    public List<DomainArtifact> dependencies(Closure<List<DomainArtifact>> closure){
        activeObject = artifactsList;
        closure.call();
        return artifactsList;
    }

    public DomainRepository maven(Closure<DomainRepository> closure){
        repository = new DomainRepository();
        activeObject = repository;
        repositoryList.add(closure.call());
        return repository;
    }

    public DomainArtifact artifact(Closure<DomainArtifact> closure){
        artifact = new DomainArtifact();
        activeObject = artifact;
        artifactsList.add(closure.call());
        return artifact;
    }



    public Dependencies resolve(Closure<Dependencies> closure){
        closure.call();
        dependencies.setRepositories(repositoryList);
        dependencies.setDependencies(artifactsList);
        return dependencies;
    }

    @Override
    public Object invokeMethod(final String name, final Object arg) {
        if(arg instanceof Object[]) {
            Object[] paramArray = (Object[])arg;
            if(paramArray.length == 1) {
                final Object param = paramArray[0];
                try {
                    BeanUtils.setProperty(this.activeObject, name, param);
                } catch (Exception e) {
                    throw new IllegalArgumentException(e);
                }
                return activeObject;
            }
        }

        return super.invokeMethod(name, arg);
    }
}
