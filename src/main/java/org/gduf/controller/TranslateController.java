package org.gduf.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.gduf.common.R;
import org.gduf.entity.SentenceHistory;
import org.gduf.mapper.SentenceHistoryMapper;
import org.gduf.security.UserContext;
import org.gduf.service.WordService;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/translate")
public class TranslateController {

    @Resource
    private WordService wordService;

    @Resource
    private SentenceHistoryMapper sentenceHistoryMapper;

    @PostMapping
    public R translate(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        String from = body.getOrDefault("from", "en");
        String to = body.getOrDefault("to", "zh");
        if (text == null || text.isBlank()) return R.fail("文本不能为空");

        String result = wordService.translateWord(text, from, to);
        return result != null ? R.ok(Map.of("translation", result)) : R.fail("翻译失败");
    }

    @PostMapping("/sentence")
    public R translateSentence(@RequestBody Map<String, String> body) {
        String text = body.get("text");
        String from = body.getOrDefault("from", "en");
        String to = body.getOrDefault("to", "zh");
        if (text == null || text.isBlank()) return R.fail("文本不能为空");

        // Translate full sentence
        String fullTranslation = wordService.translateWord(text, from, to);
        if (fullTranslation == null) return R.fail("翻译失败");

        // Tokenize and translate each word (only for English input)
        List<Map<String, Object>> words = new ArrayList<>();
        if ("en".equals(from)) {
            for (String token : text.split("[\\s,.!?;:]+")) {
                if (token.isBlank() || token.length() <= 2) continue;
                String clean = token.replaceAll("[^a-zA-Z0-9-]", "").toLowerCase();
                if (clean.isEmpty() || clean.matches("^(the|a|an|is|are|was|were|be|been|in|on|at|to|for|of|and|or|it|he|she|they|we|you|I|me|him|her|us|them|this|that|these|those|my|your|his|its|our|their|do|does|did|has|have|had|can|could|will|would|shall|should|may|might|must|not|no|yes|but|if|so|as|by|from|with|about|up|out|all|just|only|also|very|too|well|here|there|when|where|why|how|which|who|whom|what)$")) continue;

                Map<String, Object> wordInfo = (Map<String, Object>) wordService.saveOrIncrement(UserContext.getUserId(), clean);
                words.add(wordInfo);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("original", text);
        result.put("translated", fullTranslation);
        result.put("words", words.stream()
                .filter(w -> w.get("chinese") != null && !w.get("chinese").toString().isBlank())
                .collect(Collectors.toList()));

        // Save query history, keep only last 3
        saveSentenceHistory(UserContext.getUserId(), text);

        return R.ok(result);
    }

    private void saveSentenceHistory(Long userId, String text) {
        SentenceHistory exist = sentenceHistoryMapper.selectOne(
                new QueryWrapper<SentenceHistory>().eq("user_id", userId).eq("text", text));
        if (exist != null) {
            exist.setCreatedAt(new Date());
            sentenceHistoryMapper.updateById(exist);
            return;
        }
        SentenceHistory sh = new SentenceHistory().setUserId(userId).setText(text).setCreatedAt(new Date());
        sentenceHistoryMapper.insert(sh);
        List<SentenceHistory> all = sentenceHistoryMapper.selectList(
                new QueryWrapper<SentenceHistory>().eq("user_id", userId).orderByDesc("created_at"));
        if (all.size() > 3) {
            List<Long> ids = all.subList(3, all.size()).stream().map(SentenceHistory::getId).toList();
            sentenceHistoryMapper.deleteBatchIds(ids);
        }
    }

    @GetMapping("/history")
    public R history() {
        Long userId = UserContext.getUserId();
        List<SentenceHistory> list = sentenceHistoryMapper.selectList(
                new QueryWrapper<SentenceHistory>()
                        .select("id", "text", "created_at")
                        .eq("user_id", userId)
                        .orderByDesc("created_at")
                        .last("LIMIT 3"));
        return R.ok(list);
    }
}
