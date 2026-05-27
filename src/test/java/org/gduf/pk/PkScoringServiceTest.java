package org.gduf.pk;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PkScoringServiceTest {

    private final PkScoringService service = new PkScoringService();

    @Test
    void winnerGetsBaseRewardAndHighestStreakBonus() {
        PkScoringService.Settlement settlement = service.settle(new PkScoringService.Input(
                7, 3, 5, 3, 20, 10, 10, 0, 0, 0, 0, false, true));

        assertThat(settlement.playerAPointsDelta()).isEqualTo(14);
        assertThat(settlement.playerBPointsDelta()).isEqualTo(5);
        assertThat(settlement.playerABaseDelta()).isEqualTo(10);
        assertThat(settlement.playerBBaseDelta()).isEqualTo(3);
        assertThat(settlement.playerAStreakDelta()).isEqualTo(4);
        assertThat(settlement.playerBStreakDelta()).isEqualTo(2);
    }

    @Test
    void loserWithNegativeScoreLosesPointsButTotalNeverDropsBelowZero() {
        PkScoringService.Settlement settlement = service.settle(new PkScoringService.Input(
                4, -6, 0, 0, 20, 4, 2, 0, 0, 0, 0, false, true));

        assertThat(settlement.playerAPointsDelta()).isEqualTo(10);
        assertThat(settlement.playerBPointsDelta()).isEqualTo(-2);
    }

    @Test
    void dailyBaseCapLimitsPositiveBaseButNotNegativePenalty() {
        PkScoringService.Settlement settlement = service.settle(new PkScoringService.Input(
                5, -3, 0, 0, 20, 100, 100, 58, 0, 0, 0, false, true));

        assertThat(settlement.playerABaseDelta()).isEqualTo(2);
        assertThat(settlement.playerBBaseDelta()).isEqualTo(-3);
        assertThat(settlement.playerAPointsDelta()).isEqualTo(2);
        assertThat(settlement.playerBPointsDelta()).isEqualTo(-3);
    }

    @Test
    void positiveRewardsAreBlockedWhenMatchHasTooFewQuestions() {
        PkScoringService.Settlement settlement = service.settle(new PkScoringService.Input(
                5, 2, 8, 5, 9, 0, 0, 0, 0, 0, 0, false, true));

        assertThat(settlement.playerAPointsDelta()).isEqualTo(0);
        assertThat(settlement.playerBPointsDelta()).isEqualTo(0);
    }

    @Test
    void pairRewardLimitBlocksPositiveRewardsButKeepsNegativePenalty() {
        PkScoringService.Settlement settlement = service.settle(new PkScoringService.Input(
                5, -2, 8, 0, 20, 100, 100, 0, 0, 0, 0, true, true));

        assertThat(settlement.playerAPointsDelta()).isEqualTo(0);
        assertThat(settlement.playerBPointsDelta()).isEqualTo(-2);
    }

    @Test
    void dailyBaseCapAppliedSeparatelyPerPlayer() {
        PkScoringService.Settlement settlement = service.settle(new PkScoringService.Input(
                7, 6, 0, 0, 20, 100, 100,
                58, 0, 0, 0, false, true));

        assertThat(settlement.playerABaseDelta()).isEqualTo(2);
        assertThat(settlement.playerBBaseDelta()).isEqualTo(6);
    }

    @Test
    void dailyStreakCapLimitsStreakBonus() {
        PkScoringService.Settlement settlement = service.settle(new PkScoringService.Input(
                7, 3, 8, 3, 20, 100, 100,
                0, 0, 78, 0, false, true));

        assertThat(settlement.playerAStreakDelta()).isEqualTo(2);
        assertThat(settlement.playerBStreakDelta()).isEqualTo(2);
    }

    @Test
    void negativeBasePenaltyDoesNotCountTowardDailyBaseCap() {
        PkScoringService.Settlement settlement = service.settle(new PkScoringService.Input(
                -3, 7, 0, 0, 20, 100, 100,
                58, 0, 0, 0, false, true));

        assertThat(settlement.playerABaseDelta()).isEqualTo(-3);
        assertThat(settlement.playerBBaseDelta()).isEqualTo(10);
    }

    @Test
    void forfeitWithEnoughQuestionsSettlesWhenMatchNotCompleted() {
        PkScoringService.Settlement settlement = service.settle(new PkScoringService.Input(
                5, 2, 3, 0, 15, 100, 100,
                0, 0, 0, 0, false, false));

        assertThat(settlement.playerAPointsDelta()).isEqualTo(12);
        assertThat(settlement.playerBPointsDelta()).isEqualTo(2);
    }

    @Test
    void forfeitWithFewQuestionsKeepsNegativePenalties() {
        PkScoringService.Settlement settlement = service.settle(new PkScoringService.Input(
                5, -3, 0, 0, 5, 100, 10,
                0, 0, 0, 0, false, false));

        assertThat(settlement.playerAPointsDelta()).isEqualTo(0);
        assertThat(settlement.playerBPointsDelta()).isEqualTo(-3);
    }

    @Test
    void forfeitingPlayerCannotReceivePositiveRewards() {
        PkScoringService.Settlement settlement = service.settleForfeit(new PkScoringService.Input(
                8, 2, 5, 3, 12, 100, 100,
                0, 0, 0, 0, false, false), true);

        assertThat(settlement.playerAPointsDelta()).isEqualTo(0);
        assertThat(settlement.playerBPointsDelta()).isEqualTo(12);
    }

    @Test
    void forfeitingPlayerStillReceivesNegativePenaltyWithFloor() {
        PkScoringService.Settlement settlement = service.settleForfeit(new PkScoringService.Input(
                -5, 2, 0, 3, 12, 3, 100,
                0, 0, 0, 0, false, false), true);

        assertThat(settlement.playerAPointsDelta()).isEqualTo(-3);
        assertThat(settlement.playerBPointsDelta()).isEqualTo(12);
    }
}
