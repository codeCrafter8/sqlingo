package com.codecrafter8.nl2sql;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
class Nl2sqlEngineApplicationTests {

    @MockitoBean
    private VectorStore vectorStore;

    @Test
    void contextLoads() {
    }

}
