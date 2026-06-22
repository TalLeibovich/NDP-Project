package com.example.ndpclient;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class ApiClient {

    public interface ApiCallback {
        void onSuccess(String json);
        void onError(Exception e);
    }

    private static final int CONNECT_TIMEOUT = 15000;
    private static final int READ_TIMEOUT = 15000;

    public static void get(final String path, final ApiCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(AppData.BASE_URL + path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);

                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                String response = readStream(is);
                conn.disconnect();

                if (code >= 200 && code < 300) {
                    callback.onSuccess(response);
                } else {
                    callback.onError(new Exception("HTTP " + code + ": " + response));
                }
            } catch (Exception e) {
                callback.onError(e);
            }
        }).start();
    }

    public static void post(final String path, final String jsonBody, final ApiCallback callback) {
        sendWithBody("POST", path, jsonBody, callback);
    }

    public static void put(final String path, final String jsonBody, final ApiCallback callback) {
        sendWithBody("PUT", path, jsonBody, callback);
    }

    private static void sendWithBody(final String method, final String path, final String jsonBody, final ApiCallback callback) {
        new Thread(() -> {
            try {
                URL url = new URL(AppData.BASE_URL + path);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod(method);
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(READ_TIMEOUT);
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);

                OutputStream os = conn.getOutputStream();
                os.write(jsonBody.getBytes("UTF-8"));
                os.close();

                int code = conn.getResponseCode();
                InputStream is = (code >= 200 && code < 300)
                        ? conn.getInputStream()
                        : conn.getErrorStream();

                String response = readStream(is);
                conn.disconnect();

                if (code >= 200 && code < 300) {
                    callback.onSuccess(response);
                } else {
                    callback.onError(new Exception("HTTP " + code + ": " + response));
                }
            } catch (Exception e) {
                callback.onError(e);
            }
        }).start();
    }

    private static String readStream(InputStream is) throws Exception {
        if (is == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }

        reader.close();
        return sb.toString();
    }
}