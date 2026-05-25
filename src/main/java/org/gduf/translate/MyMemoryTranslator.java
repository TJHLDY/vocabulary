package org.gduf.translate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

@Component
public class MyMemoryTranslator implements Translator {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String translate(String text, String from, String to) {
        try {
            String langpair;
            if ("zh".equals(from)) {
                langpair = "zh-CN|en";
            } else {
                langpair = "en|zh";
            }
            String url = "https://api.mymemory.translated.net/get?q="
                    + URLEncoder.encode(text, StandardCharsets.UTF_8)
                    + "&langpair=" + URLEncoder.encode(langpair, StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());
            String translated = root.path("responseData").path("translatedText").asText("");
            return translated.isBlank() ? null : translated;
        } catch (Exception e) {
            System.err.println("[MyMemory] exception: " + e.getMessage());
            return null;
        }
    }

    @Override
    public String getName() { return "MyMemory"; }

    @Override
    public int getPriority() { return Integer.MAX_VALUE; }
}
