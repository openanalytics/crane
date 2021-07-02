package eu.openanalytics.rdepot.crane.config;

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
        registry
            .addResourceHandler("/repo/**")
            .addResourceLocations("file://" + config.getStorageLocation());
    }

}
