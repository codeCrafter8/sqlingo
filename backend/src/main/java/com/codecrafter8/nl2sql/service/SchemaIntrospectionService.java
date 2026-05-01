package com.codecrafter8.nl2sql.service;

import com.codecrafter8.nl2sql.model.TableSchemaDocument;
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
        schemaContext.append("Schemat bazy danych:\n\n");

        for (String tableName : tables) {
            Map<String, String> columns = getTableSchema(tableName);
            String description = generateTableDescription(tableName, columns);
            List<String> primaryKeys = getPrimaryKeys(tableName);
            Map<String, String> foreignKeys = getForeignKeys(tableName);

            TableSchemaDocument doc = TableSchemaDocument.builder()
                    .tableName(tableName)
                    .columns(columns)
                    .description(description)
                    .primaryKeys(primaryKeys)
                    .foreignKeys(foreignKeys)
                    .build();

            schemaContext.append(doc.format()).append("\n");
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

    /**
     * Generate a human-readable description for the table
     */
    public String generateTableDescription(String tableName, Map<String, String> columns) {
        return switch (tableName.toLowerCase()) {
            case "physician" ->
                    "Medical staff, physicians, doctors. Lekarze, doktorzy, personel medyczny, specjaliści.";
            case "department" ->
                    "Hospital departments, units. Oddziały szpitalne, jednostki organizacyjne, kierownictwo (Head).";
            case "affiliated_with" ->
                    "Physician-department affiliations. Przynależność lekarzy do oddziałów, gdzie pracują lekarze.";
            case "procedures" ->
                    "Medical procedures, treatments, operations. Zabiegi medyczne, operacje, procedury, koszty (Cost).";
            case "trained_in" ->
                    "Physician certifications for procedures. Szkolenia lekarzy, uprawnienia do zabiegów, certyfikaty.";
            case "patient" ->
                    "Patient records, demographics. Pacjenci, dane chorych, rekordy medyczne, lekarz prowadzący (PCP).";
            case "nurse" ->
                    "Nurses, nursing staff. Pielęgniarki, pielęgniarze, personel pomocniczy, uprawnienia (Registered).";
            case "appointment" ->
                    "Scheduled visits, medical appointments. Wizyty lekarskie, spotkania, terminy, gabinety (ExaminationRoom).";
            case "medication" -> "Available drugs, medications. Leki, lekarstwa, farmaceutyki, marki (Brand).";
            case "prescribes" -> "Prescriptions, medication orders. Recepty, przepisywanie leków, dawkowanie (Dose).";
            case "block" -> "Hospital blocks and floors. Bloki szpitalne, piętra, kondygnacje.";
            case "room" ->
                    "Hospital rooms, patient rooms. Pokoje, sale chorych, dostępność (Unavailable), typy sal (RoomType).";
            case "on_call" -> "Nurse shift schedules, on-call duties. Dyżury pielęgniarskie, grafik pielęgniarek.";
            case "stay" -> "Patient hospital stays, admissions. Pobyt w szpitalu, hospitalizacja, okres pobytu.";
            case "undergoes" ->
                    "Historical records of procedures performed on patients. Historia leczenia, wykonane zabiegi, operacje pacjentów.";
            default -> String.format("Table %s containing columns: %s. Tabela %s zawiera kolumny: %s",
                    tableName, String.join(", ", columns.keySet()), tableName, String.join(", ", columns.keySet()));
        };
    }

}
