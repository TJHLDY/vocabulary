package org.gduf.pk;

import org.gduf.entity.PkMatchAnswer;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class PkRoom {
    private final Long matchId;
    private final Long playerAId;
    private final Long playerBId;
    private final List<Map<String, Object>> questions;
    private final Map<Long, Integer> scores = new HashMap<>();
    private final Map<Long, Integer> currentStreaks = new HashMap<>();
    private final Map<Long, Integer> highestStreaks = new HashMap<>();
    private final List<PkMatchAnswer> answers = new ArrayList<>();
    private int currentQuestionIndex = -1;
    private Long buzzUserId;
    private final AtomicBoolean buzzLock = new AtomicBoolean(false);
    private final AtomicBoolean answerLock = new AtomicBoolean(false);
    private int questionsFinished = 0;
    private String status = "playing";
    private final Date createdAt = new Date();

    public PkRoom(Long matchId, Long playerAId, Long playerBId, List<Map<String, Object>> questions) {
        this.matchId = matchId;
        this.playerAId = playerAId;
        this.playerBId = playerBId;
        this.questions = questions;
        this.scores.put(playerAId, 0);
        this.scores.put(playerBId, 0);
        this.currentStreaks.put(playerAId, 0);
        this.currentStreaks.put(playerBId, 0);
        this.highestStreaks.put(playerAId, 0);
        this.highestStreaks.put(playerBId, 0);
    }

    public boolean tryBuzz(Long userId) {
        if (buzzLock.compareAndSet(false, true)) {
            this.buzzUserId = userId;
            return true;
        }
        return false;
    }

    public void resetBuzz() {
        buzzUserId = null;
        buzzLock.set(false);
        answerLock.set(false);
    }

    public boolean recordAnswer(Long answerUserId, String chosen, boolean correct, int scoreDelta, Long cetWordId) {
        if (!answerLock.compareAndSet(false, true)) {
            return false;
        }

        PkMatchAnswer answer = new PkMatchAnswer();
        answer.setMatchId(matchId);
        answer.setQuestionIndex(currentQuestionIndex);
        answer.setCetWordId(cetWordId);
        answer.setBuzzUserId(buzzUserId);
        answer.setAnswerUserId(answerUserId);
        answer.setChosen(chosen);
        answer.setCorrect(correct);
        answer.setScoreDelta(scoreDelta);
        answer.setAnsweredAt(new Date());
        answers.add(answer);

        int newScore = scores.getOrDefault(answerUserId, 0) + scoreDelta;
        scores.put(answerUserId, newScore);

        if (correct) {
            int streak = currentStreaks.getOrDefault(answerUserId, 0) + 1;
            currentStreaks.put(answerUserId, streak);
            highestStreaks.put(answerUserId, Math.max(highestStreaks.getOrDefault(answerUserId, 0), streak));
        } else {
            currentStreaks.put(answerUserId, 0);
        }
        return true;
    }

    public Map<String, Object> getQuestion() {
        if (currentQuestionIndex < 0 || currentQuestionIndex >= questions.size()) return null;
        Map<String, Object> q = new HashMap<>(questions.get(currentQuestionIndex));
        q.put("questionIndex", currentQuestionIndex);
        q.put("questionTotal", questions.size());
        return q;
    }

    public int nextQuestion() {
        currentQuestionIndex++;
        if (currentQuestionIndex >= questions.size()) {
            status = "finished";
        }
        return currentQuestionIndex;
    }

    public Long getMatchId() { return matchId; }
    public Long getPlayerAId() { return playerAId; }
    public Long getPlayerBId() { return playerBId; }
    public Long getOpponent(Long userId) {
        return userId.equals(playerAId) ? playerBId : playerAId;
    }
    public Map<Long, Integer> getScores() { return scores; }
    public Map<Long, Integer> getHighestStreaks() { return highestStreaks; }
    public Long getBuzzUserId() { return buzzUserId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public synchronized boolean finishIfActive() {
        if ("finished".equals(status)) {
            return false;
        }
        status = "finished";
        return true;
    }
    public int getCurrentQuestionIndex() { return currentQuestionIndex; }
    public int getQuestionsTotal() { return questions.size(); }
    public int getQuestionsCompleted() { return questionsFinished; }
    public void markQuestionFinished() { questionsFinished++; }
    public List<PkMatchAnswer> getAnswers() { return answers; }
    public Date getCreatedAt() { return createdAt; }
    public boolean isFinished() { return "finished".equals(status); }
}
