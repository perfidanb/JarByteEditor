package vn.perfidanb.jarbe.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public final class GoogleTranslateClient implements TranslationClient {
    private static final URI WEB_ENDPOINT = URI.create("https://translate.google.com.vn/translate_a/single");

    private final HttpClient client;
    private final ObjectMapper mapper = new ObjectMapper();

    public GoogleTranslateClient() {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build());
    }

    GoogleTranslateClient(HttpClient client) {
        this.client = client;
    }

    @Override
    public List<String> translate(List<String> texts, String sourceLanguage, String targetLanguage) throws IOException, InterruptedException {
        if (texts.isEmpty()) {
            return List.of();
        }
        List<String> translated = new ArrayList<>(texts.size());
        for (String text : texts) {
            translated.add(translateWeb(text, sourceLanguage, targetLanguage));
        }
        return translated;
    }

    private String translateWeb(String text, String sourceLanguage, String targetLanguage) throws IOException, InterruptedException {
        String source = sourceLanguage == null || sourceLanguage.isBlank() ? "auto" : sourceLanguage;
        String query = "client=gtx&sl=" + encode(source)
                + "&tl=" + encode(targetLanguage)
                + "&dt=t&q=" + encode(text);
        HttpRequest request = HttpRequest.newBuilder(URI.create(WEB_ENDPOINT + "?" + query))
                .timeout(Duration.ofSeconds(30))
                .header("User-Agent", "Mozilla/5.0 JarByteEditor/1.0")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        ensureSuccess(response);
        JsonNode segments = mapper.readTree(response.body()).path(0);
        StringBuilder translated = new StringBuilder();
        for (JsonNode segment : segments) {
            translated.append(segment.path(0).asText());
        }
        return translated.toString();
    }

    private static void ensureSuccess(HttpResponse<String> response) throws IOException {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("Google Translate endpoint returned HTTP " + response.statusCode() + ": " + response.body());
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
