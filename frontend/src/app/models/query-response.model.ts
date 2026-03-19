export interface QueryResponse {
  id: number;
  naturalLanguageQuery: string;
  generatedSql: string;
  sqlExplanation: string;
  results: any[];
  rowCount: number;
  status: string;
  executionTimeMs: number;
  error: string;
}