package org.gduf.controller;

import jakarta.annotation.Resource;
import org.gduf.common.R;
import org.gduf.entity.Word;
import org.gduf.security.UserContext;
import org.gduf.service.WordService;
import org.springframework.web.bind.annotation.*;

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
    public R all(@RequestParam(defaultValue = "count") String sort) {
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

    @PostMapping("/dictation")
    public R dictation(@RequestParam String word, @RequestParam Boolean correct) {
        wordService.recordDictation(UserContext.getUserId(), word, correct);
        return R.ok(null);
    }
}
