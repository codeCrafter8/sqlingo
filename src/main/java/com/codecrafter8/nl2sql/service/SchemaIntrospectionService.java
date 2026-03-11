package com.codecrafter8.nl2sql.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Service responsible for introspecting the database schema
 * and providing schema information to the LLM for SQL generation.
 * Reads schema directly from Spider hospital.sqlite database.
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class SchemaIntrospectionService {

    private final DataSource dataSource;

    /**
     * Get schema as formatted string for LLM context
     */
    public String getSchemaContextForLLM() {
        log.debug("Generating schema context for LLM");
        List<String> tables = getAllTables();

        StringBuilder schemaContext = new StringBuilder();
        schemaContext.append("Database Schema (Spider Hospital Database):\n\n");
        schemaContext.append("This is a hospital management database with information about physicians, patients, appointments, medications, and procedures.\n\n");

        for (String tableName : tables) {
            schemaContext.append("TABLE: ").append(tableName).append("\n");
            schemaContext.append("----------------------------------------\n");

            // Add columns
            Map<String, String> columns = getTableSchema(tableName);
            schemaContext.append("Columns:\n");
            for (Map.Entry<String, String> column : columns.entrySet()) {
                schemaContext.append("  - ").append(column.getKey())
                        .append(": ").append(column.getValue()).append("\n");
            }

            // Add primary keys
            List<String> primaryKeys = getPrimaryKeys(tableName);
            if (!primaryKeys.isEmpty()) {
                schemaContext.append("Primary Key: ").append(String.join(", ", primaryKeys)).append("\n");
            }

            // Add foreign keys
            Map<String, String> foreignKeys = getForeignKeys(tableName);
            if (!foreignKeys.isEmpty()) {
                schemaContext.append("Foreign Keys:\n");
                for (Map.Entry<String, String> fk : foreignKeys.entrySet()) {
                    schemaContext.append("  - ").append(fk.getKey())
                            .append(" -> ").append(fk.getValue()).append("\n");
                }
            }

            schemaContext.append("\n");
        }

        return schemaContext.toString();
    }

    /**
     * Get all tables from the database schema
     */
    public List<String> getAllTables() {
        log.debug("Fetching all tables from database schema");

        List<String> ignoredTables = List.of("query_logs");
        List<String> tables = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet rs = metaData.getTables(null, null, "%", new String[]{"TABLE"});

            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (!ignoredTables.contains(tableName.toLowerCase())) {
                    tables.add(tableName);
                }
            }
        } catch (Exception e) {
            log.error("Error fetching tables from database", e);
        }

        return tables;
    }

    /**
     * Get detailed schema information for a specific table
     */
    public Map<String, String> getTableSchema(String tableName) {
        log.debug("Fetching schema for table: {}", tableName);
        Map<String, String> columns = new LinkedHashMap<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet rs = metaData.getColumns(null, null, tableName, null);

            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                String columnType = rs.getString("TYPE_NAME");
                int columnSize = rs.getInt("COLUMN_SIZE");
                String isNullable = rs.getString("IS_NULLABLE");

                String columnInfo = String.format("%s(%d) %s",
                        columnType,
                        columnSize,
                        "NO".equals(isNullable) ? "NOT NULL" : "");
                columns.put(columnName, columnInfo);
            }
        } catch (Exception e) {
            log.error("Error fetching schema for table: {}", tableName, e);
        }

        return columns;
    }

    /**
     * Get primary keys for a table
     */
    public List<String> getPrimaryKeys(String tableName) {
        List<String> primaryKeys = new ArrayList<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet rs = metaData.getPrimaryKeys(null, null, tableName);

            while (rs.next()) {
                primaryKeys.add(rs.getString("COLUMN_NAME"));
            }
        } catch (Exception e) {
            log.error("Error fetching primary keys for table: {}", tableName, e);
        }

        return primaryKeys;
    }

    /**
     * Get foreign keys for a table
     */
    public Map<String, String> getForeignKeys(String tableName) {
        Map<String, String> foreignKeys = new LinkedHashMap<>();

        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet rs = metaData.getImportedKeys(null, null, tableName);

            while (rs.next()) {
                String fkColumn = rs.getString("FKCOLUMN_NAME");
                String pkTable = rs.getString("PKTABLE_NAME");
                String pkColumn = rs.getString("PKCOLUMN_NAME");
                foreignKeys.put(fkColumn, pkTable + "(" + pkColumn + ")");
            }
        } catch (Exception e) {
            log.error("Error fetching foreign keys for table: {}", tableName, e);
        }

        return foreignKeys;
    }

}
