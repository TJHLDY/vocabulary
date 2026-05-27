package org.gduf.pk;

import org.springframework.stereotype.Service;

@Service
public class PkScoringService {
    public static final int DAILY_BASE_CAP = 60;
    public static final int DAILY_STREAK_CAP = 80;
    public static final int MIN_REWARD_QUESTIONS = 10;

    public record Input(
            int playerAScore,
            int playerBScore,
            int playerAHighestStreak,
            int playerBHighestStreak,
            int completedQuestions,
            int playerACurrentPoints,
            int playerBCurrentPoints,
            int playerADailyBaseGained,
            int playerBDailyBaseGained,
            int playerADailyStreakGained,
            int playerBDailyStreakGained,
            boolean pairRewardLimitReached,
            boolean matchCompleted) {
    }

    public record Settlement(
            int playerAPointsDelta,
            int playerBPointsDelta,
            int playerABaseDelta,
            int playerBBaseDelta,
            int playerAStreakDelta,
            int playerBStreakDelta) {
    }

    public Settlement settle(Input input) {
        boolean allowPositive = input.completedQuestions() >= MIN_REWARD_QUESTIONS
                && !input.pairRewardLimitReached();

        int aBase = baseDelta(input.playerAScore(), input.playerBScore(), allowPositive);
        int bBase = baseDelta(input.playerBScore(), input.playerAScore(), allowPositive);

        if (aBase > 0) {
            aBase = Math.min(aBase, Math.max(0, DAILY_BASE_CAP - input.playerADailyBaseGained()));
        }
        if (bBase > 0) {
            bBase = Math.min(bBase, Math.max(0, DAILY_BASE_CAP - input.playerBDailyBaseGained()));
        }

        int aStreak = allowPositive ? streakBonus(input.playerAHighestStreak()) : 0;
        int bStreak = allowPositive ? streakBonus(input.playerBHighestStreak()) : 0;

        if (aStreak > 0) {
            aStreak = Math.min(aStreak, Math.max(0, DAILY_STREAK_CAP - input.playerADailyStreakGained()));
        }
        if (bStreak > 0) {
            bStreak = Math.min(bStreak, Math.max(0, DAILY_STREAK_CAP - input.playerBDailyStreakGained()));
        }

        int aDelta = floorPenalty(input.playerACurrentPoints(), aBase + aStreak);
        int bDelta = floorPenalty(input.playerBCurrentPoints(), bBase + bStreak);
        return new Settlement(aDelta, bDelta, aBase, bBase, aStreak, bStreak);
    }

    public Settlement settleForfeit(Input input, boolean playerAForfeited) {
        boolean allowPositive = input.completedQuestions() >= MIN_REWARD_QUESTIONS
                && !input.pairRewardLimitReached();

        int aBase = forfeitBaseDelta(input.playerAScore(), !playerAForfeited, playerAForfeited, allowPositive);
        int bBase = forfeitBaseDelta(input.playerBScore(), playerAForfeited, !playerAForfeited, allowPositive);
        if (aBase > 0) {
            aBase = Math.min(aBase, Math.max(0, DAILY_BASE_CAP - input.playerADailyBaseGained()));
        }
        if (bBase > 0) {
            bBase = Math.min(bBase, Math.max(0, DAILY_BASE_CAP - input.playerBDailyBaseGained()));
        }

        int aStreak = !playerAForfeited && allowPositive ? streakBonus(input.playerAHighestStreak()) : 0;
        int bStreak = playerAForfeited && allowPositive ? streakBonus(input.playerBHighestStreak()) : 0;
        if (aStreak > 0) {
            aStreak = Math.min(aStreak, Math.max(0, DAILY_STREAK_CAP - input.playerADailyStreakGained()));
        }
        if (bStreak > 0) {
            bStreak = Math.min(bStreak, Math.max(0, DAILY_STREAK_CAP - input.playerBDailyStreakGained()));
        }

        int aDelta = floorPenalty(input.playerACurrentPoints(), aBase + aStreak);
        int bDelta = floorPenalty(input.playerBCurrentPoints(), bBase + bStreak);
        return new Settlement(aDelta, bDelta, aBase, bBase, aStreak, bStreak);
    }

    private int baseDelta(int score, int opponentScore, boolean allowPositive) {
        if (score < 0) return score;
        if (!allowPositive) return 0;
        if (score > opponentScore) return score > 0 ? 10 : 0;
        if (score == opponentScore) return score;
        return score > 0 ? score : 0;
    }

    private int forfeitBaseDelta(int score, boolean winner, boolean forfeited, boolean allowPositive) {
        if (score < 0) return score;
        if (forfeited || !allowPositive) return 0;
        if (winner) return score > 0 ? 10 : 0;
        return score > 0 ? score : 0;
    }

    private int streakBonus(int streak) {
        if (streak >= 8) return 8;
        if (streak >= 5) return 4;
        if (streak >= 3) return 2;
        return 0;
    }

    private int floorPenalty(int currentPoints, int delta) {
        if (delta >= 0) return delta;
        return -Math.min(currentPoints, Math.abs(delta));
    }
}
