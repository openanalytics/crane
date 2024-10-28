package eu.openanalytics.crane.model;

import eu.openanalytics.crane.model.config.PathComponent;
import eu.openanalytics.crane.model.config.Repository;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@ConfigurationPropertiesBinding
public class RepositoryConverter implements Converter<String, Repository> {
    @Override
    public Repository convert(@NotNull String source) {
        return new Repository();
    }
}
