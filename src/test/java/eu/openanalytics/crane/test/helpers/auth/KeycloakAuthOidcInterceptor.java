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

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;


public class KeycloakAuthOidcInterceptor implements Interceptor {
    private final String username;
    private final String password;
    private static final CsrfTokenInterceptor csrfTokenInterceptor = new CsrfTokenInterceptor();

    public KeycloakAuthOidcInterceptor(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        // Adding username for test messages
        Request initialRequest = chain.request().newBuilder().header("user", username).build();
        Response response = chain.proceed(csrfTokenInterceptor.addCsrfToken(initialRequest));
        Request request = response.request();
        String body = new String(response.peekBody(Integer.MAX_VALUE).bytes());
        if (body.contains("action=")) {
            int startOfAuthenticationUrl = body.indexOf("action=") + "action=".length() + 1;
            String authenticationUrl = body.substring(startOfAuthenticationUrl, body.indexOf('"', startOfAuthenticationUrl));
            if (authenticationUrl.startsWith("http://") || authenticationUrl.startsWith("https://")) {
                Request authenticationRequest = request.newBuilder()
                    .post(RequestBody.create(
                        String.format("password=%s&username=%s&credentialId=", password, username),
                        MediaType.get("application/x-www-form-urlencoded"))
                    )
                    .url(authenticationUrl)
                    .build();
                response = chain.proceed(authenticationRequest);
            }
        }
        return response;
    }
}
