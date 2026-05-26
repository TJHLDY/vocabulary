package org.gduf.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gduf.entity.Word;
import org.gduf.entity.WordRelation;
import org.gduf.mapper.WordMapper;
import org.gduf.mapper.WordRelationMapper;
import org.gduf.service.WordService;
import org.gduf.translate.TranslateChain;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class WordServiceImpl extends ServiceImpl<WordMapper, Word> implements WordService {

    private final WordRelationMapper relationMapper;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TranslateChain translateChain;

    public WordServiceImpl(WordRelationMapper relationMapper, TranslateChain translateChain) {
        this.relationMapper = relationMapper;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.translateChain = translateChain;
    }

    @Override
    public Map<String, Object> saveOrIncrement(Long userId, String word) {
        word = word.toLowerCase().trim();

        Map<String, Object> dict = lookupWord(word);
        String chinese = null;
        if (dict != null && !dict.isEmpty()) {
            chinese = formatMeanings(dict);
        }
        if (chinese == null) {
            chinese = translateWord(word, "en", "zh");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("word", word);

        if (chinese == null || chinese.equalsIgnoreCase(word)) {
            result.put("chinese", null);
            return result;
        }
        result.put("chinese", chinese);
        String fc = (dict != null) ? formatMeaningsFull(dict) : null;
        result.put("fullChinese", fc != null ? fc : chinese);
        result.put("dict", dict);

        baseMapper.insertOrIncrement(userId, word, chinese);
        return result;
    }

    @Override
    public List<Word> myWords(Long userId, String sort) {
        return baseMapper.selectByUserId(userId, sort);
    }

    @Override
    public Map<String, Object> myWordsPage(Long userId, String sort, String keyword, int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 20), 200);
        int offset = (safePage - 1) * safeSize;
        String kw = keyword == null ? "" : keyword.trim();

        int total = baseMapper.countByUserId(userId, kw);
        List<Word> items = total == 0
                ? Collections.emptyList()
                : baseMapper.selectPageByUserId(userId, sort, kw, offset, safeSize);

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("total", total);
        result.put("page", safePage);
        result.put("size", safeSize);
        result.put("hasMore", offset + items.size() < total);
        return result;
    }

    @Override
    public List<WordRelation> getRelations(Long userId, String word) {
        return relationMapper.selectTopByWord(userId, word.toLowerCase().trim(), 5);
    }

    @Override
    public String translateWord(String word, String from, String to) {
        return translateChain.translate(word, from, to);
    }

    @Override
    public Map<String, Object> lookupWord(String word) {
        try {
            String url = "http://dict.youdao.com/jsonapi?jsonversion=2&client=mobile&q="
                    + URLEncoder.encode(word, StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            JsonNode root = objectMapper.readTree(resp.body());

            JsonNode wordList = root.path("ec").path("word");
            if (wordList.isMissingNode() || !wordList.isArray() || wordList.isEmpty()) return null;
            JsonNode entry = wordList.get(0);

            Map<String, Object> result = new HashMap<>();
            result.put("word", word);

            String usphone = entry.path("usphone").asText("");
            if (!usphone.isBlank()) result.put("phonetic", "/" + usphone + "/");

            // trs → extract pos+chinese strings from trs[].tr[].l.i[]
            List<String> meanings = new ArrayList<>();
            for (JsonNode t : entry.path("trs")) {
                for (JsonNode trItem : t.path("tr")) {
                    for (JsonNode i : trItem.path("l").path("i")) {
                        String text = i.asText("").trim();
                        if (!text.isBlank()) meanings.add(text);
                    }
                }
            }
            result.put("meanings", meanings);

            // word forms: wfs[].wf.{name, value}
            List<Map<String, String>> wordForms = new ArrayList<>();
            for (JsonNode wf : entry.path("wfs")) {
                JsonNode wfObj = wf.path("wf");
                Map<String, String> form = new HashMap<>();
                form.put("name", wfObj.path("name").asText(""));
                form.put("value", wfObj.path("value").asText(""));
                if (!form.get("value").isBlank()) {
                    wordForms.add(form);
                }
            }
            if (!wordForms.isEmpty()) result.put("wordForms", wordForms);

            return result;
        } catch (Exception e) {
            return null;
        }
    }

    private String formatMeanings(Map<String, Object> dict) {
        @SuppressWarnings("unchecked")
        List<String> meanings = (List<String>) dict.get("meanings");
        if (meanings == null || meanings.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < meanings.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(truncateMeaning(meanings.get(i)));
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    private String truncateMeaning(String meaning) {
        String[] parts = meaning.split("；");
        if (parts.length <= 3) return meaning;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 3; i++) {
            if (i > 0) sb.append("；");
            sb.append(parts[i]);
        }
        return sb.toString();
    }

    private String formatMeaningsFull(Map<String, Object> dict) {
        @SuppressWarnings("unchecked")
        List<String> meanings = (List<String>) dict.get("meanings");
        if (meanings == null || meanings.isEmpty()) return null;

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < meanings.size(); i++) {
            if (i > 0) sb.append("\n");
            sb.append(meanings.get(i));
        }

        @SuppressWarnings("unchecked")
        List<Map<String, String>> wordForms = (List<Map<String, String>>) dict.get("wordForms");
        if (wordForms != null && !wordForms.isEmpty()) {
            for (Map<String, String> wf : wordForms) {
                sb.append("\n").append(wf.get("name")).append(": ").append(wf.get("value"));
            }
        }

        return sb.length() > 0 ? sb.toString() : null;
    }

    @Override
    public void removeWord(Long userId, String word) {
        baseMapper.deleteByUserAndWord(userId, word.toLowerCase().trim());
    }

    @Override
    public void clearAll(Long userId) {
        baseMapper.deleteAllByUser(userId);
    }

    @Override
    public void recordDictation(Long userId, String word, boolean correct) {
        baseMapper.recordDictation(userId, word.toLowerCase().trim(), correct);
    }
}
