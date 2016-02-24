package ru.redenergy.resolver.config;

import java.util.List;

public class Dependencies {

    private List<Repository> repositories;

    private List<String> dependencies;

    public Dependencies(List<Repository> repositories, List<String> dependencies) {
        this.repositories = repositories;
        this.dependencies = dependencies;
    }

    public List<Repository> getRepositories() {
        return repositories;
    }

    public List<String> getDependencies() {
        return dependencies;
    }

    public static class Repository {
        private String name;
        private String url;

        private String login;
        private String password;

        public Repository(String name, String url, String login, String password) {
            this.name = name;
            this.url = url;
            this.login = login;
            this.password = password;
        }

        public String getName() {
            return name;
        }

        public String getUrl() {
            return url;
        }

        public String getLogin() {
            return login;
        }

        public String getPassword() {
            return password;
        }
    }
}
