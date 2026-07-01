export interface QueryHistoryEntry {
  id: number;
  naturalLanguageQuery: string;
  generatedSql: string | null;
  results: string | null;
  status: string | null;
  executionTimeMs: number | null;
  createdAt: string | null;
  error: string | null;
}
