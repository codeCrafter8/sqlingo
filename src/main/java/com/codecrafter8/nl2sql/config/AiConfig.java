package com.codecrafter8.nl2sql.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.PgVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(RagProperties.class)
class AiConfig {

    @Bean
    ChatClient chatClient(ChatClient.Builder builder) {
        return builder.build();
    }

    @Bean
    VectorStore vectorStore(
            EmbeddingModel embeddingModel,
            @Value("${app.pgvector.datasource.url}") String pgVectorUrl,
            @Value("${app.pgvector.datasource.username}") String pgVectorUsername,
            @Value("${app.pgvector.datasource.password}") String pgVectorPassword,
            @Value("${app.pgvector.datasource.driver-class-name}") String pgVectorDriver,
            @Value("${app.pgvector.table-name}") String tableName,
            @Value("${app.pgvector.dimensions}") int dimensions,
            @Value("${app.pgvector.initialize-schema}") boolean initializeSchema,
            @Value("${app.pgvector.remove-existing-table}") boolean removeExistingTable) {

        DataSource pgVectorDataSource = new DriverManagerDataSource(pgVectorUrl, pgVectorUsername, pgVectorPassword);
        ((DriverManagerDataSource) pgVectorDataSource).setDriverClassName(pgVectorDriver);

        JdbcTemplate jdbcTemplate = new JdbcTemplate(pgVectorDataSource);

        return new PgVectorStore(
                tableName,
                jdbcTemplate,
                embeddingModel,
                dimensions,
                PgVectorStore.PgDistanceType.COSINE_DISTANCE,
                removeExistingTable,
                PgVectorStore.PgIndexType.NONE,
                initializeSchema);
    }

}
