package eu.openanalytics.rdepot.crane.config;

import eu.openanalytics.rdepot.crane.model.Repository;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebMvc
public class WebMvc implements WebMvcConfigurer {

    private final CraneConfig config;

    public WebMvc(CraneConfig config) {
        this.config = config;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // TODO name may not contain / or .
        for (Repository repository : config.getRepositories()) {
            registry
                .addResourceHandler(String.format("/repo/%s/**", repository.getName()))
                .addResourceLocations(String.format("file://%s/%s/", config.getStorageLocation(), repository.getName()));
        }
    }

}
