package ru.redenergy.resolver.config;

import java.util.ArrayList;
import java.util.List;

public class ResolutionCache {
    private List<String> artifacts;

    public ResolutionCache() {
        this.artifacts = new ArrayList<>();
    }

    public List<String> getArtifacts() {
        return artifacts;
    }
}
