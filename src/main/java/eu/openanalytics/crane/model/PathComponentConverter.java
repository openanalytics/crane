package eu.openanalytics.crane.model;

import eu.openanalytics.crane.model.config.PathComponent;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@ConfigurationPropertiesBinding
public class PathComponentConverter implements Converter<String, PathComponent> {
    @Override
    public PathComponent convert(@NotNull String source) {
        return new PathComponent();
    }
}
