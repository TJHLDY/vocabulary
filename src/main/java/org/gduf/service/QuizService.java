package org.gduf.service;

import java.util.List;
import java.util.Map;

public interface QuizService {

    /** 生成 20 道选择题, mode: cn2en 或 en2cn */
    List<Map<String, Object>> generateQuestions(Long userId, String mode);

    /** 提交答案，返回 {correct: bool, correctWord: str} */
    Map<String, Object> submitAnswer(Long userId, Long cetWordId, String chosen, String mode);

    /** 学习记录: {wrong: [...], mastered: [...]} */
    Map<String, Object> getProgress(Long userId);

    /** 近 7 天每日统计 */
    List<Map<String, Object>> getWeeklyStats(Long userId);

    /** 全部掌握后重置 */
    void resetAll(Long userId);

    /** 检查是否全部掌握 */
    boolean checkAllMastered(Long userId);

    /** 排除已答单词后重新生成, mode: cn2en 或 en2cn */
    List<Map<String, Object>> regenerateQuestions(Long userId, String mode, List<Long> excludeWordIds);
}
