/**
 * Crane
 *
 * Copyright (C) 2021-2024 Open Analytics
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

import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.autoconfigure.audit.AuditAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

@EnableAsync
@EnableWebMvc
@SpringBootApplication(exclude = {RedisAutoConfiguration.class, AuditAutoConfiguration.class})
public class CraneApplication {

    private static final Path TERMINATION_LOG_FILE = Path.of("/dev/termination-log");

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

        // use in-memory session storage by default. Can be overwritten in application.yml
        properties.put("spring.session.store-type", "none");

        // hide "Started CraneApplication in ..." since we already log the version number
        properties.put("spring.main.log-startup-info", "false");

        // https://docs.spring.io/spring-boot/reference/web/graceful-shutdown.html
        properties.put("server.shutdown", "graceful");
        properties.put("spring.lifecycle.timeout-per-shutdown-phase", "60s");
        // ====================

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

}
