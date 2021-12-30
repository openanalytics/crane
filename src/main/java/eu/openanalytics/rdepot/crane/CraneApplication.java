package eu.openanalytics.rdepot.crane;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.Map;
import java.util.Properties;

@SpringBootApplication
public class CraneApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CraneApplication.class);
        app.setDefaultProperties(getDefaultProperties());
        app.run(args);
    }

    @EventListener
    public void handleContextRefresh(ContextRefreshedEvent event) {
        ApplicationContext applicationContext = event.getApplicationContext();
        RequestMappingHandlerMapping requestMappingHandlerMapping = applicationContext
            .getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        Map<RequestMappingInfo, HandlerMethod> map = requestMappingHandlerMapping
            .getHandlerMethods();
        map.forEach((key, value) -> System.out.printf("%s, %s \n", key, value));
    }


    private static Properties getDefaultProperties() {
        Properties properties = new Properties();

        // Health configuration
        // ====================
        
        properties.put("management.server.port", "9090");

        // disable ldap health endpoint
        properties.put("management.health.ldap.enabled", false);
        // disable default redis health endpoint since it's managed by redisSession
        properties.put("management.health.redis.enabled", false);
        // enable Kubernetes probes
        properties.put("management.endpoint.health.probes.enabled", true);

        // ====================

        return properties;
    }


}
