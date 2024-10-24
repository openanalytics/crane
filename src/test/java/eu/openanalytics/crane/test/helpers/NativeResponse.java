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

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class NativeResponse {
    private final HttpResponse<String> response;
    private final HttpRequest request;

    public NativeResponse(HttpResponse<String> response, HttpRequest request) {
        this.response = response;
        this.request = request;
    }

    public int code() {
        return response.statusCode();
    }

    public void assertBadRequest() {
        Assertions.assertEquals(400, code(), String.format("In request: %s %s", request.method(), request.uri()));
    }

    public void assertNotFound() {
        Assertions.assertEquals(404, code(), String.format("In request: %s %s", request.method(), request.uri()));
    }

    public void assertUnauthorizedRedirectToLogIn() {
        Assertions.assertEquals(302, code());
        Assertions.assertTrue(response.headers().firstValue("location").isPresent());
        Assertions.assertTrue(response.headers().firstValue("location").get().endsWith("/oauth2/authorization/crane"));
        Assertions.assertEquals(response.body(), "");
    }

    public void assertUnauthorized() {
        Assertions.assertEquals(401, code());
    }

}
