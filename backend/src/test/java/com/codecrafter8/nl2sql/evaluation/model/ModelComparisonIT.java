package com.codecrafter8.nl2sql.evaluation.model;

import com.codecrafter8.nl2sql.dto.SchemaContextMode;
import com.codecrafter8.nl2sql.evaluation.support.AbstractResearchEvaluationIT;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.List;

@Tag("evaluation-model")
@SpringBootTest(properties = "spring.ai.openai.chat.options.model=${NL2SQL_MODEL:gpt-4o-mini}")
@ActiveProfiles("test")
class ModelComparisonIT extends AbstractResearchEvaluationIT {

    @Test
    void evaluateModel() throws IOException {
        runCampaign("model-comparison", "EWALUACJA MODELI", List.of(
                SchemaContextMode.FULL_SCHEMA,
                SchemaContextMode.RAG
        ));
    }
}
