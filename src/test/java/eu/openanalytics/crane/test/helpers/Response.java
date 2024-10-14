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
package eu.openanalytics.crane.test.helpers;

import org.junit.jupiter.api.Assertions;
import org.thymeleaf.util.StringUtils;

import java.io.IOException;

public class Response {
    private final okhttp3.Response response;

    private String body;

    public Response(okhttp3.Response response) {
        this.response = response;
    }


    public void assertSuccess() {
        Assertions.assertEquals(200, code());
        if (response.priorResponse() != null && !response.priorResponse().request().url().toString().contains("/login")) {
            String url = response.request().url().toString();
            String priorUrl = response.priorResponse().request().url().toString();
            Assertions.assertEquals(url.substring(0, url.length()-1), priorUrl.replaceFirst("/__file", ""));
        }
    }

    public void assertPlainSuccess() {
        assertContentTypeWithCode(200, "text/plain");
    }

    public void assertCsvSuccess() {
        assertContentTypeWithCode(200, "text/csv");
    }

    public void assertJsonSuccess() {
        assertContentTypeWithCode(200, "application/json");
    }

    public void assertHtmlSuccess() {
        assertContentTypeWithCode(200, "text/html");
    }

    public void assertUnauthorizedRedirectToLogIn() {
        Assertions.assertEquals(302, priorCode());
        assertContentTypeWithCode(200, "text/html");
    }


    private int priorCode() {
        Assertions.assertNotNull(response.priorResponse());
        return response.priorResponse().code();
    }

    public void assertNotFound() {
        Assertions.assertEquals(404, code());
    }

    public void assertUnauthorized() {
        Assertions.assertEquals(401, code());
    }

    private void assertContentTypeWithCode(int expected, String contentType) {
        Assertions.assertEquals(expected, code());
        Assertions.assertTrue(response.header("Content-Type", "").startsWith(contentType));
        Assertions.assertNotNull(body());
    }

    public String body() {
        if (body == null) {
            try {
                if (response.body() == null) {
                    throw new RuntimeException("Body is null");
                }
                body = response.body().string();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return body;
    }

    public int code() {
        return response.code();
    }

    public void assertMissingNoCachingHeader() {
        Assertions.assertFalse(response.cacheControl().noCache());
    }

    public void assertMaxAgeInSeconds(int expectedSeconds) {
        Assertions.assertEquals(expectedSeconds, response.cacheControl().maxAgeSeconds());
    }

    public void assertHasNoCachingHeader() {
        Assertions.assertTrue(response.cacheControl().noCache());
    }

    public void assertRedirectedTo(String redirectTo) {
        Assertions.assertEquals(redirectTo, "/" + StringUtils.join(response.request().url().pathSegments(), "/"));
    }

    public void assertBadRequest() {
        Assertions.assertEquals(400, code());
    }

    public void assertSuccessWithRedirect() {
        Assertions.assertEquals(200, code());
        Assertions.assertEquals(302, priorCode());
        Assertions.assertEquals(response.request().url().port(), response.priorResponse().request().url().port());
    }

    public void assertForbidden() {
        Assertions.assertEquals(403, code());
    }
}
