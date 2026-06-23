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
package eu.openanalytics.crane.test.helpers.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import eu.openanalytics.crane.test.helpers.TestHelperException;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

public class CsrfTokenInterceptor implements Interceptor {

    private static final OkHttpClient client = new OkHttpClient.Builder().followRedirects(true).callTimeout(Duration.ofSeconds(120)).readTimeout(Duration.ofSeconds(120)).build();
    private final String baseUrl;
    private String cookie = null;
    private String csrfToken = null;

    public CsrfTokenInterceptor(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    @NotNull
    @Override
    public Response intercept(@NotNull Interceptor.Chain chain) throws IOException {
        return chain.proceed(addCsrfToken(chain.request()));
    }

    public Request addCsrfToken(Request request) throws IOException {
        if (cookie != null && csrfToken != null) {
            return request.newBuilder().addHeader("Cookie", cookie).addHeader("X-CSRF-TOKEN", csrfToken).build();
        }
        if ((request.method().equals("POST") || request.method().equals("DELETE")) && !request.url().uri().getPath().equals("/logout")) {
            Request csrfRequest = request.newBuilder().url(baseUrl + "/__csrf").get().build();
            try (Response response = client.newCall(csrfRequest).execute()) {
                ResponseBody responseBody = response.body();
                if (responseBody == null) {
                    throw new TestHelperException("Empty or invalid CSRF response");
                }
                if (response.isSuccessful()) {
                    ObjectMapper mapper = new ObjectMapper();
                    ResponseDTO csrfToken = mapper.readValue(responseBody.string(), ResponseDTO.class);
                    Request.Builder builder = request.newBuilder();
                    String cookie = response.header("Set-Cookie");
                    if (cookie != null) {
                        builder.addHeader("Cookie", cookie);
                        this.cookie = cookie;
                    }
                    if (csrfToken.data.containsKey("token")) {
                        builder.addHeader("X-CSRF-TOKEN", String.valueOf(csrfToken.data.get("token")));
                        this.csrfToken = csrfToken.data.get("token").toString();
                    }
                    request = builder.build();
                }
            }
        }
        return request;
    }

    private static class ResponseDTO {
        @JsonProperty("data")
        public Map<String, Object> data;

        @JsonProperty("status")
        public String status;
    }

}
