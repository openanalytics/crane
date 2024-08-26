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

import eu.openanalytics.crane.test.helpers.auth.KeycloakAuthOidcInterceptor;
import eu.openanalytics.crane.test.helpers.auth.KeycloakAuthTokenInterceptor;
import okhttp3.CookieJar;
import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import java.io.IOException;
import java.net.CookieManager;
import java.time.Duration;

public class ApiTestHelper {

    private final String baseUrl;
    private final OkHttpClient clientTokenDemo;
    private final OkHttpClient clientTokenTest;
    private final OkHttpClient clientOidcDemo;
    private final OkHttpClient clientOidcTest;
    private final OkHttpClient clientWithoutAuth;

    private ApiTestHelper(String baseUrl) {
        if (!(baseUrl.startsWith("http://") || baseUrl.startsWith("https://"))) {
            throw new TestHelperException(String.format("The passed url '%s' does not start with 'http://' or 'https://'", baseUrl));
        }
        this.baseUrl = baseUrl;
        clientWithoutAuth = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(120))
                .build();
        clientTokenDemo = new OkHttpClient.Builder()
                .addInterceptor(new KeycloakAuthTokenInterceptor("demo", "demo"))
                .callTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(120))
                .build();
        clientTokenTest = new OkHttpClient.Builder()
                .addInterceptor(new KeycloakAuthTokenInterceptor("test", "test"))
                .callTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(120))
                .build();
        CookieJar demoCookieJar = new JavaNetCookieJar(new CookieManager());
        clientOidcDemo = new OkHttpClient.Builder()
                .cookieJar(demoCookieJar)
                .addInterceptor(new KeycloakAuthOidcInterceptor("demo", "demo"))
                .callTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(120))
                .build();
        CookieJar testCookieJar = new JavaNetCookieJar(new CookieManager());
        clientOidcTest = new OkHttpClient.Builder()
                .cookieJar(testCookieJar)
                .addInterceptor(new KeycloakAuthOidcInterceptor("test", "test"))
                .callTimeout(Duration.ofSeconds(120))
                .readTimeout(Duration.ofSeconds(120))
                .build();
    }

    public static ApiTestHelper from(CraneInstance inst) {
        return new ApiTestHelper(inst.client.getBaseUrl());
    }

    public static ApiTestHelper from(String baseUrl) {
        return new ApiTestHelper(baseUrl);
    }

    public Request.Builder createHtmlRequest(String path) {
        return new Request.Builder()
                .url(baseUrl + path).addHeader("Accept", "text/html");
    }

    public Response callWithTokenAuthDemoUser(Request.Builder request) {
        try {
            return new Response(clientTokenDemo.newCall(request.build()).execute());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Response callWithTokenAuthTestUser(Request.Builder request) {
        try {
            return new Response(clientTokenTest.newCall(request.build()).execute());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Response callWithOidcAuthDemoUser(Request.Builder request) {
        try {
            return new Response(clientOidcDemo.newCall(request.build()).execute());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Response callWithOidcAuthTestUser(Request.Builder request) {
        try {
            return new Response(clientOidcTest.newCall(request.build()).execute());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Response callWithoutAuth(Request.Builder request) {
        try {
            return new Response(clientWithoutAuth.newCall(request.build()).execute());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
