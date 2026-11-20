/*
 * Crane
 *
 * Copyright (C) 2021-2026 Open Analytics
 *
 * ===========================================================================
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the Apache License as published by
 * The Apache Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * Apache License for more details.
 *
 * You should have received a copy of the Apache License
 * along with this program.  If not, see <http://www.apache.org/licenses/>
 */
package eu.openanalytics.crane;

import eu.openanalytics.crane.service.LanguageService;
import io.undertow.UndertowOptions;
import io.undertow.server.handlers.SameSiteCookieHandler;
import io.undertow.servlet.api.ServletSessionConfig;
import io.undertow.springboot.servlet.UndertowServletWebServerFactory;
import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.audit.AuditAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@EnableScheduling
@EnableAsync
@EnableMethodSecurity
@SpringBootApplication(exclude = {DataRedisAutoConfiguration.class, AuditAutoConfiguration.class})
public class CraneApplication {

    public static final String CONFIG_FILENAME = "application.yml";
    private static final Path TERMINATION_LOG_FILE = Path.of("/dev/termination-log");
    private static final String PROP_PROXY_SAME_SITE_COOKIE = "app.same-site-cookie";
    private static final String SAME_SITE_COOKIE_DEFAULT_VALUE = "Lax";
    private static final String PROP_SERVER_SECURE_COOKIES = "app.secure-cookies";
    private static final Boolean SECURE_COOKIES_DEFAULT_VALUE = false;
    private static final String PROP_FORCE_HTTPS_IN_REDIRECTS = "app.force-https-in-redirects";
    private static final Boolean FORCE_HTTPS_IN_REDIRECTS_DEFAULT_VALUE = false;
    private final Logger logger = LoggerFactory.getLogger(getClass());

    public static Boolean secureCookiesEnabled;
    public static String sameSiteCookiePolicy;

    @Autowired
    private Environment environment;

    @Autowired
    private DefaultCookieSerializer defaultCookieSerializer;

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(CraneApplication.class);
        app.setDefaultProperties(getDefaultProperties());
        try {
            app.run(args);
        } catch (Throwable t) {
            handleError(t);
        }
    }

    public static Properties getDefaultProperties() {
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

        // use in-memory session storage by default. Can be overwritten in application.yml
        properties.put("spring.session.store-type", "none");

        // hide "Started CraneApplication in ..." since we already log the version number
        properties.put("spring.main.log-startup-info", "false");

        // https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html
        properties.put("server.shutdown", "graceful");
        properties.put("spring.lifecycle.timeout-per-shutdown-phase", "60s");

        // disable spring multipart parsing
        properties.put("spring.servlet.multipart.enabled", false);

        // disable matomo by default
        properties.put("matomo.tracker.enabled", false);
        properties.put("matomo.tracker.filter.enabled", false);

        return properties;
    }

    private static void handleError(Throwable t) {
        Throwable cause = ExceptionUtils.getRootCause(t);
        if (cause == null) {
            cause = t;
        }
        String message = "Crane crashed! Exception: '" + cause.getClass().getName() + "', message: '" + cause.getMessage() + "'";
        if (Files.exists(TERMINATION_LOG_FILE) && Files.isRegularFile(TERMINATION_LOG_FILE)) {
            try {
                FileWriter fileWriter = new FileWriter(TERMINATION_LOG_FILE.toString());
                PrintWriter printWriter = new PrintWriter(fileWriter);

                printWriter.print(message);
                printWriter.close();
            } catch (Throwable ioException) {
                System.out.println("Error while writing termination log");
                ioException.printStackTrace();
            }
        }
        System.out.println();
        System.out.println(message);
        System.out.println();

        System.exit(1);
    }

    @PostConstruct
    public void init() {

        sameSiteCookiePolicy = environment.getProperty(PROP_PROXY_SAME_SITE_COOKIE, SAME_SITE_COOKIE_DEFAULT_VALUE);
        secureCookiesEnabled = environment.getProperty(PROP_SERVER_SECURE_COOKIES, Boolean.class, SECURE_COOKIES_DEFAULT_VALUE);

        logger.debug("Setting sameSiteCookie policy to {}", sameSiteCookiePolicy);
        defaultCookieSerializer.setSameSite(sameSiteCookiePolicy);
        defaultCookieSerializer.setUseSecureCookie(secureCookiesEnabled);

        if (sameSiteCookiePolicy.equalsIgnoreCase("none") && !secureCookiesEnabled) {
            logger.warn("WARNING: Invalid configuration detected: same-site-cookie policy is set to None, but secure-cookies are not enabled. Secure cookies must be enabled when using None as same-site-cookie policy ");
        }

    }

    @Bean
    public UndertowServletWebServerFactory servletContainer() {
        UndertowServletWebServerFactory factory = new UndertowServletWebServerFactory();
        factory.addDeploymentInfoCustomizers(info -> {
            logger.debug("Setting sameSiteCookie policy for session cookies to {}", sameSiteCookiePolicy);
            info.addOuterHandlerChainWrapper(defaultHandler -> new SameSiteCookieHandler(defaultHandler, sameSiteCookiePolicy, null, true, true, false));

            ServletSessionConfig sessionConfig = new ServletSessionConfig();
            sessionConfig.setHttpOnly(true);
            sessionConfig.setSecure(secureCookiesEnabled);
            info.setServletSessionConfig(sessionConfig);
            if (environment.getProperty(PROP_FORCE_HTTPS_IN_REDIRECTS, Boolean.class, FORCE_HTTPS_IN_REDIRECTS_DEFAULT_VALUE)) {
                logger.debug("Enforcing redirects to always use https.");
                info.addInitialHandlerChainWrapper(handler ->
                    exchange -> {
                        exchange.setRequestScheme("https");
                        handler.handleRequest(exchange);
                    }
                );
            }
        });
        factory.addBuilderCustomizers(builder -> {
            // allow uploads of unlimited size
            builder.setServerOption(UndertowOptions.MAX_ENTITY_SIZE, -1L);
            // limit parsing of multipart requests to 2MB (see #36124), multipart requests are not used in Crane
            builder.setServerOption(UndertowOptions.MULTIPART_MAX_ENTITY_SIZE, 2097152L);
        });
        return factory;
    }

    @Bean
    public LocaleResolver localeResolver(LanguageService languageService) {
        // no default language is configured, therefore the Accept header will determine the language if there is no cookie
        CookieLocaleResolver cookieLocaleResolver = new CookieLocaleResolver(languageService.getLanguageCookieName());
        cookieLocaleResolver.setCookieSecure(secureCookiesEnabled);
        cookieLocaleResolver.setCookieSameSite(sameSiteCookiePolicy);
        cookieLocaleResolver.setCookieHttpOnly(true);
        return cookieLocaleResolver;
    }

}
