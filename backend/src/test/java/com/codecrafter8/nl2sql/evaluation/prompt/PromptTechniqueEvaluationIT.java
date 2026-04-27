package com.codecrafter8.nl2sql.evaluation.prompt;

import com.codecrafter8.nl2sql.dto.SchemaContextMode;
import com.codecrafter8.nl2sql.evaluation.support.AbstractResearchEvaluationIT;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.List;

@Tag("evaluation-prompt")
@SpringBootTest(properties = "app.sql-generation.system-prompt=${NL2SQL_SYSTEM_PROMPT:prompts/openai/generate-sql-system-zero-shot.txt}")
@ActiveProfiles("test")
class PromptTechniqueEvaluationIT extends AbstractResearchEvaluationIT {

    @Test
    void evaluatePromptTechnique() throws IOException {
        runCampaign("prompt-technique", "EWALUACJA TECHNIK PROMPTOWANIA", List.of(
                SchemaContextMode.FULL_SCHEMA,
                SchemaContextMode.RAG
        ));
    }
}
