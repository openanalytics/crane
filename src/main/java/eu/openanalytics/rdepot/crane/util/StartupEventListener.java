package eu.openanalytics.rdepot.crane.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

@Component
public class StartupEventListener {
    private final Logger LOGGER = LoggerFactory.getLogger(StartupEventListener.class);

    @Inject
    private BuildProperties buildProperties;

    @EventListener
    public void onStartup(ApplicationReadyEvent event) {
        String startupMsg = String.format(
            "Started %s %s",
            buildProperties.getName(), buildProperties.getVersion()
        );
        LOGGER.info(startupMsg);
    }
}
