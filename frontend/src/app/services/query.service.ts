import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, effect, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { catchError, finalize, tap, throwError } from 'rxjs';

import { QueryHistoryEntry, QueryRequest, QueryResponse } from '../models';

@Injectable({ providedIn: 'root' })
export class QueryService {
  private static readonly QUERY_API_BASE_URL = 'http://localhost:8080/api/v1/query';
  private static readonly EXECUTE_QUERY_URL = `${QueryService.QUERY_API_BASE_URL}/execute`;
  private static readonly QUERY_HISTORY_URL = `${QueryService.QUERY_API_BASE_URL}/history`;

  private readonly http = inject(HttpClient);
  private readonly queryInput = signal<string>('');
  private readonly historyLimit = signal<number>(10);
  private readonly historyRefreshToken = signal<number>(0);

  readonly queryResource = rxResource<QueryResponse, QueryRequest | undefined>({
    params: () => {
      const normalizedQuery = this.queryInput().trim();

      if (!normalizedQuery) {
        return undefined;
      }

      return {
        naturalLanguageQuery: normalizedQuery,
        explainSql: true
      };
    },
    stream: ({ params }) =>
      this.http.post<QueryResponse>(QueryService.EXECUTE_QUERY_URL, params).pipe(
        tap((response) => {
          console.info('[QueryService] Query executed with backend status:', response.status);
        }),
        catchError((error: HttpErrorResponse) => {
          console.error('[QueryService] Query execution failed.', {
            status: error.status,
            message: error.message,
            details: error.error
          });

          return throwError(() => this.mapHttpError(error));
        }),
        finalize(() => {
          this.refreshHistory();
        })
      )
  });

  readonly historyResource = rxResource<QueryHistoryEntry[], { limit: number; refreshToken: number }>({
    params: () => ({
      limit: this.historyLimit(),
      refreshToken: this.historyRefreshToken()
    }),
    stream: ({ params }) =>
      this.http
        .get<QueryHistoryEntry[]>(QueryService.QUERY_HISTORY_URL, {
          params: { limit: String(params.limit) }
        })
        .pipe(
          tap((historyEntries) => {
            console.info('[QueryService] Loaded query history entries:', historyEntries.length);
          }),
          catchError((error: HttpErrorResponse) => {
            console.error('[QueryService] Query history loading failed.', {
              status: error.status,
              message: error.message,
              details: error.error
            });

            return throwError(() => this.mapHttpError(error));
          })
        )
  });

  readonly loading = this.queryResource.isLoading;
  readonly value = this.queryResource.value;
  readonly error = this.queryResource.error;
  readonly historyLoading = this.historyResource.isLoading;
  readonly historyValue = this.historyResource.value;
  readonly historyError = this.historyResource.error;

  constructor() {
    effect(() => {
      console.info('[QueryService] Resource status:', this.queryResource.status());
    });

    effect(() => {
      const currentError = this.queryResource.error();
      if (currentError) {
        console.error('[QueryService] Resource error:', currentError);
      }
    });

    effect(() => {
      console.info('[QueryService] History resource status:', this.historyResource.status());
    });

    effect(() => {
      const currentHistoryError = this.historyResource.error();
      if (currentHistoryError) {
        console.error('[QueryService] History resource error:', currentHistoryError);
      }
    });
  }

  executeQuery(query: string): void {
    const normalizedQuery = query.trim();

    if (!normalizedQuery) {
      console.warn('[QueryService] Empty query passed to executeQuery. Request skipped.');
      this.queryInput.set('');
      return;
    }

    console.info('[QueryService] Executing query:', normalizedQuery);

    if (this.queryInput() === normalizedQuery) {
      console.info('[QueryService] Same query detected. Triggering resource reload.');
      this.queryResource.reload();
      return;
    }

    this.queryInput.set(normalizedQuery);
  }

  refreshHistory(limit = this.historyLimit()): void {
    const normalizedLimit = Number.isFinite(limit) && limit > 0 ? Math.floor(limit) : 10;

    if (this.historyLimit() !== normalizedLimit) {
      this.historyLimit.set(normalizedLimit);
    }

    this.historyRefreshToken.update((current) => current + 1);
  }

  private mapHttpError(error: HttpErrorResponse): Error {
    const backendMessage =
      typeof error.error === 'string'
        ? error.error
        : (error.error?.error as string | undefined) ?? error.message;

    const statusLabel = error.status > 0 ? `HTTP ${error.status}` : 'Network Error';
    return new Error(`${statusLabel}: ${backendMessage}`);
  }
}
