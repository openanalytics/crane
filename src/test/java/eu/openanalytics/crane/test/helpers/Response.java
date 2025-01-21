/**
 * Crane
 *
 * Copyright (C) 2021-2025 Open Analytics
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

import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Assertions;

import java.io.IOException;

public class Response {
    private final okhttp3.Response response;

    private String body;

    public Response(okhttp3.Response response) {
        this.response = response;
    }

    private String path() {
        return response.request().url().toString();
    }

    private String method() {
        return response.request().method();
    }

    private String user() {
        String user = response.request().header("user");
        if (user == null || user.isEmpty()) {
            return "unauthenticated";
        }
        return user;
    }

    public void assertSuccess() {
        checkResponseCode(200, code());
        if (response.priorResponse() != null && !response.priorResponse().request().url().toString().contains("/login")) {
            String url = response.request().url().toString();
            String priorUrl = response.priorResponse().request().url().toString();
            Assertions.assertEquals(url.substring(0, url.length() - 1), priorUrl.replaceFirst("/__file", ""));
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
        checkResponseCode(302, priorCode());
        assertContentTypeWithCode(200, "text/html");
    }

    private void checkResponseCode(int expectedResponseCode, int actualResponseCode) {
        String message = String.format("Unexpected response code %d expected %d from request %s %s made by user %s", actualResponseCode, expectedResponseCode, method(), path(), user());
        Assertions.assertEquals(expectedResponseCode, actualResponseCode, message);
    }

    private int priorCode() {
        Assertions.assertNotNull(response.priorResponse());
        return response.priorResponse().code();
    }

    public void assertNotFound() {
        checkResponseCode(404, code());
    }

    public void assertUnauthorized() {
        checkResponseCode(401, code());
    }

    private void assertContentTypeWithCode(int expected, String contentType) {
        checkResponseCode(expected, code());
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
        checkResponseCode(200, code());
        checkResponseCode(302, priorCode());
        Assertions.assertEquals(response.request().url().port(), response.priorResponse().request().url().port());
    }

    public void assertForbidden() {
        checkResponseCode(403, code());
    }

    public void setCookies(Request.Builder builder) {
        String cookies = response.header("Set-Cookie");
        if (cookies!= null) {
            builder.addHeader("Cookie", cookies);
        }
    }
}
