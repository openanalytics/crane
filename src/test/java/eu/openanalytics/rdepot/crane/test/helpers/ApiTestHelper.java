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
package eu.openanalytics.rdepot.crane.test.helpers;

import okhttp3.*;

import java.io.IOException;
import java.time.Duration;

public class ApiTestHelper {

    private final String baseUrl;
    private final OkHttpClient clientDemo;
    private final OkHttpClient clientTest;
    private final OkHttpClient clientWithoutAuth;

    public ApiTestHelper(CraneInstance inst) {
        this.baseUrl = inst.client.getBaseUrl();
        clientWithoutAuth = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(120))
            .readTimeout(Duration.ofSeconds(120))
            .build();
        clientDemo = new OkHttpClient.Builder()
            .addInterceptor(new KeycloakAuthInterceptor("demo", "demo"))
            .callTimeout(Duration.ofSeconds(120))
            .readTimeout(Duration.ofSeconds(120))
            .build();
        clientTest = new OkHttpClient.Builder()
            .addInterceptor(new KeycloakAuthInterceptor("test", "test"))
            .callTimeout(Duration.ofSeconds(120))
            .readTimeout(Duration.ofSeconds(120))
            .build();
    }

    public Request.Builder createHtmlRequest(String path) {
        return new Request.Builder()
            .url(baseUrl + path).addHeader("Accept", "text/html").addHeader("remote-address", "11.11.11.11");
    }

    public eu.openanalytics.rdepot.crane.test.helpers.Response callWithAuth(Request.Builder request) {
        try {
            return new eu.openanalytics.rdepot.crane.test.helpers.Response(clientDemo.newCall(request.build()).execute());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public eu.openanalytics.rdepot.crane.test.helpers.Response callWithAuthTestUser(Request.Builder request) {
        try {
            return new eu.openanalytics.rdepot.crane.test.helpers.Response(clientTest.newCall(request.build()).execute());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public eu.openanalytics.rdepot.crane.test.helpers.Response callWithoutAuth(Request.Builder request) {
        try {
            return new Response(clientWithoutAuth.newCall(request.build()).execute());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
