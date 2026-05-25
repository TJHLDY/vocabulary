package org.gduf.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.gduf.entity.Word;
import org.gduf.entity.WordRelation;

import java.util.List;
import java.util.Map;

public interface WordService extends IService<Word> {
    Map<String, Object> saveOrIncrement(Long userId, String word);
    List<Word> myWords(Long userId, String sort);
    List<WordRelation> getRelations(Long userId, String word);
    String translateWord(String word, String from, String to);
    Map<String, Object> lookupWord(String word);
    void removeWord(Long userId, String word);
    void clearAll(Long userId);
    void recordDictation(Long userId, String word, boolean correct);
}
