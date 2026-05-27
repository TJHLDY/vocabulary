package org.gduf.service;

import java.util.List;
import java.util.Map;

public interface PkService {

    List<Map<String, Object>> getArenaUsers(Long currentUserId);

    Map<String, Object> getSettings(Long userId);

    void setInvisible(Long userId, boolean invisible);

    void updateDeclarations(Long userId, Map<String, Object> body);

    List<Map<String, Object>> getRecentMatches(Long userId);

    Map<String, Object> getReplay(Long userId, Long matchId);

    List<Map<String, Object>> getLeaderboard(String scope);

    Map<String, Object> getMyStats(Long userId);
}
