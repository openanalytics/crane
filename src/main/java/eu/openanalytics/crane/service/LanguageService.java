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
package eu.openanalytics.crane.service;

import eu.openanalytics.crane.config.LanguageConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.Locale;
import java.util.Map;


@Service
public class LanguageService {

    private final String fallbackLanguage;

    @Getter
    private final Map<String, Language> enabledLanguages;

    @Getter
    private final String languageCookieName;

    public LanguageService(LanguageConfig languageConfig) {
        enabledLanguages = languageConfig.getEnabledLanguages();
        fallbackLanguage = languageConfig.getFallbackLanguage();
        languageCookieName = languageConfig.getLanguageCookieName();
    }

    public void updateLanguageOfUser(HttpServletRequest request, HttpServletResponse response, String language) {
        if (StringUtils.isBlank(language)) {
            throw new IllegalArgumentException("No language provided");
        }

        if (!enabledLanguages.containsKey(language)) {
            throw new IllegalArgumentException("Requested language not enabled");
        }

        LocaleResolver localeResolver = RequestContextUtils.getLocaleResolver(request);
        if (localeResolver == null) {
            throw new IllegalStateException("Unable to change language");
        }

        localeResolver.setLocale(request, response, Locale.forLanguageTag(language));
    }

    public String getAndUpdateLanguageOfUser(HttpServletRequest request, HttpServletResponse response) {
        String language = LocaleContextHolder.getLocale().getLanguage();
        if (enabledLanguages.containsKey(language)) {
            return language;
        }
        // language of user is not supported -> use fallback language
        updateLanguageOfUser(request, response, fallbackLanguage);
        return fallbackLanguage;
    }

    public record Language(String key, String displayName) {

    }

}
