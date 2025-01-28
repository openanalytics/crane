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

import eu.openanalytics.crane.test.helpers.auth.CsrfTokenInterceptor;
import eu.openanalytics.crane.test.helpers.auth.KeycloakAuthOidcInterceptor;
import eu.openanalytics.crane.test.helpers.auth.KeycloakAuthTokenInterceptor;
import okhttp3.CookieJar;
import okhttp3.JavaNetCookieJar;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

import java.io.IOException;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        clientWithoutAuth = new OkHttpClient.Builder().addInterceptor(new CsrfTokenInterceptor()).callTimeout(Duration.ofSeconds(120)).readTimeout(Duration.ofSeconds(120)).build();
        clientTokenDemo = new OkHttpClient.Builder().addInterceptor(new KeycloakAuthTokenInterceptor("demo", "demo")).callTimeout(Duration.ofSeconds(120)).readTimeout(Duration.ofSeconds(120)).build();
        clientTokenTest = new OkHttpClient.Builder().addInterceptor(new KeycloakAuthTokenInterceptor("test", "test")).callTimeout(Duration.ofSeconds(120)).readTimeout(Duration.ofSeconds(120)).build();
        CookieJar demoCookieJar = new JavaNetCookieJar(new CookieManager());
        clientOidcDemo = new OkHttpClient.Builder().cookieJar(demoCookieJar).addInterceptor(new KeycloakAuthOidcInterceptor("demo", "demo")).callTimeout(Duration.ofSeconds(120)).readTimeout(Duration.ofSeconds(120)).build();
        CookieJar testCookieJar = new JavaNetCookieJar(new CookieManager());
        clientOidcTest = new OkHttpClient.Builder().cookieJar(testCookieJar).addInterceptor(new KeycloakAuthOidcInterceptor("test", "test")).callTimeout(Duration.ofSeconds(120)).readTimeout(Duration.ofSeconds(120)).build();
    }

    public static ApiTestHelper from(CraneInstance inst) {
        return new ApiTestHelper(inst.client.getBaseUrl());
    }

    public static ApiTestHelper from(String baseUrl) {
        return new ApiTestHelper(baseUrl);
    }

    public Request.Builder createHtmlRequest(String path) {
        return new Request.Builder().url(baseUrl + path).addHeader("Accept", "text/html");
    }

    public Request.Builder createLogoutRequest(Response response) {
        String responseBody = response.body().toString();
        Pattern pattern = Pattern.compile("<input name=\"_csrf\" type=\"hidden\" value=\"(.*)\"");
        Matcher matcher = pattern.matcher(responseBody);
        String csrfToken = "";
        if (matcher.find())
        {
            csrfToken = matcher.group(1);
        }
        RequestBody requestBody = RequestBody.create("_csrf=%s".formatted(csrfToken), MediaType.get("application/x-www-form-urlencoded"));
        Request.Builder builder = new Request.Builder().url(baseUrl + "/logout").post(requestBody);
        response.setCookies(builder);
        return builder;
    }

    public void performLogoutRoutine(Function<Request.Builder, Response> callAsUser) {
        Response resp = callAsUser.apply(createHtmlRequest("/logout"));
        resp.assertSuccess();
        resp = callAsUser.apply(createLogoutRequest(resp));
        resp.assertSuccessWithRedirect();
        resp.assertRedirectedTo("/logout-success");
    }


    public Request.Builder createMultiPartRequest(String path, Path fileToUpload) throws IOException {
        return createMultiPartRequest(path, fileToUpload, "file", "file.txt");
    }

    public Request.Builder createMultiPartRequest(String path, Path fileToUpload, String parameterName, String fileName) throws IOException {
        MultipartBody body = new MultipartBody.Builder().setType(MultipartBody.FORM).addFormDataPart(parameterName, fileName, RequestBody.create(Files.readAllBytes(fileToUpload))).build();
        return new Request.Builder().url(baseUrl + path).addHeader("Accept", "*/*").post(body);
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

    /**
     * Uses the native JDK HttpClient to perform the request.
     */
    public HttpRequest.Builder createNativeHtmlRequest(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).header("Accept", "text/html");
    }

    /**
     * Uses the native JDK HttpClient to perform the request.
     */
    public HttpRequest.Builder createNativeJsonRequest(String path) {
        return HttpRequest.newBuilder(URI.create(baseUrl + path)).header("Accept", "application/json");
    }

    /**
     * Uses the native JDK HttpClient to perform the request.
     */
    public NativeResponse nativeCallWithTokenAuthDemoUser(HttpRequest.Builder requestBuilder) {
        var client = HttpClient.newHttpClient();
        try {
            KeycloakAuthTokenInterceptor interceptor = new KeycloakAuthTokenInterceptor("demo", "demo");
            HttpRequest request = interceptor.intercept(requestBuilder).build();
            return new NativeResponse(client.send(request, HttpResponse.BodyHandlers.ofString()), request);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Uses the native JDK HttpClient to perform the request.
     */
    public NativeResponse nativeCallWithoutAuth(HttpRequest.Builder requestBuilder) {
        var client = HttpClient.newHttpClient();
        try {
            HttpRequest request = requestBuilder.build();
            return new NativeResponse(client.send(request, HttpResponse.BodyHandlers.ofString()), request);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
