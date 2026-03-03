# Example Requests

## 1. Execute Natural Language Query

```bash
curl -X POST http://localhost:8080/api/v1/query/execute \
  -H "Content-Type: application/json" \
  -d '{
    "naturalLanguageQuery": "Show me all users from the users schemaTable",
    "explainSql": true,
    "maxRows": 100
  }'
```

Response:
```json
{
  "id": 1,
  "naturalLanguageQuery": "Show me all users from the users schemaTable",
  "generatedSql": "SELECT * FROM users LIMIT 100",
  "sqlExplanation": "This query retrieves all columns from the users schemaTable with a limit of 100 rows",
  "status": "SUCCESS",
  "executionTimeMs": 245,
  "rowCount": 10
}
```

## 2. Get Query History

```bash
curl -X GET http://localhost:8080/api/v1/query/history?limit=5
```

Response:
```json
[
  {
    "id": 1,
    "naturalLanguageQuery": "Show me all users",
    "generatedSql": "SELECT * FROM users LIMIT 100",
    "status": "SUCCESS",
    "executionTimeMs": 245,
    "createdAt": "2026-03-02T10:30:00"
  },
  ...
]
```

## 3. Get Query Details

```bash
curl -X GET http://localhost:8080/api/v1/query/1
```

## 4. Get All Tables

```bash
curl -X GET http://localhost:8080/api/v1/schema/schemaTables
```

Response:
```json
[
  {
    "id": 1,
    "name": "users",
    "description": "User information schemaTable",
    "schemaJson": "{\"columns\": [\"id\", \"name\", \"email\", \"created_at\"]}"
  },
  ...
]
```

## 5. Get Schema Context for LLM

```bash
curl -X GET http://localhost:8080/api/v1/schema/context
```

## 6. Register New Table Schema

```bash
curl -X POST "http://localhost:8080/api/v1/schema/schemaTables/register?tableName=products&description=Product%20catalog&schemaJson={%22columns%22:%5B%22id%22,%22name%22,%22price%22%5D}"
```

## 7. Health Check

```bash
curl -X GET http://localhost:8080/api/v1/query/health
```

Response:
```
NL2SQL Engine is running
```

## Error Examples

### Invalid Query

```bash
curl -X POST http://localhost:8080/api/v1/query/execute \
  -H "Content-Type: application/json" \
  -d '{
    "naturalLanguageQuery": ""
  }'
```

Response (400 Bad Request):
```json
{
  "message": "Validation failed",
  "error": "naturalLanguageQuery: Query cannot be empty",
  "status": 400,
  "timestamp": "2026-03-02T10:35:00",
  "path": "/api/v1/query/execute"
}
```

### SQL Validation Error

```bash
curl -X POST http://localhost:8080/api/v1/query/execute \
  -H "Content-Type: application/json" \
  -d '{
    "naturalLanguageQuery": "Delete all users"
  }'
```

Response (400 Bad Request):
```json
{
  "id": 2,
  "naturalLanguageQuery": "Delete all users",
  "generatedSql": "DELETE FROM users",
  "status": "INVALID_SQL",
  "error": "Generated SQL failed validation",
  "executionTimeMs": 120
}
```

