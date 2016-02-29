package ru.redenergy.resolver.domain;

import ru.redenergy.resolver.domain.Repository;

import java.util.List;

public class Dependencies {

    private List<Repository> repositories;

    private List<Artifact> dependencies;

    public Dependencies(){}

    public Dependencies(List<Repository> repositories, List<Artifact> dependencies) {
        this.repositories = repositories;
        this.dependencies = dependencies;
    }

    public List<Repository> getRepositories() {
        return repositories;
    }

    public List<Artifact> getDependencies() {
        return dependencies;
    }

    public void setRepositories(List<Repository> repositories) {
        this.repositories = repositories;
    }

    public void setDependencies(List<Artifact> dependencies) {
        this.dependencies = dependencies;
    }

    public static class Artifact {
        private String id;
        private String[] exclude;
        private boolean transitive;

        public String getId() {
            return id;
        }

        public String[] getExcludes() {
            return exclude;
        }

        public boolean isTransitive() {
            return transitive;
        }

        public void setId(String id) {
            this.id = id;
        }

        public void setExclude(String[] exclude) {
            this.exclude = exclude;
        }

        public void setTransitive(boolean transitive) {
            this.transitive = transitive;
        }
    }
}
