package org.gduf.pk;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PkCompetitivePhaseTwoXmlTest {

    @Test
    void settingsPersistAllVictoryDeclarations() throws Exception {
        String xml = Files.readString(
                Path.of("src/main/resources/mapper/PkUserSettingMapper.xml"),
                StandardCharsets.UTF_8);

        assertThat(xml).contains("first_win_declaration");
        assertThat(xml).contains("revenge_declaration");
        assertThat(xml).contains("sweep_declaration");
        assertThat(xml).contains("final_win_declaration");
        assertThat(xml).contains("upsertDeclarations");
    }

    @Test
    void matchReviewQueriesRecentMatchesAndReplayQuestions() throws Exception {
        String matchXml = Files.readString(
                Path.of("src/main/resources/mapper/PkMatchMapper.xml"),
                StandardCharsets.UTF_8);
        String questionXml = Files.readString(
                Path.of("src/main/resources/mapper/PkMatchQuestionMapper.xml"),
                StandardCharsets.UTF_8);

        assertThat(matchXml).contains("selectRecentMatches");
        assertThat(matchXml).contains("LIMIT #{limit}");
        assertThat(questionXml).contains("selectReplayQuestions");
        assertThat(questionXml).contains("pk_match_question");
        assertThat(questionXml).contains("LEFT JOIN pk_match_answer");
    }
}
