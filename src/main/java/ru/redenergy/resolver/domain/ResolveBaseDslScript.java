package ru.redenergy.resolver.domain;

import groovy.lang.Closure;
import groovy.lang.Script;
import org.apache.commons.beanutils.BeanUtils;

import java.util.ArrayList;
import java.util.List;

public abstract class ResolveBaseDslScript extends Script {

    Dependencies dependencies = new Dependencies();
    Object activeObject = null;
    List<Repository> repositoryList = new ArrayList<>();
    Repository repository = new Repository();
    List<Dependencies.Artifact> artifactsList = new ArrayList<>();
    Dependencies.Artifact artifact = new Dependencies.Artifact();

    public List<Repository> repositories(Closure<List<Repository>> closure){
        activeObject = repositoryList;
        closure.call();
        return repositoryList;
    }

    public List<Dependencies.Artifact> dependencies(Closure<List<Dependencies.Artifact>> closure){
        activeObject = artifactsList;
        closure.call();
        return artifactsList;
    }

    public Repository maven(Closure<Repository> closure){
        repository = new Repository();
        activeObject = repository;
        repositoryList.add(closure.call());
        return repository;
    }

    public Dependencies.Artifact artifact(Closure<Dependencies.Artifact> closure){
        artifact = new Dependencies.Artifact();
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
