package ru.redenergy.resolver.domain;

public class DomainArtifact {
    private String id = "";
    private String[] exclude = {};
    private boolean transitive = true;

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
