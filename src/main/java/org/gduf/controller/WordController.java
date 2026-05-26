package org.gduf.controller;

import jakarta.annotation.Resource;
import org.gduf.common.R;
import org.gduf.entity.Word;
import org.gduf.security.UserContext;
import org.gduf.service.WordService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/words")
public class WordController {

    @Resource
    private WordService wordService;

    @PostMapping
    public R save(@RequestParam String word) {
        if (word == null || word.isBlank()) {
            return R.fail("单词不能为空");
        }
        Map<String, Object> result = wordService.saveOrIncrement(UserContext.getUserId(), word);
        return R.ok(result);
    }

    @GetMapping("/all")
    public R all(@RequestParam(defaultValue = "count") String sort,
                 @RequestParam(required = false) Integer page,
                 @RequestParam(required = false) Integer size,
                 @RequestParam(required = false) String q) {
        if (page != null || size != null || (q != null && !q.isBlank())) {
            int safePage = page == null ? 1 : page;
            int safeSize = size == null ? 80 : size;
            return R.ok(wordService.myWordsPage(UserContext.getUserId(), sort, q, safePage, safeSize));
        }
        List<Word> list = wordService.myWords(UserContext.getUserId(), sort);
        return R.ok(list);
    }

    @GetMapping("/relations")
    public R relations(@RequestParam String word) {
        if (word == null || word.isBlank()) {
            return R.fail("单词不能为空");
        }
        return R.ok(wordService.getRelations(UserContext.getUserId(), word));
    }

    @GetMapping("/translate")
    public R translateOnly(@RequestParam String word) {
        if (word == null || word.isBlank()) return R.fail("单词不能为空");
        Map<String, Object> dict = wordService.lookupWord(word);
        if (dict == null || dict.isEmpty()) return R.fail("未查到该单词");
        return R.ok(dict);
    }

    @DeleteMapping("/{word}")
    public R remove(@PathVariable String word) {
        wordService.removeWord(UserContext.getUserId(), word);
        return R.ok(null);
    }

    @DeleteMapping("/all")
    public R clear() {
        wordService.clearAll(UserContext.getUserId());
        return R.ok(null);
    }

    @GetMapping("/dict-proxy/{word}")
    public ResponseEntity<String> dictProxy(@PathVariable String word) {
        try {
            String url = "https://api.dictionaryapi.dev/api/v2/entries/en/"
                    + URLEncoder.encode(word, StandardCharsets.UTF_8);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();
            HttpResponse<String> resp = HttpClient.newHttpClient()
                    .send(req, HttpResponse.BodyHandlers.ofString());
            return ResponseEntity.status(resp.statusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(resp.body());
        } catch (Exception e) {
            return ResponseEntity.status(502).build();
        }
    }

    @PostMapping("/dictation")
    public R dictation(@RequestParam String word, @RequestParam Boolean correct) {
        wordService.recordDictation(UserContext.getUserId(), word, correct);
        return R.ok(null);
    }
}
