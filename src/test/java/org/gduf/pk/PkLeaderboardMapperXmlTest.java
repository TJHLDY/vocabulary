package org.gduf.pk;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PkLeaderboardMapperXmlTest {

    @Test
    void leaderboardOrderingUsesExistingWinRateAlias() throws Exception {
        String xml = Files.readString(
                Path.of("src/main/resources/mapper/PkUserStatsMapper.xml"),
                StandardCharsets.UTF_8);

        assertThat(xml).doesNotContain("win_rate");
        assertThat(xml).contains("winRate DESC");
    }
}
