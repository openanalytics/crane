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
package eu.openanalytics.rdepot.crane.test.helpers.auth;

import okhttp3.*;

import java.io.IOException;


public class KeycloakAuthOidcInterceptor implements Interceptor {
    private final String username;
    private final String password;

    public KeycloakAuthOidcInterceptor(String username, String password) {
        this.username = username;
        this.password = password;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Response response = chain.proceed(chain.request());
        Request request = response.request();
        ResponseBody responseBody = response.body();
        if (responseBody != null && responseBody.contentLength() != -1) {
            String body = responseBody.string();
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