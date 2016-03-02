package ru.redenergy.resolver.domain;

import java.util.List;

public class Dependencies {

    private List<DomainRepository> repositories;

    private List<DomainArtifact> dependencies;

    public Dependencies(){}

    public Dependencies(List<DomainRepository> repositories, List<DomainArtifact> dependencies) {
        this.repositories = repositories;
        this.dependencies = dependencies;
    }

    public List<DomainRepository> getRepositories() {
        return repositories;
    }

    public List<DomainArtifact> getDependencies() {
        return dependencies;
    }

    public void setRepositories(List<DomainRepository> repositories) {
        this.repositories = repositories;
    }

    public void setDependencies(List<DomainArtifact> dependencies) {
        this.dependencies = dependencies;
    }
}
