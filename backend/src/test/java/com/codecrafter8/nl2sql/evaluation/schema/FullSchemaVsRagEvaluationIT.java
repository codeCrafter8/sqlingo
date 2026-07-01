package com.codecrafter8.nl2sql.evaluation.schema;

import com.codecrafter8.nl2sql.dto.SchemaContextMode;
import com.codecrafter8.nl2sql.evaluation.support.AbstractResearchEvaluationIT;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.IOException;
import java.util.List;

@Tag("evaluation-schema")
@SpringBootTest
@ActiveProfiles("test")
class FullSchemaVsRagEvaluationIT extends AbstractResearchEvaluationIT {

    @Test
    void compareFullSchemaAndRag() throws IOException {
        runCampaign("1-full-schema-vs-rag", "EWALUACJA FULL_SCHEMA VS RAG", List.of(
                SchemaContextMode.FULL_SCHEMA,
                SchemaContextMode.RAG
        ));
    }
}
