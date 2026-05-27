package org.gduf.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.gduf.entity.PkDailyStats;
import org.gduf.entity.PkMatch;
import org.gduf.entity.PkUserSetting;
import org.gduf.entity.PkUserStats;
import org.gduf.mapper.PkMatchMapper;
import org.gduf.mapper.PkMatchQuestionMapper;
import org.gduf.mapper.PkDailyStatsMapper;
import org.gduf.mapper.PkUserSettingMapper;
import org.gduf.mapper.PkUserStatsMapper;
import org.gduf.security.JwtAuthFilter;
import org.gduf.service.PkService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class PkServiceImpl implements PkService {

    private static final long ONLINE_THRESHOLD_MS = 5 * 60 * 1000;

    private final PkUserStatsMapper pkUserStatsMapper;
    private final PkDailyStatsMapper pkDailyStatsMapper;
    private final PkUserSettingMapper pkUserSettingMapper;
    private final PkMatchMapper pkMatchMapper;
    private final PkMatchQuestionMapper pkMatchQuestionMapper;

    public PkServiceImpl(PkUserStatsMapper pkUserStatsMapper,
                         PkDailyStatsMapper pkDailyStatsMapper,
                         PkUserSettingMapper pkUserSettingMapper,
                         PkMatchMapper pkMatchMapper,
                         PkMatchQuestionMapper pkMatchQuestionMapper) {
        this.pkUserStatsMapper = pkUserStatsMapper;
        this.pkDailyStatsMapper = pkDailyStatsMapper;
        this.pkUserSettingMapper = pkUserSettingMapper;
        this.pkMatchMapper = pkMatchMapper;
        this.pkMatchQuestionMapper = pkMatchQuestionMapper;
    }

    @Override
    public List<Map<String, Object>> getArenaUsers(Long currentUserId) {
        long now = System.currentTimeMillis();
        long threshold = now - ONLINE_THRESHOLD_MS;

        List<Long> onlineIds = JwtAuthFilter.LAST_ACTIVE.entrySet().stream()
                .filter(e -> e.getValue() >= threshold)
                .map(Map.Entry::getKey)
                .filter(id -> !id.equals(currentUserId))
                .collect(Collectors.toList());

        if (onlineIds.isEmpty()) return Collections.emptyList();

        List<Map<String, Object>> users = pkUserStatsMapper.selectArenaUsers(onlineIds);
        Map<Long, Integer> rankByUserId = new HashMap<>();
        List<Map<String, Object>> leaderboard = pkUserStatsMapper.selectLeaderboard("all");
        for (int i = 0; i < leaderboard.size(); i++) {
            Object id = leaderboard.get(i).get("userId");
            if (id instanceof Number number) {
                rankByUserId.put(number.longValue(), i + 1);
            }
        }
        for (Map<String, Object> user : users) {
            Object id = user.get("userId");
            if (id instanceof Number number) {
                Integer rank = rankByUserId.get(number.longValue());
                if (rank != null) user.put("pkRank", rank);
            }
        }
        return users;
    }

    @Override
    public Map<String, Object> getSettings(Long userId) {
        PkUserSetting setting = pkUserSettingMapper.selectByUserId(userId);
        Map<String, Object> result = new HashMap<>();
        result.put("invisible", setting != null && Boolean.TRUE.equals(setting.getInvisible()));
        result.put("firstWinDeclaration", setting != null ? nullToEmpty(setting.getFirstWinDeclaration()) : "");
        result.put("revengeDeclaration", setting != null ? nullToEmpty(setting.getRevengeDeclaration()) : "");
        result.put("sweepDeclaration", setting != null ? nullToEmpty(setting.getSweepDeclaration()) : "");
        result.put("finalWinDeclaration", setting != null ? nullToEmpty(setting.getFinalWinDeclaration()) : "");
        return result;
    }

    @Override
    public void setInvisible(Long userId, boolean invisible) {
        pkUserSettingMapper.upsertInvisible(userId, invisible);
    }

    @Override
    public void updateDeclarations(Long userId, Map<String, Object> body) {
        String firstWin = declaration(body.get("firstWinDeclaration"));
        String revenge = declaration(body.get("revengeDeclaration"));
        String sweep = declaration(body.get("sweepDeclaration"));
        String finalWin = declaration(body.get("finalWinDeclaration"));
        pkUserSettingMapper.upsertDeclarations(userId, firstWin, revenge, sweep, finalWin);
    }

    @Override
    public List<Map<String, Object>> getRecentMatches(Long userId) {
        return pkMatchMapper.selectRecentMatches(userId, 5);
    }

    @Override
    public Map<String, Object> getReplay(Long userId, Long matchId) {
        if (matchId == null) {
            throw new IllegalArgumentException("缺少对局ID");
        }
        PkMatch match = pkMatchMapper.selectById(matchId);
        if (match == null) {
            throw new IllegalArgumentException("对局不存在");
        }
        if (!userId.equals(match.getPlayerAId()) && !userId.equals(match.getPlayerBId())) {
            throw new IllegalArgumentException("无权查看这场复盘");
        }
        Map<String, Object> result = new HashMap<>();
        result.put("match", match);
        result.put("questions", pkMatchQuestionMapper.selectReplayQuestions(matchId));
        return result;
    }

    @Override
    public List<Map<String, Object>> getLeaderboard(String scope) {
        if (scope == null || scope.isBlank()) scope = "day";
        return pkUserStatsMapper.selectLeaderboard(scope);
    }

    @Override
    public Map<String, Object> getMyStats(Long userId) {
        PkUserStats stats = pkUserStatsMapper.selectOne(
                new QueryWrapper<PkUserStats>().eq("user_id", userId));
        PkDailyStats today = pkDailyStatsMapper.selectTodayByUserId(userId);

        Map<String, Object> result = new HashMap<>();
        if (stats != null) {
            result.put("points", stats.getPoints());
            result.put("wins", stats.getWins());
            result.put("losses", stats.getLosses());
            result.put("draws", stats.getDraws());
            result.put("matches", stats.getMatches());
            result.put("highestStreak", stats.getHighestStreak());
        } else {
            result.put("points", 0);
            result.put("wins", 0);
            result.put("losses", 0);
            result.put("draws", 0);
            result.put("matches", 0);
            result.put("highestStreak", 0);
        }
        result.put("basePointsToday", today != null ? today.getBasePointsGained() : 0);
        result.put("streakPointsToday", today != null ? today.getStreakPointsGained() : 0);
        result.put("netPointsToday", today != null ? today.getNetPoints() : 0);
        return result;
    }

    private String declaration(Object value) {
        String text = value == null ? "" : value.toString().trim();
        if (text.length() > 15) {
            throw new IllegalArgumentException("胜利宣言最多15个字");
        }
        return text;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
