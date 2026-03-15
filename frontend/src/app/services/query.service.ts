import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Injectable, effect, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { catchError, tap, throwError } from 'rxjs';

import { QueryRequest, QueryResponse } from '../models';

@Injectable({ providedIn: 'root' })
export class QueryService {
  private static readonly EXECUTE_QUERY_URL = 'http://localhost:8080/api/v1/query/execute';

  private readonly http = inject(HttpClient);
  private readonly queryInput = signal<string>('');

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
        })
      )
  });

  readonly loading = this.queryResource.isLoading;
  readonly value = this.queryResource.value;
  readonly error = this.queryResource.error;

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

  private mapHttpError(error: HttpErrorResponse): Error {
    const backendMessage =
      typeof error.error === 'string'
        ? error.error
        : (error.error?.error as string | undefined) ?? error.message;

    const statusLabel = error.status > 0 ? `HTTP ${error.status}` : 'Network Error';
    return new Error(`${statusLabel}: ${backendMessage}`);
  }
}
