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

import com.fasterxml.jackson.annotation.JsonProperty;
import okhttp3.*;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;

public class KeycloakAuthInterceptor implements Interceptor {

    static class KeycloakToken {

        @JsonProperty("access_token")
        public String accessToken;
        @JsonProperty("expires_in")
        public int expiresIn;
        @JsonProperty("refresh_expires_in")
        public int refreshExpiresIn;
        @JsonProperty("refresh_token")
        public String refreshToken;
        @JsonProperty("token_type")
        public String tokenType;
        @JsonProperty("not-before-policy")
        public int notBeforePolicy;
        @JsonProperty("session_state")
        public String sessionState;
        @JsonProperty("scope")
        public String scope;

        public KeycloakToken() {}

        public String getCredentials() {
            return "Bearer " + accessToken;
        }
    }
    private final String credentials;
    public static OkHttpClient client = new OkHttpClient.Builder()
        .followRedirects(false)
        .callTimeout(Duration.ofSeconds(120))
        .readTimeout(Duration.ofSeconds(120))
        .build();

    public KeycloakAuthInterceptor(String user, String password) {
        try {
            Response response = new Response(
                client.newCall(
                    new Request.Builder().post(
                            RequestBody.create(
                                "username=" + user +
                                    "&password=" + password +
                                    "&grant_type=password" +
                                    "&client_id=crane_client" +
                                    "&client_secret=secret",
                                MediaType.parse("application/x-www-form-urlencoded")
                            )
                        ).url("http://localhost:9189/realms/crane/protocol/openid-connect/token")
                        .build()
                ).execute()
            );
            ObjectMapper mapper = new ObjectMapper();
            KeycloakToken keycloakToken = mapper.readValue(response.body(), KeycloakToken.class);
            this.credentials = keycloakToken.getCredentials();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public okhttp3.Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        Request authenticatedRequest = request.newBuilder()
            .header("Authorization", credentials).build();
        return chain.proceed(authenticatedRequest);
    }

}
