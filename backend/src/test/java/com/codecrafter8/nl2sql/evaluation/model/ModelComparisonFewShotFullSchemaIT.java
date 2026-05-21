package com.codecrafter8.nl2sql.evaluation.model;

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

@DisplayName("Model Comparison: gpt-4o-mini vs gpt-4o - Few-Shot / Full Schema")
@Tag("evaluation-model")
class ModelComparisonFewShotFullSchemaIT {

    /*@Nested
    @DisplayName("gpt-4o-mini")
    @Tag("gpt-4o-mini")
    @SpringBootTest(properties = {
            "spring.ai.openai.chat.options.model=gpt-4o-mini",
            "app.sql-generation.system-prompt=prompts/openai/generate-sql-system-zero-shot.txt"
    })
    @ActiveProfiles("test")
    class Gpt4oMiniEvaluation extends AbstractResearchEvaluationIT {

        @Test
        void evaluateGpt4oMini() throws IOException {
            runCampaign(
                    "3-model-compare-gpt4o-mini",
                    "EWALUACJA MODELU: gpt-4o-mini",
                    List.of(SchemaContextMode.FULL_SCHEMA)
            );
        }
    }*/

    @Nested
    @DisplayName("gpt-4o")
    @Tag("gpt-4o")
    @SpringBootTest(properties = {
            "spring.ai.openai.chat.options.model=gpt-4o",
            "app.sql-generation.system-prompt=prompts/openai/generate-sql-system-zero-shot.txt"
    })
    @ActiveProfiles("test")
    class Gpt4oEvaluation extends AbstractResearchEvaluationIT {

        @Test
        void evaluateGpt4o() throws IOException {
            runCampaign(
                    "3-model-compare-gpt4o",
                    "EWALUACJA MODELU: gpt-4o",
                    List.of(SchemaContextMode.FULL_SCHEMA)
            );
        }
    }
}
