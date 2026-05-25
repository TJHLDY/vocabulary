package org.gduf.translate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Random;

public class BaiduTranslator implements Translator {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String name;
    private final int priority;
    private final String appId;
    private final String secretKey;
    private final String endpoint;
    private final boolean llmMode;

    public BaiduTranslator(String name, int priority, String appId, String secretKey,
                           String endpoint, boolean llmMode) {
        this.name = name;
        this.priority = priority;
        this.appId = appId;
        this.secretKey = secretKey;
        this.endpoint = endpoint;
        this.llmMode = llmMode;
    }

    @Override
    public String translate(String text, String from, String to) {
        if (appId == null || secretKey == null || appId.isBlank() || secretKey.isBlank()) {
            return null;
        }
        try {
            String salt = String.valueOf(new Random().nextInt(1000000000));
            String sign = md5(appId + text + salt + secretKey);

            String langFrom = "zh".equals(from) ? "zh" : (from == null || from.isBlank() ? "auto" : from);
            String langTo = "zh".equals(to) ? "zh" : (to == null || to.isBlank() ? "en" : to);

            String body = "q=" + URLEncoder.encode(text, StandardCharsets.UTF_8)
                    + "&from=" + URLEncoder.encode(langFrom, StandardCharsets.UTF_8)
                    + "&to=" + URLEncoder.encode(langTo, StandardCharsets.UTF_8)
                    + "&appid=" + URLEncoder.encode(appId, StandardCharsets.UTF_8)
                    + "&salt=" + URLEncoder.encode(salt, StandardCharsets.UTF_8)
                    + "&sign=" + URLEncoder.encode(sign, StandardCharsets.UTF_8);

            if (llmMode) {
                body += "&model=llm";
            }

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());

            JsonNode errorCode = root.path("error_code");
            if (!errorCode.isMissingNode() && errorCode.asInt() != 0) {
                System.err.println("[Baidu " + name + "] error: " + errorCode.asText() + " - " + root.path("error_msg").asText());
                return null;
            }

            String translated = root.path("trans_result").get(0).path("dst").asText("");
            return translated.isBlank() ? null : translated;
        } catch (Exception e) {
            System.err.println("[Baidu " + name + "] exception: " + e.getMessage());
            return null;
        }
    }

    @Override
    public String getName() { return name; }

    @Override
    public int getPriority() { return priority; }

    private static String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
