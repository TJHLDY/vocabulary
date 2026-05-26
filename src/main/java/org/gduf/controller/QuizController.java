package org.gduf.controller;

import jakarta.annotation.Resource;
import org.gduf.common.R;
import org.gduf.security.UserContext;
import org.gduf.service.QuizService;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    @Resource
    private QuizService quizService;

    /** 生成 20 道题 */
    @GetMapping("/generate")
    public R generate(@RequestParam(defaultValue = "cn2en") String mode) {
        List<Map<String, Object>> questions = quizService.generateQuestions(UserContext.getUserId(), mode);
        boolean allMastered = quizService.checkAllMastered(UserContext.getUserId());
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("questions", questions);
        result.put("allMastered", allMastered);
        return R.ok(result);
    }

    /** 提交单题答案 */
    @PostMapping("/answer")
    public R answer(@RequestBody Map<String, Object> body) {
        Long wordId = Long.valueOf(body.get("wordId").toString());
        String chosen = (String) body.get("chosen");
        String mode = (String) body.getOrDefault("mode", "cn2en");
        Map<String, Object> result = quizService.submitAnswer(UserContext.getUserId(), wordId, chosen, mode);
        return R.ok(result);
    }

    /** 学习记录（错题 + 已掌握） */
    @GetMapping("/progress")
    public R progress() {
        return R.ok(quizService.getProgress(UserContext.getUserId()));
    }

    /** 近 7 天统计 */
    @GetMapping("/stats")
    public R stats() {
        return R.ok(quizService.getWeeklyStats(UserContext.getUserId()));
    }

    /** 全部掌握后重置 */
    @PostMapping("/reset")
    public R reset() {
        quizService.resetAll(UserContext.getUserId());
        return R.ok(null);
    }

    /** 检查是否全部掌握 */
    @GetMapping("/check-mastered")
    public R checkMastered() {
        return R.ok(quizService.checkAllMastered(UserContext.getUserId()));
    }

    /** 切换模式时重新生成, 排除已答单词 */
    @PostMapping("/regenerate")
    public R regenerate(@RequestBody Map<String, Object> body) {
        String mode = (String) body.getOrDefault("mode", "cn2en");
        @SuppressWarnings("unchecked")
        List<Integer> idList = (List<Integer>) body.get("excludeWordIds");
        List<Long> excludeWordIds = idList != null
                ? idList.stream().map(Long::valueOf).toList()
                : Collections.emptyList();
        List<Map<String, Object>> questions = quizService.regenerateQuestions(UserContext.getUserId(), mode, excludeWordIds);
        boolean allMastered = quizService.checkAllMastered(UserContext.getUserId());
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("questions", questions);
        result.put("allMastered", allMastered);
        return R.ok(result);
    }

    /** 排行榜 */
    @GetMapping("/leaderboard")
    public R leaderboard(@RequestParam(defaultValue = "day") String scope) {
        return R.ok(quizService.getLeaderboard(UserContext.getUserId(), scope));
    }

    /** 点赞 */
    @PostMapping("/leaderboard/like")
    public R like(@RequestBody Map<String, Object> body) {
        Object target = body.get("targetUserId");
        if (target == null) {
            return R.fail("缺少点赞用户");
        }

        Long targetUserId;
        try {
            targetUserId = Long.valueOf(target.toString());
        } catch (NumberFormatException e) {
            return R.fail("点赞用户无效");
        }

        Map<String, Object> result = quizService.likeUser(UserContext.getUserId(), targetUserId);
        if (Boolean.FALSE.equals(result.get("liked"))) {
            return R.fail((String) result.get("msg"));
        }
        return R.ok(result);
    }
}
