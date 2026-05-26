package com.codecrafter8.nl2sql.evaluation.prompt;

import com.codecrafter8.nl2sql.dto.SchemaContextMode;
import com.codecrafter8.nl2sql.evaluation.support.AbstractResearchEvaluationIT;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.List;

@DisplayName("Prompt Techniques")
class PromptTechniquesEvaluationIT {

    /*@Tag("prompt-zero-shot")
    @SpringBootTest(
            properties = "app.sql-generation.system-prompt=prompts/openai/generate-sql-system-zero-shot.txt"
    )
    @ActiveProfiles("test")
    @DisplayName("Zero-Shot: Full Schema")
    @Nested
    class ZeroShotEvaluation extends AbstractResearchEvaluationIT {

        @Test
        @DisplayName("Porównanie prompt techniques - Zero-Shot na Full Schema")
        void compareFullSchemaZeroShot() throws IOException {
            runCampaign(
                    "2-zero-shot",
                    "EWALUACJA: FULL_SCHEMA - ZERO-SHOT",
                    List.of(
                            SchemaContextMode.FULL_SCHEMA
                    )
            );
        }
    }*/

    @Tag("prompt-few-shot")
    @SpringBootTest(
            properties = "app.sql-generation.system-prompt=prompts/openai/generate-sql-system-few-shot.txt"
    )
    @ActiveProfiles("test")
    @DisplayName("Few-Shot: Full Schema")
    @Nested
    class FewShotEvaluation extends AbstractResearchEvaluationIT {

        @Test
        @DisplayName("Porównanie prompt techniques - Few-Shot na Full Schema")
        void compareFullSchemaFewShot() throws IOException {
            runCampaign(
                    "2-few-shot",
                    "EWALUACJA: FULL_SCHEMA - FEW-SHOT",
                    List.of(
                            SchemaContextMode.FULL_SCHEMA
                    )
            );
        }
    }

    @Tag("prompt-cof")
    @SpringBootTest(
            properties = "app.sql-generation.system-prompt=prompts/openai/generate-sql-system-cof.txt"
    )
    @ActiveProfiles("test")
    @DisplayName("Chain-of-Thoughts: Full Schema")
    @Nested
    class ChainOfThoughtsEvaluation extends AbstractResearchEvaluationIT {

        @Test
        @DisplayName("Porównanie prompt techniques - Chain-of-Thoughts na Full Schema")
        void compareFullSchemaChainOfThoughts() throws IOException {
            runCampaign(
                    "2-cof",
                    "EWALUACJA: FULL_SCHEMA - CHAIN-OF-THOUGHTS",
                    List.of(
                            SchemaContextMode.FULL_SCHEMA
                    )
            );
        }
    }
}
