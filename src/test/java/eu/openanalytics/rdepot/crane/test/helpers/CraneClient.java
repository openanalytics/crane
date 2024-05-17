package eu.openanalytics.rdepot.crane.test.helpers;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.time.Duration;

public class CraneClient {

    private final String baseUrl;


    public CraneClient(int port) {
        this.baseUrl = "http://localhost:" + port;
    }

    public boolean checkAlive() {
        // client without auth
        OkHttpClient client = new OkHttpClient.Builder()
            .callTimeout(Duration.ofSeconds(120))
            .readTimeout(Duration.ofSeconds(120))
            .build();

        Request request = new Request.Builder()
            .get()
            .url(baseUrl + "/logout-success")
            .build();

        try (Response response = client.newCall(request).execute()) {
            return response.code() == 200;
        } catch (Exception e) {
            return false;
        }

    }

    public String getBaseUrl() {
        return baseUrl;
    }
}
