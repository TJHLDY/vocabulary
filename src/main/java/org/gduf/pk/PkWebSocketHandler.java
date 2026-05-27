package org.gduf.pk;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.gduf.common.JwtUtils;
import org.gduf.entity.*;
import org.gduf.mapper.*;
import org.gduf.security.JwtAuthFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

@Component
public class PkWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(PkWebSocketHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long INVITE_COOLDOWN_MS = 15_000;
    private static final long INVITE_EXPIRE_MS = 20_000;
    private static final int QUESTIONS_PER_MATCH = 20;
    private static final int QUESTION_TIMEOUT_SEC = 15;

    private final ConcurrentHashMap<Long, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, InviteInfo> pendingInvites = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PkRoom> rooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> userRooms = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Long> inviteCooldowns = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> questionTimers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SeriesInfo> seriesById = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> seriesByMatchId = new ConcurrentHashMap<>();

    private final JwtUtils jwtUtils;
    private final Cet6WordMapper cet6WordMapper;
    private final PkScoringService pkScoringService;
    private final PkMatchMapper pkMatchMapper;
    private final PkMatchAnswerMapper pkMatchAnswerMapper;
    private final PkMatchQuestionMapper pkMatchQuestionMapper;
    private final PkUserStatsMapper pkUserStatsMapper;
    private final PkDailyStatsMapper pkDailyStatsMapper;
    private final PkPairDailyRewardMapper pkPairDailyRewardMapper;
    private final PkUserSettingMapper pkUserSettingMapper;
    private final UserMapper userMapper;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    public PkWebSocketHandler(JwtUtils jwtUtils, Cet6WordMapper cet6WordMapper,
                               PkScoringService pkScoringService, PkMatchMapper pkMatchMapper,
                               PkMatchAnswerMapper pkMatchAnswerMapper,
                               PkMatchQuestionMapper pkMatchQuestionMapper,
                               PkUserStatsMapper pkUserStatsMapper,
                               PkDailyStatsMapper pkDailyStatsMapper,
                               PkPairDailyRewardMapper pkPairDailyRewardMapper,
                               PkUserSettingMapper pkUserSettingMapper,
                               UserMapper userMapper) {
        this.jwtUtils = jwtUtils;
        this.cet6WordMapper = cet6WordMapper;
        this.pkScoringService = pkScoringService;
        this.pkMatchMapper = pkMatchMapper;
        this.pkMatchAnswerMapper = pkMatchAnswerMapper;
        this.pkMatchQuestionMapper = pkMatchQuestionMapper;
        this.pkUserStatsMapper = pkUserStatsMapper;
        this.pkDailyStatsMapper = pkDailyStatsMapper;
        this.pkPairDailyRewardMapper = pkPairDailyRewardMapper;
        this.pkUserSettingMapper = pkUserSettingMapper;
        this.userMapper = userMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = extractToken(session);
        if (token == null) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }
        try {
            Long userId = jwtUtils.getUserId(token);
            session.getAttributes().put("userId", userId);
            sessions.put(userId, session);
            JwtAuthFilter.LAST_ACTIVE.put(userId, System.currentTimeMillis());
            log.info("PK WebSocket connected: userId={}", userId);
        } catch (Exception e) {
            log.warn("PK WebSocket auth failed", e);
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) return;

        JwtAuthFilter.LAST_ACTIVE.put(userId, System.currentTimeMillis());

        @SuppressWarnings("unchecked")
        Map<String, Object> msg = MAPPER.readValue(message.getPayload(), Map.class);
        String type = (String) msg.get("type");
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) msg.getOrDefault("payload", Collections.emptyMap());

        try {
            switch (type) {
                case "pk.invite.send" -> handleInviteSend(userId, payload);
                case "pk.invite.accept" -> handleInviteAccept(userId, payload);
                case "pk.invite.reject" -> handleInviteReject(userId, payload);
                case "pk.buzz" -> handleBuzz(userId);
                case "pk.answer.submit" -> handleAnswer(userId, payload);
                case "pk.rematch.request" -> handleRematchRequest(userId, payload);
                case "pk.rematch.accept" -> handleRematchAccept(userId, payload);
                case "pk.rematch.reject" -> handleRematchReject(userId, payload);
                default -> log.debug("Unknown PK message type: {}", type);
            }
        } catch (Exception e) {
            log.error("PK message handling failed: userId={}, type={}", userId, type, e);
            sendToUser(userId, new PkMessage("pk.error", Map.of("message", "操作失败，请重试")));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = (Long) session.getAttributes().get("userId");
        if (userId == null) return;
        if (!sessions.remove(userId, session)) {
            log.info("Ignored stale PK WebSocket close: userId={}", userId);
            return;
        }
        removePendingInvitesForDisconnect(userId);

        Long matchId = userRooms.remove(userId);
        if (matchId != null) {
            PkRoom room = rooms.get(matchId);
            if (room != null) {
                handleForfeit(room, userId);
            }
        }
        log.info("PK WebSocket disconnected: userId={}", userId);
    }

    private void handleInviteSend(Long fromUserId, Map<String, Object> payload) {
        Number toUserIdNum = (Number) payload.get("toUserId");
        if (toUserIdNum == null) return;
        Long toUserId = toUserIdNum.longValue();
        if (toUserId.equals(fromUserId)) return;

        // Check cooldown
        Long lastInvite = inviteCooldowns.get(fromUserId);
        long now = System.currentTimeMillis();
        if (lastInvite != null && now - lastInvite < INVITE_COOLDOWN_MS) {
            long remaining = secondsLeft(lastInvite + INVITE_COOLDOWN_MS, now);
            sendToUser(fromUserId, new PkMessage("pk.invite.cooldown",
                    Map.of("message", "邀请冷却中，请稍后再试",
                            "remainingSeconds", remaining,
                            "cooldownSeconds", INVITE_COOLDOWN_MS / 1000,
                            "pending", false)));
            return;
        }

        // Check pending outgoing invite
        if (pendingInvites.containsKey(fromUserId)) {
            InviteInfo pending = pendingInvites.get(fromUserId);
            long remaining = pending != null ? secondsLeft(pending.createdAt + INVITE_EXPIRE_MS, now) : INVITE_EXPIRE_MS / 1000;
            sendToUser(fromUserId, new PkMessage("pk.invite.cooldown",
                    Map.of("message", "已有待处理的邀请",
                            "remainingSeconds", remaining,
                            "cooldownSeconds", INVITE_COOLDOWN_MS / 1000,
                            "pending", true)));
            return;
        }

        // Check target online
        if (!sessions.containsKey(toUserId)) {
            sendToUser(fromUserId, new PkMessage("pk.invite.error",
                    Map.of("message", "对方不在线")));
            return;
        }

        // Check target invisible
        PkUserSetting targetSetting = pkUserSettingMapper.selectByUserId(toUserId);
        if (targetSetting != null && Boolean.TRUE.equals(targetSetting.getInvisible())) {
            sendToUser(fromUserId, new PkMessage("pk.invite.error",
                    Map.of("message", "对方已隐身")));
            return;
        }

        // Check target busy
        if (userRooms.containsKey(toUserId)) {
            sendToUser(fromUserId, new PkMessage("pk.invite.error",
                    Map.of("message", "对方正在对战中")));
            return;
        }

        // Valid invite
        inviteCooldowns.put(fromUserId, now);
        InviteInfo info = new InviteInfo(fromUserId, toUserId, now);
        pendingInvites.put(fromUserId, info);

        // Expiry timer
        info.expiryFuture = scheduler.schedule(() -> expireInvite(fromUserId), INVITE_EXPIRE_MS, TimeUnit.MILLISECONDS);

        // Notify target
        Map<String, Object> incoming = new HashMap<>(userPayload(fromUserId, "from"));
        incoming.put("fromUserId", fromUserId);
        incoming.put("expiresIn", INVITE_EXPIRE_MS / 1000);
        sendToUser(toUserId, new PkMessage("pk.invite.incoming", incoming));
        sendToUser(fromUserId, new PkMessage("pk.invite.sent",
                Map.of("toUserId", toUserId,
                        "cooldownSeconds", INVITE_COOLDOWN_MS / 1000,
                        "expiresIn", INVITE_EXPIRE_MS / 1000)));
    }

    private void expireInvite(Long fromUserId) {
        InviteInfo info = pendingInvites.remove(fromUserId);
        if (info != null) {
            sendToUser(fromUserId, new PkMessage("pk.invite.expired", Map.of("toUserId", info.toUserId)));
            sendToUser(info.toUserId, new PkMessage("pk.invite.expired", Map.of("fromUserId", fromUserId)));
        }
    }

    private void handleInviteAccept(Long userId, Map<String, Object> payload) {
        Number fromUserIdNum = (Number) payload.get("fromUserId");
        if (fromUserIdNum == null) return;
        Long fromUserId = fromUserIdNum.longValue();

        InviteInfo info = pendingInvites.get(fromUserId);
        if (info == null || !info.toUserId.equals(userId)) {
            sendToUser(userId, new PkMessage("pk.invite.error",
                    Map.of("message", "邀请已过期")));
            return;
        }

        // Check both still online and not busy
        if (!sessions.containsKey(fromUserId) || !sessions.containsKey(userId)) {
            sendToUser(userId, new PkMessage("pk.invite.error",
                    Map.of("message", "对方已离线")));
            sendToUser(fromUserId, new PkMessage("pk.invite.error",
                    Map.of("message", "对方已离线")));
            return;
        }
        if (userRooms.containsKey(fromUserId) || userRooms.containsKey(userId)) {
            sendToUser(userId, new PkMessage("pk.invite.error",
                    Map.of("message", "对方正在对战中")));
            sendToUser(fromUserId, new PkMessage("pk.invite.error",
                    Map.of("message", "对方正在对战中")));
            return;
        }

        pendingInvites.remove(fromUserId);
        if (info.expiryFuture != null) info.expiryFuture.cancel(false);
        startMatch(fromUserId, userId);
    }

    private void handleInviteReject(Long userId, Map<String, Object> payload) {
        Number fromUserIdNum = (Number) payload.get("fromUserId");
        if (fromUserIdNum == null) return;
        Long fromUserId = fromUserIdNum.longValue();

        InviteInfo info = pendingInvites.remove(fromUserId);
        if (info != null && info.toUserId.equals(userId)) {
            if (info.expiryFuture != null) info.expiryFuture.cancel(false);
            sendToUser(fromUserId, new PkMessage("pk.invite.rejected",
                    Map.of("message", "对方拒绝了邀请")));
        }
    }

    private void handleRematchRequest(Long userId, Map<String, Object> payload) {
        String seriesId = (String) payload.get("seriesId");
        SeriesInfo seriesInfo = seriesId != null ? seriesById.get(seriesId) : null;
        if (seriesInfo == null) {
            sendToUser(userId, new PkMessage("pk.rematch.error", Map.of("message", "再战已失效")));
            return;
        }
        Long opponentId;
        synchronized (seriesInfo) {
            Long eligibleUserId = eligibleRematchRequester(seriesInfo);
            if (!userId.equals(eligibleUserId)) {
                sendToUser(userId, new PkMessage("pk.rematch.error", Map.of("message", "当前没有再战权")));
                return;
            }
            if (seriesInfo.awaitingRematch) {
                sendToUser(userId, new PkMessage("pk.rematch.error", Map.of("message", "再战邀请已发出")));
                return;
            }
            seriesInfo.awaitingRematch = true;
            seriesInfo.rematchRequesterId = userId;
            opponentId = seriesInfo.opponent(userId);
        }

        Map<String, Object> msg = new HashMap<>();
        msg.put("seriesId", seriesInfo.seriesId);
        msg.put("requesterUserId", userId);
        msg.put("requester", userPayload(userId, ""));
        sendToUser(userId, new PkMessage("pk.rematch.requested", msg));
        sendToUser(opponentId, new PkMessage("pk.rematch.requested", msg));
    }

    private void handleRematchAccept(Long userId, Map<String, Object> payload) {
        String seriesId = (String) payload.get("seriesId");
        SeriesInfo seriesInfo = seriesId != null ? seriesById.get(seriesId) : null;
        if (seriesInfo == null) {
            sendToUser(userId, new PkMessage("pk.rematch.error", Map.of("message", "再战已失效")));
            return;
        }
        Long requesterId;
        synchronized (seriesInfo) {
            requesterId = seriesInfo.rematchRequesterId;
            if (!seriesInfo.awaitingRematch || requesterId == null || userId.equals(requesterId)) {
                sendToUser(userId, new PkMessage("pk.rematch.error", Map.of("message", "没有可接受的再战")));
                return;
            }
            if (Boolean.FALSE.equals(seriesInfo.rematchRights.get(requesterId))) {
                sendToUser(userId, new PkMessage("pk.rematch.error", Map.of("message", "对方再战权已用完")));
                return;
            }
            if (userRooms.containsKey(seriesInfo.playerAId) || userRooms.containsKey(seriesInfo.playerBId)) {
                sendToUser(userId, new PkMessage("pk.rematch.error", Map.of("message", "双方还未准备好")));
                return;
            }
            if (!sessions.containsKey(seriesInfo.playerAId) || !sessions.containsKey(seriesInfo.playerBId)) {
                sendToUser(userId, new PkMessage("pk.rematch.error", Map.of("message", "对方已离线")));
                return;
            }
            seriesInfo.rematchRights.put(requesterId, false);
        }
        startMatch(seriesInfo.playerAId, seriesInfo.playerBId, seriesInfo);
    }

    private void handleRematchReject(Long userId, Map<String, Object> payload) {
        String seriesId = (String) payload.get("seriesId");
        SeriesInfo seriesInfo = seriesId != null ? seriesById.get(seriesId) : null;
        if (seriesInfo == null) return;
        Long requesterId;
        synchronized (seriesInfo) {
            requesterId = seriesInfo.rematchRequesterId;
            seriesInfo.awaitingRematch = false;
            seriesInfo.rematchRequesterId = null;
        }
        if (requesterId != null) {
            sendToUser(requesterId, new PkMessage("pk.rematch.rejected", Map.of("message", "对方拒绝了再战")));
        }
        sendToUser(userId, new PkMessage("pk.rematch.rejected", Map.of("message", "已拒绝再战")));
    }

    private void startMatch(Long playerAId, Long playerBId) {
        startMatch(playerAId, playerBId, new SeriesInfo(playerAId, playerBId));
    }

    private void startMatch(Long playerAId, Long playerBId, SeriesInfo seriesInfo) {
        // Generate questions
        List<Map<String, Object>> questions = generateMatchQuestions();

        // Create match record
        PkMatch match = new PkMatch();
        match.setPlayerAId(playerAId);
        match.setPlayerBId(playerBId);
        match.setQuestionsTotal(questions.size());
        match.setQuestionsCompleted(0);
        match.setStatus("playing");
        match.setPlayerAScore(0);
        match.setPlayerBScore(0);
        match.setStartedAt(new Date());
        pkMatchMapper.insert(match);
        saveMatchQuestions(match.getId(), questions);
        int seriesMatchNo;
        synchronized (seriesInfo) {
            if (seriesInfo.cleanupFuture != null) {
                seriesInfo.cleanupFuture.cancel(false);
                seriesInfo.cleanupFuture = null;
            }
            seriesInfo.matchNo++;
            seriesInfo.awaitingRematch = false;
            seriesInfo.rematchRequesterId = null;
            seriesMatchNo = seriesInfo.matchNo;
        }
        seriesById.put(seriesInfo.seriesId, seriesInfo);
        seriesByMatchId.put(match.getId(), seriesInfo.seriesId);

        PkRoom room = new PkRoom(match.getId(), playerAId, playerBId, questions);
        rooms.put(match.getId(), room);
        userRooms.put(playerAId, match.getId());
        userRooms.put(playerBId, match.getId());

        // Notify both players
        Map<String, Object> roomInfo = new HashMap<>();
        roomInfo.put("matchId", match.getId());
        roomInfo.put("playerAId", playerAId);
        roomInfo.put("playerBId", playerBId);
        roomInfo.put("questionsTotal", questions.size());
        roomInfo.put("seriesId", seriesInfo.seriesId);
        roomInfo.put("seriesMatchNo", seriesMatchNo);
        roomInfo.put("playerA", userPayload(playerAId, ""));
        roomInfo.put("playerB", userPayload(playerBId, ""));
        sendToUser(playerAId, new PkMessage("pk.room.started", roomInfo));
        sendToUser(playerBId, new PkMessage("pk.room.started", roomInfo));

        // Start first question
        room.nextQuestion();
        broadcastQuestion(room);
    }

    private void broadcastQuestion(PkRoom room) {
        Map<String, Object> question = room.getQuestion();
        if (question == null) return;

        Map<String, Object> qPayload = new HashMap<>(question);
        qPayload.put("scoreA", room.getScores().get(room.getPlayerAId()));
        qPayload.put("scoreB", room.getScores().get(room.getPlayerBId()));

        sendToUser(room.getPlayerAId(), new PkMessage("pk.question.started", qPayload));
        sendToUser(room.getPlayerBId(), new PkMessage("pk.question.started", qPayload));

        // Question timeout
        ScheduledFuture<?> timer = scheduler.schedule(() -> onQuestionTimeout(room.getMatchId()),
                QUESTION_TIMEOUT_SEC, TimeUnit.SECONDS);
        questionTimers.put(room.getMatchId(), timer);
    }

    private void onQuestionTimeout(Long matchId) {
        PkRoom room = rooms.get(matchId);
        if (room == null || room.isFinished()) return;

        // No one buzzed, skip question
        advanceQuestion(room);
    }

    private void handleBuzz(Long userId) {
        Long matchId = userRooms.get(userId);
        if (matchId == null) return;
        PkRoom room = rooms.get(matchId);
        if (room == null || room.isFinished()) return;

        boolean buzzed = room.tryBuzz(userId);
        if (buzzed) {
            // Cancel question timer
            ScheduledFuture<?> timer = questionTimers.remove(matchId);
            if (timer != null) timer.cancel(false);

            Long opponentId = room.getOpponent(userId);
            sendToUser(userId, new PkMessage("pk.buzz.result", Map.of("buzzedByMe", true)));
            sendToUser(opponentId, new PkMessage("pk.buzz.result",
                    Map.of("buzzedByMe", false, "buzzedByUserId", userId)));
        }
    }

    private void handleAnswer(Long userId, Map<String, Object> payload) {
        Long matchId = userRooms.get(userId);
        if (matchId == null) return;
        PkRoom room = rooms.get(matchId);
        if (room == null || room.isFinished()) return;
        if (!userId.equals(room.getBuzzUserId())) return;

        Map<String, Object> question = room.getQuestion();
        if (question == null) return;
        String chosen = (String) payload.get("chosen");
        String mode = (String) payload.getOrDefault("mode", question.get("mode"));

        Number wordIdNum = (Number) question.get("wordId");
        Long cetWordId = wordIdNum != null ? wordIdNum.longValue() : 0L;

        boolean correct = isAnswerCorrect(cetWordId, chosen, mode);
        int scoreDelta = correct ? 1 : -1;

        if (!room.recordAnswer(userId, chosen, correct, scoreDelta, cetWordId)) {
            return;
        }

        // Notify both players
        Map<String, Object> result = new HashMap<>();
        result.put("answerUserId", userId);
        result.put("username", Optional.ofNullable(userMapper.selectById(userId))
                .map(User::getUsername)
                .orElse("对手"));
        result.put("chosen", chosen);
        result.put("correct", correct);
        result.put("correctAnswer", correctAnswer(cetWordId, mode));
        result.put("scoreDelta", scoreDelta);
        result.put("scoreA", room.getScores().get(room.getPlayerAId()));
        result.put("scoreB", room.getScores().get(room.getPlayerBId()));

        sendToUser(room.getPlayerAId(), new PkMessage("pk.answer.result", result));
        sendToUser(room.getPlayerBId(), new PkMessage("pk.answer.result", result));

        // Delay before next question
        scheduler.schedule(() -> advanceQuestion(room), 2, TimeUnit.SECONDS);
    }

    private void advanceQuestion(PkRoom room) {
        ScheduledFuture<?> timer = questionTimers.remove(room.getMatchId());
        if (timer != null) timer.cancel(false);

        room.markQuestionFinished();
        room.resetBuzz();

        if (room.getCurrentQuestionIndex() + 1 >= room.getQuestionsTotal()) {
            finishMatch(room);
            return;
        }

        room.nextQuestion();
        broadcastQuestion(room);
    }

    private void finishMatch(PkRoom room) {
        if (!room.finishIfActive()) return;
        userRooms.remove(room.getPlayerAId());
        userRooms.remove(room.getPlayerBId());

        int scoreA = room.getScores().get(room.getPlayerAId());
        int scoreB = room.getScores().get(room.getPlayerBId());
        int streakA = room.getHighestStreaks().get(room.getPlayerAId());
        int streakB = room.getHighestStreaks().get(room.getPlayerBId());

        PkDailyStats dailyA = pkDailyStatsMapper.selectTodayByUserId(room.getPlayerAId());
        PkDailyStats dailyB = pkDailyStatsMapper.selectTodayByUserId(room.getPlayerBId());

        int baseGainedA = dailyA != null && dailyA.getBasePointsGained() != null ? dailyA.getBasePointsGained() : 0;
        int baseGainedB = dailyB != null && dailyB.getBasePointsGained() != null ? dailyB.getBasePointsGained() : 0;
        int streakGainedA = dailyA != null && dailyA.getStreakPointsGained() != null ? dailyA.getStreakPointsGained() : 0;
        int streakGainedB = dailyB != null && dailyB.getStreakPointsGained() != null ? dailyB.getStreakPointsGained() : 0;

        long lowId = Math.min(room.getPlayerAId(), room.getPlayerBId());
        long highId = Math.max(room.getPlayerAId(), room.getPlayerBId());
        int pairCount = pkPairDailyRewardMapper.selectTodayCount(lowId, highId);
        boolean pairLimit = pairCount >= 3;

        PkUserStats userStatsA = selectUserStats(room.getPlayerAId());
        PkUserStats userStatsB = selectUserStats(room.getPlayerBId());
        int pointsA = userStatsA != null ? userStatsA.getPoints() : 0;
        int pointsB = userStatsB != null ? userStatsB.getPoints() : 0;

        PkScoringService.Input input = new PkScoringService.Input(
                scoreA, scoreB, streakA, streakB,
                room.getQuestionsCompleted(),
                pointsA, pointsB,
                baseGainedA, baseGainedB,
                streakGainedA, streakGainedB,
                pairLimit, true);

        PkScoringService.Settlement settlement = pkScoringService.settle(input);

        boolean allowPositive = room.getQuestionsCompleted() >= PkScoringService.MIN_REWARD_QUESTIONS
                && !pairLimit;

        boolean aWin = scoreA > scoreB;
        boolean bWin = scoreB > scoreA;
        boolean draw = scoreA == scoreB;
        Long winnerId = aWin ? room.getPlayerAId() : (bWin ? room.getPlayerBId() : null);

        pkMatchMapper.updateSettled(room.getMatchId(), "finished", winnerId,
                room.getQuestionsCompleted(), scoreA, scoreB,
                settlement.playerAPointsDelta(), settlement.playerBPointsDelta());

        pkUserStatsMapper.upsert(room.getPlayerAId(), settlement.playerAPointsDelta(),
                aWin, !aWin && !draw, draw, streakA);
        pkUserStatsMapper.upsert(room.getPlayerBId(), settlement.playerBPointsDelta(),
                bWin, !bWin && !draw, draw, streakB);

        pkDailyStatsMapper.upsert(room.getPlayerAId(), settlement.playerAPointsDelta(),
                Math.max(0, settlement.playerABaseDelta()), settlement.playerAStreakDelta(),
                aWin, !aWin && !draw, draw, streakA);
        pkDailyStatsMapper.upsert(room.getPlayerBId(), settlement.playerBPointsDelta(),
                Math.max(0, settlement.playerBBaseDelta()), settlement.playerBStreakDelta(),
                bWin, !bWin && !draw, draw, streakB);

        if (allowPositive && hasPositiveReward(settlement)) {
            pkPairDailyRewardMapper.incrementToday(lowId, highId);
        }

        for (PkMatchAnswer answer : room.getAnswers()) {
            pkMatchAnswerMapper.insert(answer);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("matchId", room.getMatchId());
        result.put("scoreA", scoreA);
        result.put("scoreB", scoreB);
        result.put("pointsDeltaA", settlement.playerAPointsDelta());
        result.put("pointsDeltaB", settlement.playerBPointsDelta());
        result.put("winnerUserId", winnerId);
        result.putAll(seriesSettlement(room, winnerId));
        result.putAll(winnerDeclaration(room, winnerId, scoreA, scoreB, userStatsA, userStatsB));

        sendToUser(room.getPlayerAId(), new PkMessage("pk.match.finished", result));
        sendToUser(room.getPlayerBId(), new PkMessage("pk.match.finished", result));

        rooms.remove(room.getMatchId());
    }

    private void handleForfeit(PkRoom room, Long leaverId) {
        if (!room.finishIfActive()) return;
        Long opponentId = room.getOpponent(leaverId);
        userRooms.remove(leaverId);
        userRooms.remove(opponentId);

        sendToUser(opponentId, new PkMessage("pk.opponent.left",
                Map.of("message", "对手已断开连接")));

        int scoreA = room.getScores().get(room.getPlayerAId());
        int scoreB = room.getScores().get(room.getPlayerBId());
        int streakA = room.getHighestStreaks().get(room.getPlayerAId());
        int streakB = room.getHighestStreaks().get(room.getPlayerBId());

        if (room.getQuestionsCompleted() >= PkScoringService.MIN_REWARD_QUESTIONS) {
            PkDailyStats dailyA = pkDailyStatsMapper.selectTodayByUserId(room.getPlayerAId());
            PkDailyStats dailyB = pkDailyStatsMapper.selectTodayByUserId(room.getPlayerBId());

            int baseGainedA = dailyA != null && dailyA.getBasePointsGained() != null ? dailyA.getBasePointsGained() : 0;
            int baseGainedB = dailyB != null && dailyB.getBasePointsGained() != null ? dailyB.getBasePointsGained() : 0;
            int streakGainedA = dailyA != null && dailyA.getStreakPointsGained() != null ? dailyA.getStreakPointsGained() : 0;
            int streakGainedB = dailyB != null && dailyB.getStreakPointsGained() != null ? dailyB.getStreakPointsGained() : 0;

            long lowId = Math.min(room.getPlayerAId(), room.getPlayerBId());
            long highId = Math.max(room.getPlayerAId(), room.getPlayerBId());
            int pairCount = pkPairDailyRewardMapper.selectTodayCount(lowId, highId);
            boolean pairLimit = pairCount >= 3;

            PkUserStats userStatsA = selectUserStats(room.getPlayerAId());
            PkUserStats userStatsB = selectUserStats(room.getPlayerBId());
            int pointsA = userStatsA != null ? userStatsA.getPoints() : 0;
            int pointsB = userStatsB != null ? userStatsB.getPoints() : 0;

            PkScoringService.Input input = new PkScoringService.Input(
                    scoreA, scoreB, streakA, streakB,
                    room.getQuestionsCompleted(),
                    pointsA, pointsB,
                    baseGainedA, baseGainedB,
                    streakGainedA, streakGainedB,
                    pairLimit, false);

            boolean isLeaverA = leaverId.equals(room.getPlayerAId());
            PkScoringService.Settlement settlement = pkScoringService.settleForfeit(input, isLeaverA);

            boolean allowPositive = room.getQuestionsCompleted() >= PkScoringService.MIN_REWARD_QUESTIONS
                    && !pairLimit;

            boolean aWin = !isLeaverA;
            boolean bWin = isLeaverA;
            boolean draw = false;
            Long winnerId = opponentId;

            pkMatchMapper.updateSettled(room.getMatchId(), "forfeit", winnerId,
                    room.getQuestionsCompleted(), scoreA, scoreB,
                    settlement.playerAPointsDelta(), settlement.playerBPointsDelta());

            pkUserStatsMapper.upsert(room.getPlayerAId(), settlement.playerAPointsDelta(),
                    aWin, !aWin && !draw, draw, streakA);
            pkUserStatsMapper.upsert(room.getPlayerBId(), settlement.playerBPointsDelta(),
                    bWin, !bWin && !draw, draw, streakB);

            pkDailyStatsMapper.upsert(room.getPlayerAId(), settlement.playerAPointsDelta(),
                    Math.max(0, settlement.playerABaseDelta()), settlement.playerAStreakDelta(),
                    aWin, !aWin && !draw, draw, streakA);
            pkDailyStatsMapper.upsert(room.getPlayerBId(), settlement.playerBPointsDelta(),
                    Math.max(0, settlement.playerBBaseDelta()), settlement.playerBStreakDelta(),
                    bWin, !bWin && !draw, draw, streakB);

            if (allowPositive && hasPositiveReward(settlement)) {
                pkPairDailyRewardMapper.incrementToday(lowId, highId);
            }

            for (PkMatchAnswer answer : room.getAnswers()) {
                pkMatchAnswerMapper.insert(answer);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("matchId", room.getMatchId());
            result.put("scoreA", scoreA);
            result.put("scoreB", scoreB);
            result.put("pointsDeltaA", settlement.playerAPointsDelta());
            result.put("pointsDeltaB", settlement.playerBPointsDelta());
            result.put("winnerUserId", winnerId);
            result.putAll(seriesSettlement(room, winnerId));
            result.putAll(winnerDeclaration(room, winnerId, scoreA, scoreB, userStatsA, userStatsB));

            sendToUser(room.getPlayerAId(), new PkMessage("pk.match.finished", result));
            sendToUser(room.getPlayerBId(), new PkMessage("pk.match.finished", result));
        } else {
            boolean isLeaverA = leaverId.equals(room.getPlayerAId());
            int leaverScore = isLeaverA ? scoreA : scoreB;
            int opponentScore = isLeaverA ? scoreB : scoreA;
            int leaverStreak = isLeaverA ? streakA : streakB;
            int opponentStreak = isLeaverA ? streakB : streakA;

            Long leaverIdForStats = leaverId;
            Long opponentIdForStats = opponentId;

            PkUserStats leaverStats = selectUserStats(leaverIdForStats);
            PkUserStats opponentStats = selectUserStats(opponentIdForStats);
            int leaverPoints = leaverStats != null ? leaverStats.getPoints() : 0;
            int opponentPoints = opponentStats != null ? opponentStats.getPoints() : 0;

            int leaverDelta = leaverScore < 0 ? -Math.min(leaverPoints, Math.abs(leaverScore)) : 0;
            int opponentDelta = 0;

            boolean oppWin = true;
            boolean oppLoss = false;
            boolean oppDraw = false;

            pkMatchMapper.updateSettled(room.getMatchId(), "forfeit", opponentId,
                    room.getQuestionsCompleted(), scoreA, scoreB,
                    isLeaverA ? leaverDelta : opponentDelta,
                    isLeaverA ? opponentDelta : leaverDelta);

            pkUserStatsMapper.upsert(leaverIdForStats, leaverDelta, false, true, false, leaverStreak);
            pkUserStatsMapper.upsert(opponentIdForStats, opponentDelta, oppWin, oppLoss, oppDraw, opponentStreak);

            pkDailyStatsMapper.upsert(leaverIdForStats, leaverDelta, 0, 0, false, true, false, leaverStreak);
            pkDailyStatsMapper.upsert(opponentIdForStats, opponentDelta, 0, 0, oppWin, oppLoss, oppDraw, opponentStreak);

            for (PkMatchAnswer answer : room.getAnswers()) {
                pkMatchAnswerMapper.insert(answer);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("matchId", room.getMatchId());
            result.put("scoreA", scoreA);
            result.put("scoreB", scoreB);
            result.put("pointsDeltaA", isLeaverA ? leaverDelta : opponentDelta);
            result.put("pointsDeltaB", isLeaverA ? opponentDelta : leaverDelta);
            result.put("winnerUserId", opponentId);
            result.putAll(seriesSettlement(room, opponentId));
            PkUserStats statsAForDeclaration = isLeaverA ? leaverStats : opponentStats;
            PkUserStats statsBForDeclaration = isLeaverA ? opponentStats : leaverStats;
            result.putAll(winnerDeclaration(room, opponentId, scoreA, scoreB,
                    statsAForDeclaration, statsBForDeclaration));

            sendToUser(room.getPlayerAId(), new PkMessage("pk.match.finished", result));
            sendToUser(room.getPlayerBId(), new PkMessage("pk.match.finished", result));
        }

        rooms.remove(room.getMatchId());
    }

    private List<Map<String, Object>> generateMatchQuestions() {
        List<Cet6Word> words = cet6WordMapper.selectRandomExcluding(null, QUESTIONS_PER_MATCH);
        return buildQuestions(words);
    }

    private void saveMatchQuestions(Long matchId, List<Map<String, Object>> questions) {
        for (int i = 0; i < questions.size(); i++) {
            Map<String, Object> q = questions.get(i);
            Number wordId = (Number) q.get("wordId");
            if (wordId == null) continue;

            PkMatchQuestion question = new PkMatchQuestion();
            question.setMatchId(matchId);
            question.setQuestionIndex(i);
            question.setCetWordId(wordId.longValue());
            question.setMode((String) q.get("mode"));
            question.setOptionsJson(toJson(q));
            pkMatchQuestionMapper.insert(question);
        }
    }

    private List<Map<String, Object>> buildQuestions(List<Cet6Word> words) {
        List<Map<String, Object>> questions = new ArrayList<>();
        boolean en2cn = false;
        for (Cet6Word correct : words) {
            en2cn = !en2cn;
            Map<String, Object> q = new HashMap<>();
            q.put("wordId", correct.getId());
            q.put("mode", en2cn ? "en2cn" : "cn2en");

            List<Cet6Word> distractors = cet6WordMapper.selectRandomByPos(
                    correct.getPos(), correct.getWord(), 3);
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

            if (en2cn) {
                q.put("word", correct.getWord());
                q.put("meaning", correct.getMeaning());
                q.put("options", optionMeanings);
                q.put("optionWords", optionWords);
            } else {
                q.put("meaning", correct.getMeaning());
                q.put("options", optionWords);
                q.put("optionMeanings", optionMeanings);
            }
            questions.add(q);
        }
        return questions;
    }

    private boolean isAnswerCorrect(Long cetWordId, String chosen, String mode) {
        if (chosen == null || cetWordId == null) return false;
        Cet6Word word = cet6WordMapper.selectById(cetWordId);
        if (word == null) return false;
        if ("en2cn".equals(mode)) {
            return word.getMeaning() != null && word.getMeaning().equals(chosen);
        } else {
            return word.getWord() != null && word.getWord().equalsIgnoreCase(chosen.trim());
        }
    }

    private String correctAnswer(Long cetWordId, String mode) {
        if (cetWordId == null) return "";
        Cet6Word word = cet6WordMapper.selectById(cetWordId);
        if (word == null) return "";
        return "en2cn".equals(mode) ? word.getMeaning() : word.getWord();
    }

    private PkUserStats selectUserStats(Long userId) {
        return pkUserStatsMapper.selectOne(new QueryWrapper<PkUserStats>().eq("user_id", userId));
    }

    private Map<String, Object> userPayload(Long userId, String prefix) {
        User user = userMapper.selectById(userId);
        Map<String, Object> payload = new HashMap<>();
        if (prefix == null || prefix.isBlank()) {
            payload.put("userId", userId);
            payload.put("username", user != null ? user.getUsername() : ("用户" + userId));
            payload.put("avatarUrl", user != null ? user.getAvatarUrl() : null);
        } else {
            payload.put(prefix + "Username", user != null ? user.getUsername() : ("用户" + userId));
            payload.put(prefix + "AvatarUrl", user != null ? user.getAvatarUrl() : null);
        }
        return payload;
    }

    private boolean hasPositiveReward(PkScoringService.Settlement settlement) {
        return settlement.playerABaseDelta() > 0
                || settlement.playerBBaseDelta() > 0
                || settlement.playerAStreakDelta() > 0
                || settlement.playerBStreakDelta() > 0;
    }

    private Map<String, Object> seriesSettlement(PkRoom room, Long winnerId) {
        Map<String, Object> result = new HashMap<>();
        String seriesId = seriesByMatchId.get(room.getMatchId());
        SeriesInfo seriesInfo = seriesId != null ? seriesById.get(seriesId) : null;
        if (seriesInfo == null) {
            return result;
        }

        Long canRequestUserId;
        synchronized (seriesInfo) {
            if (winnerId != null && !seriesInfo.settledMatchIds.contains(room.getMatchId())) {
                if (winnerId.equals(seriesInfo.playerAId)) {
                    seriesInfo.playerAWins++;
                } else if (winnerId.equals(seriesInfo.playerBId)) {
                    seriesInfo.playerBWins++;
                }
                if (seriesInfo.firstWinnerId == null) {
                    seriesInfo.firstWinnerId = winnerId;
                }
                seriesInfo.lastWinnerId = winnerId;
                seriesInfo.settledMatchIds.add(room.getMatchId());
            }
            canRequestUserId = eligibleRematchRequester(seriesInfo);
            scheduleSeriesCleanup(seriesInfo);

            result.put("seriesId", seriesInfo.seriesId);
            result.put("seriesMatchNo", seriesInfo.matchNo);
            result.put("seriesPlayerAWins", seriesInfo.playerAWins);
            result.put("seriesPlayerBWins", seriesInfo.playerBWins);
            result.put("canRequestRematchUserId", canRequestUserId);
            result.put("maxSeriesMatches", 3);
        }
        return result;
    }

    private Long eligibleRematchRequester(SeriesInfo seriesInfo) {
        if (seriesInfo.matchNo >= 3 || seriesInfo.playerAWins >= 2 || seriesInfo.playerBWins >= 2) {
            return null;
        }
        if (seriesInfo.lastWinnerId == null) {
            return null;
        }
        Long candidate = null;
        if (seriesInfo.matchNo == 1) {
            candidate = seriesInfo.opponent(seriesInfo.lastWinnerId);
        } else if (seriesInfo.matchNo == 2
                && seriesInfo.playerAWins == 1
                && seriesInfo.playerBWins == 1) {
            candidate = seriesInfo.firstWinnerId;
        }
        if (candidate == null || Boolean.FALSE.equals(seriesInfo.rematchRights.get(candidate))) {
            return null;
        }
        return candidate;
    }

    private void scheduleSeriesCleanup(SeriesInfo seriesInfo) {
        if (seriesInfo.cleanupFuture != null) {
            seriesInfo.cleanupFuture.cancel(false);
        }
        seriesInfo.cleanupFuture = scheduler.schedule(() -> {
            seriesById.remove(seriesInfo.seriesId);
            for (Long matchId : seriesInfo.settledMatchIds) {
                seriesByMatchId.remove(matchId);
            }
        }, 10, TimeUnit.MINUTES);
    }

    private Map<String, Object> winnerDeclaration(PkRoom room, Long winnerId, int scoreA, int scoreB,
                                                   PkUserStats statsA, PkUserStats statsB) {
        Map<String, Object> result = new HashMap<>();
        result.put("declarationType", "");
        result.put("winnerDeclaration", "");
        if (winnerId == null) {
            return result;
        }

        boolean winnerIsA = winnerId.equals(room.getPlayerAId());
        int winnerScore = winnerIsA ? scoreA : scoreB;
        int loserScore = winnerIsA ? scoreB : scoreA;
        PkUserStats winnerStats = winnerIsA ? statsA : statsB;
        Long loserId = winnerIsA ? room.getPlayerBId() : room.getPlayerAId();
        Long previousWinner = pkMatchMapper.selectPreviousWinnerBetween(winnerId, loserId, room.getMatchId());

        String type;
        if (winnerStats == null || winnerStats.getWins() == null || winnerStats.getWins() == 0) {
            type = "firstWin";
        } else if (previousWinner != null && previousWinner.equals(loserId)) {
            type = "revenge";
        } else if (winnerScore > loserScore && loserScore <= 0) {
            type = "sweep";
        } else {
            type = "finalWin";
        }

        PkUserSetting setting = pkUserSettingMapper.selectByUserId(winnerId);
        String declaration = declarationForType(setting, type);
        if (declaration == null || declaration.isBlank()) {
            declaration = switch (type) {
                case "firstWin" -> "初战告捷";
                case "revenge" -> "这局拿回来了";
                case "sweep" -> "完胜收笔";
                default -> "胜负已定";
            };
        }
        result.put("declarationType", type);
        result.put("winnerDeclaration", declaration);
        result.put("winnerUsername", Optional.ofNullable(userMapper.selectById(winnerId))
                .map(User::getUsername)
                .orElse("胜者"));
        return result;
    }

    private String declarationForType(PkUserSetting setting, String type) {
        if (setting == null) return "";
        return switch (type) {
            case "firstWin" -> setting.getFirstWinDeclaration();
            case "revenge" -> setting.getRevengeDeclaration();
            case "sweep" -> setting.getSweepDeclaration();
            default -> setting.getFinalWinDeclaration();
        };
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    private void sendToUser(Long userId, PkMessage message) {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(MAPPER.writeValueAsString(message)));
                }
            } catch (IOException e) {
                log.warn("Failed to send PK message to userId={}", userId, e);
            }
        }
    }

    private long secondsLeft(long endAtMs, long nowMs) {
        return Math.max(1, (long) Math.ceil((endAtMs - nowMs) / 1000.0));
    }

    private void removePendingInvitesForDisconnect(Long userId) {
        InviteInfo outgoing = pendingInvites.remove(userId);
        if (outgoing != null) {
            if (outgoing.expiryFuture != null) outgoing.expiryFuture.cancel(false);
            sendToUser(outgoing.toUserId, new PkMessage("pk.invite.expired",
                    Map.of("fromUserId", userId, "message", "邀请已失效")));
        }

        for (Map.Entry<Long, InviteInfo> entry : pendingInvites.entrySet()) {
            InviteInfo info = entry.getValue();
            if (info != null && userId.equals(info.toUserId)
                    && pendingInvites.remove(entry.getKey(), info)) {
                if (info.expiryFuture != null) info.expiryFuture.cancel(false);
                sendToUser(info.fromUserId, new PkMessage("pk.invite.expired",
                        Map.of("toUserId", userId, "message", "对方已离线")));
            }
        }
    }

    private String extractToken(WebSocketSession session) {
        String query = session.getUri() != null ? session.getUri().getQuery() : null;
        if (query != null) {
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    return param.substring(6);
                }
            }
        }
        return null;
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try { session.close(status); } catch (IOException ignored) {}
    }

    private static class InviteInfo {
        final Long fromUserId;
        final Long toUserId;
        final long createdAt;
        ScheduledFuture<?> expiryFuture;

        InviteInfo(Long fromUserId, Long toUserId, long createdAt) {
            this.fromUserId = fromUserId;
            this.toUserId = toUserId;
            this.createdAt = createdAt;
        }
    }

    private static class SeriesInfo {
        final String seriesId = UUID.randomUUID().toString();
        final Long playerAId;
        final Long playerBId;
        final Map<Long, Boolean> rematchRights = new HashMap<>();
        final Set<Long> settledMatchIds = new HashSet<>();
        int matchNo = 0;
        int playerAWins = 0;
        int playerBWins = 0;
        Long firstWinnerId;
        Long lastWinnerId;
        boolean awaitingRematch = false;
        Long rematchRequesterId;
        ScheduledFuture<?> cleanupFuture;

        SeriesInfo(Long playerAId, Long playerBId) {
            this.playerAId = playerAId;
            this.playerBId = playerBId;
            rematchRights.put(playerAId, true);
            rematchRights.put(playerBId, true);
        }

        Long opponent(Long userId) {
            return userId.equals(playerAId) ? playerBId : playerAId;
        }
    }
}
