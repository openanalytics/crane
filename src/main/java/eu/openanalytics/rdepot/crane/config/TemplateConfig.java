package eu.openanalytics.rdepot.crane.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.thymeleaf.templateresolver.FileTemplateResolver;

@Configuration
public class TemplateConfig {

    @Bean
    public FileTemplateResolver templateResolver(CraneConfig craneConfig) {
        FileTemplateResolver resolver = new FileTemplateResolver();
        resolver.setPrefix(craneConfig.getTemplatePath());
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML5");
        resolver.setCacheable(false);
        resolver.setCheckExistence(true);
        resolver.setOrder(1);
        return resolver;
    }

}
