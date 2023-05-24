package eu.openanalytics.rdepot.crane.config;

import eu.openanalytics.rdepot.crane.RepositoryHostingHandler;
import eu.openanalytics.rdepot.crane.model.Repository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.HttpRequestHandler;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class RepositoryHostingConfig {

    private final CraneConfig config;

    public RepositoryHostingConfig(CraneConfig config) {
        this.config = config;
    }

    @Bean
    public SimpleUrlHandlerMapping handler() {
        Map<String, HttpRequestHandler> urlMap = new LinkedHashMap<>();

        for (Repository repository : config.getRepositories()) {
            Path repositoryRoot = config.getRoot().resolve(repository.getName());
            RepositoryHostingHandler resourceHttpRequestHandler = new RepositoryHostingHandler(repository, repositoryRoot);
            urlMap.put(String.format("/%s/**", repository.getName()), resourceHttpRequestHandler);
        }

        return new SimpleUrlHandlerMapping(urlMap);
    }

}
