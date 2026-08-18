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
package eu.openanalytics.crane.config;

import eu.openanalytics.crane.service.LanguageService;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "app")
public class LanguageConfig {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private static final Map<String, LanguageService.Language> DEFAULT_AVAILABLE_LANGUAGES = Map.of(
        "en", new LanguageService.Language("en", "English"),
        "nl", new LanguageService.Language("nl", "Dutch"),
        "fr", new LanguageService.Language("fr", "French"),
        "es", new LanguageService.Language("es", "Spanish")
    );

    private static final String DEFAULT_FALLBACK_LANGUAGE = "en";
    private static final String DEFAULT_LANGUAGE_COOKIE_NAME = "crane_language";

    @Getter
    private String availableLanguages;

    @Setter
    @Getter
    private String fallbackLanguage = DEFAULT_FALLBACK_LANGUAGE;

    @Setter
    @Getter
    private String languageCookieName = DEFAULT_LANGUAGE_COOKIE_NAME;

    @Getter
    private Map<String, LanguageService.Language> enabledLanguages = DEFAULT_AVAILABLE_LANGUAGES;

    public void setAvailableLanguages(List<String> availableLanguages) {
        if (availableLanguages == null) {
            return;
        }
        enabledLanguages = new HashMap<>();
        for (String key : availableLanguages) {
            LanguageService.Language lang = DEFAULT_AVAILABLE_LANGUAGES.get(key);
            if (lang != null) {
                enabledLanguages.put(lang.key(), lang);
            } else {
                logger.warn("Enabled language '{}' is not supported", key);
            }
        }
    }

}
