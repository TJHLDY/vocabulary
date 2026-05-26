package org.gduf.service.impl;

import org.gduf.entity.Cet6Word;
import org.gduf.entity.QuizDailyStats;
import org.gduf.entity.QuizProgress;
import org.gduf.mapper.Cet6WordMapper;
import org.gduf.mapper.QuizDailyStatsMapper;
import org.gduf.mapper.QuizLeaderboardLikeMapper;
import org.gduf.mapper.QuizProgressMapper;
import org.gduf.mapper.UserMapper;
import org.gduf.service.QuizService;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QuizServiceImpl implements QuizService {

    private final Cet6WordMapper cet6WordMapper;
    private final QuizProgressMapper quizProgressMapper;
    private final QuizDailyStatsMapper quizDailyStatsMapper;
    private final QuizLeaderboardLikeMapper quizLeaderboardLikeMapper;
    private final UserMapper userMapper;
    private static final SimpleDateFormat SDF = new SimpleDateFormat("yyyy-MM-dd");

    public QuizServiceImpl(Cet6WordMapper cet6WordMapper,
                           QuizProgressMapper quizProgressMapper,
                           QuizDailyStatsMapper quizDailyStatsMapper,
                           QuizLeaderboardLikeMapper quizLeaderboardLikeMapper,
                           UserMapper userMapper) {
        this.cet6WordMapper = cet6WordMapper;
        this.quizProgressMapper = quizProgressMapper;
        this.quizDailyStatsMapper = quizDailyStatsMapper;
        this.quizLeaderboardLikeMapper = quizLeaderboardLikeMapper;
        this.userMapper = userMapper;
    }

    @Override
    public List<Map<String, Object>> generateQuestions(Long userId, String mode) {
        List<Cet6Word> selected = new ArrayList<>();
        Set<Long> selectedIds = new HashSet<>();

        // 1. 错题优先: consecutive_correct = 0, 最多 5 个
        List<QuizProgress> wrongWords = quizProgressMapper.selectWrongWords(userId, 5);
        for (QuizProgress wp : wrongWords) {
            Cet6Word w = cet6WordMapper.selectById(wp.getCetWordId());
            if (w != null && selectedIds.add(w.getId())) {
                selected.add(w);
            }
        }

        // 2. 剩余从未掌握词中随机取
        int remaining = 20 - selected.size();
        if (remaining > 0) {
            List<Long> excludeIds = new ArrayList<>(selectedIds);
            List<QuizProgress> mastered = quizProgressMapper.selectByUser(userId, true);
            for (QuizProgress mp : mastered) {
                excludeIds.add(mp.getCetWordId());
            }
            List<Cet6Word> extras = cet6WordMapper.selectRandomExcluding(
                    excludeIds.isEmpty() ? null : excludeIds, remaining);
            for (Cet6Word w : extras) {
                if (selectedIds.add(w.getId())) {
                    selected.add(w);
                }
            }
        }

        List<Map<String, Object>> questions = buildQuestions(selected, mode);
        Collections.shuffle(questions);
        return questions;
    }

    @Override
    public List<Map<String, Object>> regenerateQuestions(Long userId, String mode, List<Long> excludeWordIds) {
        Set<Long> exclude = new HashSet<>(excludeWordIds != null ? excludeWordIds : Collections.emptyList());

        // 错题优先, 但排除已答
        List<Cet6Word> selected = new ArrayList<>();
        List<QuizProgress> wrongWords = quizProgressMapper.selectWrongWords(userId, 10);
        for (QuizProgress wp : wrongWords) {
            if (selected.size() >= 5) break;
            if (!exclude.contains(wp.getCetWordId())) {
                Cet6Word w = cet6WordMapper.selectById(wp.getCetWordId());
                if (w != null) {
                    selected.add(w);
                    exclude.add(w.getId());
                }
            }
        }

        // 剩余随机取
        int remaining = 20 - selected.size();
        if (remaining > 0) {
            List<QuizProgress> mastered = quizProgressMapper.selectByUser(userId, true);
            for (QuizProgress mp : mastered) exclude.add(mp.getCetWordId());
            List<Long> excludeList = new ArrayList<>(exclude);
            List<Cet6Word> extras = cet6WordMapper.selectRandomExcluding(
                    excludeList.isEmpty() ? null : excludeList, remaining);
            selected.addAll(extras);
        }

        List<Map<String, Object>> questions = buildQuestions(selected, mode);
        Collections.shuffle(questions);
        return questions;
    }

    /** 将单词列表构造成前端题目, 附带选项对照数组 */
    private List<Map<String, Object>> buildQuestions(List<Cet6Word> words, String mode) {
        boolean isEn2Cn = "en2cn".equals(mode);
        List<Map<String, Object>> questions = new ArrayList<>();
        for (Cet6Word correct : words) {
            Map<String, Object> q = new HashMap<>();
            q.put("wordId", correct.getId());
            q.put("mode", isEn2Cn ? "en2cn" : "cn2en");

            List<Cet6Word> distractors = cet6WordMapper.selectRandomByPos(
                    correct.getPos(), correct.getWord(), 3);
            // 构建 (word, meaning) 对, 打乱后提取两个数组
            List<String[]> pairs = new ArrayList<>();
            pairs.add(new String[]{correct.getWord(), correct.getMeaning()});
            for (Cet6Word d : distractors) {
                pairs.add(new String[]{d.getWord(), d.getMeaning()});
            }
            Collections.shuffle(pairs);
            List<String> optionWords = new ArrayList<>();
            List<String> optionMeanings = new ArrayList<>();
            for (String[] p : pairs) {
                optionWords.add(p[0]);
                optionMeanings.add(p[1]);
            }

            if (isEn2Cn) {
                q.put("word", correct.getWord());
                q.put("meaning", correct.getMeaning());
                q.put("pos", formatPos(correct.getPos()));
                q.put("options", optionMeanings);
                q.put("optionWords", optionWords);
            } else {
                q.put("pos", formatPos(correct.getPos()));
                q.put("meaning", correct.getMeaning());
                q.put("options", optionWords);
                q.put("optionMeanings", optionMeanings);
            }
            questions.add(q);
        }
        return questions;
    }

    @Override
    public Map<String, Object> submitAnswer(Long userId, Long cetWordId, String chosen, String mode) {
        Cet6Word correct = cet6WordMapper.selectById(cetWordId);
        boolean isCorrect;
        if ("en2cn".equals(mode)) {
            // 英选中: 比较中文释义
            isCorrect = correct != null && correct.getMeaning().equals(chosen);
        } else {
            // 中选英: 比较英文单词
            isCorrect = correct != null && correct.getWord().equalsIgnoreCase(
                    chosen != null ? chosen.trim() : "");
        }

        // 更新 quiz_progress
        int consecutiveCorrect;
        int totalWrong;
        QuizProgress existing = quizProgressMapper.selectByUserAndWord(userId, cetWordId);
        if (isCorrect) {
            consecutiveCorrect = (existing != null ? existing.getConsecutiveCorrect() : 0) + 1;
            totalWrong = existing != null ? existing.getTotalWrong() : 0;
        } else {
            consecutiveCorrect = 0;
            totalWrong = (existing != null ? existing.getTotalWrong() : 0) + 1;
        }
        quizProgressMapper.upsert(userId, cetWordId, consecutiveCorrect, totalWrong, new Date());

        // 更新每日统计
        quizDailyStatsMapper.upsert(userId, isCorrect);

        Map<String, Object> result = new HashMap<>();
        result.put("correct", isCorrect);
        result.put("correctWord", correct != null ? correct.getWord() : "");
        return result;
    }

    @Override
    public Map<String, Object> getProgress(Long userId) {
        List<QuizProgress> all = quizProgressMapper.selectByUser(userId, null);

        List<Map<String, Object>> wrong = new ArrayList<>();
        List<Map<String, Object>> mastered = new ArrayList<>();

        for (QuizProgress p : all) {
            Cet6Word w = cet6WordMapper.selectById(p.getCetWordId());
            if (w == null) continue;
            Map<String, Object> item = new HashMap<>();
            item.put("wordId", w.getId());
            item.put("word", w.getWord());
            item.put("meaning", w.getMeaning());
            item.put("consecutiveCorrect", p.getConsecutiveCorrect());
            item.put("totalWrong", p.getTotalWrong());

            if (p.getConsecutiveCorrect() >= 3) {
                mastered.add(item);
            } else if (p.getTotalWrong() > 0) {
                wrong.add(item);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("wrong", wrong);
        result.put("mastered", mastered);
        return result;
    }

    @Override
    public List<Map<String, Object>> getWeeklyStats(Long userId) {
        List<QuizDailyStats> stats = quizDailyStatsMapper.selectWeekly(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (QuizDailyStats s : stats) {
            Map<String, Object> item = new HashMap<>();
            item.put("date", SDF.format(s.getQuizDate()));
            item.put("total", s.getTotalQuestions());
            item.put("correct", s.getCorrectAnswers());
            int total = s.getTotalQuestions();
            item.put("rate", total > 0 ? s.getCorrectAnswers() * 100 / total : 0);
            result.add(item);
        }
        return result;
    }

    @Override
    public void resetAll(Long userId) {
        quizProgressMapper.deleteAllByUser(userId);
        quizDailyStatsMapper.deleteAllByUser(userId);
    }

    @Override
    public boolean checkAllMastered(Long userId) {
        return quizProgressMapper.allMastered(userId);
    }

    @Override
    public Map<String, Object> getLeaderboard(Long viewerId, String scope) {
        List<Map<String, Object>> rows = quizDailyStatsMapper.selectLeaderboard(viewerId, scope);

        List<Map<String, Object>> list = new ArrayList<>();
        Long myRank = null;

        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            long rank = i + 1L;
            Map<String, Object> item = new HashMap<>();
            Long uid = ((Number) row.get("user_id")).longValue();
            item.put("userId", uid);
            item.put("username", row.get("username"));
            item.put("avatarUrl", row.get("avatar_url"));
            item.put("correct", ((Number) row.get("correct")).intValue());
            item.put("total", ((Number) row.get("total")).intValue());
            item.put("accuracy", row.get("accuracy"));
            item.put("likes", ((Number) row.get("likes")).intValue());
            item.put("likedByMe", ((Number) row.get("liked_by_me")).intValue() > 0);
            item.put("rank", rank);

            if (uid.equals(viewerId)) {
                myRank = rank;
            }
            if (rank <= 10 || uid.equals(viewerId)) {
                list.add(item);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("myRank", myRank);
        return result;
    }

    @Override
    public Map<String, Object> likeUser(Long fromUserId, Long toUserId) {
        Map<String, Object> result = new HashMap<>();

        if (fromUserId == null || toUserId == null || toUserId <= 0) {
            result.put("liked", false);
            result.put("msg", "点赞用户无效");
            return result;
        }

        if (fromUserId.equals(toUserId)) {
            result.put("liked", false);
            result.put("msg", "不能给自己点赞");
            return result;
        }

        if (userMapper.selectById(toUserId) == null) {
            result.put("liked", false);
            result.put("msg", "用户不存在");
            return result;
        }

        int rows = quizLeaderboardLikeMapper.insertIgnore(fromUserId, toUserId);
        result.put("liked", true);
        result.put("msg", rows > 0 ? "点赞成功" : "今日已点赞");
        return result;
    }

    private String formatPos(String pos) {
        if (pos == null || pos.isBlank()) return "";
        return pos.endsWith(".") ? pos : pos + ".";
    }
}
