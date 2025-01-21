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
package eu.openanalytics.crane.test.helpers.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.jetbrains.annotations.NotNull;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

public class CsrfTokenInterceptor implements Interceptor {

    public static OkHttpClient client = new OkHttpClient.Builder().followRedirects(true).callTimeout(Duration.ofSeconds(120)).readTimeout(Duration.ofSeconds(120)).build();
    public String cookie = null;
    public String csrfToken = null;
    @NotNull
    @Override
    public Response intercept(@NotNull Interceptor.Chain chain) throws IOException {
        return chain.proceed(addCsrfToken(chain.request()));
    }

    public Request addCsrfToken(Request request) throws IOException {
        if (cookie != null && csrfToken != null) {
            return request.newBuilder().addHeader("Cookie", cookie).addHeader("X-CSRF-TOKEN", csrfToken).build();
        }
        if (request.method().equals("POST") && !request.url().uri().getPath().equals("/logout")) {
            String url = request.url().toString();
            url = url.substring(0, url.lastIndexOf("/") + 1);
            Request indexToIndexPage = request.newBuilder().url(url).get().build();
            Response response = client.newCall(indexToIndexPage).execute();
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new RuntimeException();
            }
            if (response.isSuccessful()) {
                ObjectMapper mapper = new ObjectMapper();
                CsrfToken csrfToken = mapper.readValue(responseBody.string(), CsrfToken.class);
                Request.Builder builder = request.newBuilder();
                String cookie = response.header("Set-Cookie");
                if (cookie != null) {
                    builder.addHeader("Cookie", cookie);
                    this.cookie = cookie;
                }
                if (csrfToken.data.containsKey("X-CSRF-TOKEN")) {
                    builder.addHeader("X-CSRF-TOKEN", String.valueOf(csrfToken.data.get("X-CSRF-TOKEN")));
                    this.csrfToken = csrfToken.data.get("X-CSRF-TOKEN").toString();
                }
                request = builder.build();
            }
        }
        return request;
    }

    static class CsrfToken {
        @JsonProperty("data")
        public Map<String, Object> data;

        @JsonProperty("status")
        public String status;
    }
}
