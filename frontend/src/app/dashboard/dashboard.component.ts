import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { Highlight } from 'ngx-highlightjs';

import { QueryHistoryEntry, QueryResponse, QueryStatus } from '../models';
import { QueryService } from '../services/query.service';

type ResultRow = Record<string, unknown>;

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, Highlight],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DashboardComponent {
  private readonly queryService = inject(QueryService);
  private readonly historyDateFormatter = new Intl.DateTimeFormat('pl-PL', {
    dateStyle: 'short',
    timeStyle: 'short'
  });
  private readonly previousErrorLog = signal<string>('');
  private readonly handledResponseFingerprint = signal<string>('');

  readonly historySidebarOpen = signal<boolean>(true);
  readonly queryDraft = signal<string>('');
  readonly loading = this.queryService.loading;
  readonly historyLoading = this.queryService.historyLoading;
  readonly response = computed<QueryResponse | null>(() => this.queryService.value() ?? null);
  readonly queryHistory = computed<QueryHistoryEntry[]>(() => {
    const entries = this.queryService.historyValue() ?? [];

    return [...entries].sort((left, right) => {
      const leftTimestamp = left.createdAt ? Date.parse(left.createdAt) : Number.NaN;
      const rightTimestamp = right.createdAt ? Date.parse(right.createdAt) : Number.NaN;

      if (!Number.isNaN(leftTimestamp) && !Number.isNaN(rightTimestamp) && leftTimestamp !== rightTimestamp) {
        return rightTimestamp - leftTimestamp;
      }

      return right.id - left.id;
    });
  });
  readonly transportError = computed<Error | undefined>(() => this.queryService.error() ?? undefined);
  readonly historyError = computed<Error | undefined>(() => this.queryService.historyError() ?? undefined);

  readonly status = computed<QueryStatus>(() => this.normalizeStatus(this.response()?.status));
  readonly isSuccess = computed<boolean>(() => this.status() === QueryStatus.SUCCESS);
  readonly isError = computed<boolean>(() => {
    const status = this.status();
    return status === QueryStatus.ERROR || status === QueryStatus.INVALID_SQL || status === QueryStatus.FAILED;
  });
  readonly hasHistory = computed<boolean>(() => this.queryHistory().length > 0);

  readonly statusLabel = computed<string>(() => {
    switch (this.status()) {
      case QueryStatus.SUCCESS:
        return 'SUCCESS';
      case QueryStatus.FAILED:
        return 'FAILED';
      case QueryStatus.ERROR:
        return 'ERROR';
      case QueryStatus.INVALID_SQL:
        return 'INVALID_SQL';
      default:
        return 'PENDING';
    }
  });

  readonly statusNote = computed<string>(() => {
    switch (this.status()) {
      case QueryStatus.SUCCESS:
        return 'Zapytanie wykonane poprawnie.';
      case QueryStatus.FAILED:
        return 'Zapytanie zakonczone niepowodzeniem.';
      case QueryStatus.INVALID_SQL:
        return 'Wykryto niepoprawne lub niebezpieczne SQL.';
      case QueryStatus.ERROR:
        return 'Wykonanie nie powiodlo sie.';
      default:
        return this.loading() ? 'Analiza w toku...' : 'Oczekiwanie na uruchomienie.';
    }
  });

  readonly statusBadgeClass = computed<string>(() => {
    if (this.isSuccess()) {
      return 'border-emerald-300 bg-emerald-100 text-emerald-800';
    }

    if (this.isError()) {
      return 'border-rose-300 bg-rose-100 text-rose-800';
    }

    return 'border-sky-300 bg-sky-100 text-sky-800';
  });

  readonly statusIconClass = computed<string>(() => {
    if (this.isSuccess()) {
      return 'border-emerald-300 bg-emerald-100 text-emerald-800';
    }

    if (this.isError()) {
      return 'border-rose-300 bg-rose-100 text-rose-800';
    }

    return 'border-sky-300 bg-sky-100 text-sky-800';
  });

  readonly executionTimeLabel = computed<string>(() => {
    const executionTime = this.response()?.executionTimeMs;
    return typeof executionTime === 'number' ? `${executionTime} ms` : 'n/a';
  });

  readonly generatedSql = computed<string>(() => {
    const sql = this.response()?.generatedSql?.trim();
    return sql && sql.length > 0 ? sql : '-- SQL pojawi sie po wykonaniu analizy --';
  });

  readonly explainability = computed<string>(() => {
    const explanation = this.response()?.sqlExplanation?.trim();
    return explanation && explanation.length > 0
      ? explanation
      : 'Uruchom analize, aby zobaczyc tekstowe uzasadnienie wygenerowanego SQL.';
  });

  readonly resultRows = computed<ResultRow[]>(() => {
    const rows = this.response()?.results;
    if (!Array.isArray(rows)) {
      return [];
    }

    return rows.filter((row): row is ResultRow => typeof row === 'object' && row !== null);
  });

  readonly resultColumns = computed<string[]>(() => {
    const columns = new Set<string>();

    for (const row of this.resultRows()) {
      for (const key of Object.keys(row)) {
        columns.add(key);
      }
    }

    return Array.from(columns);
  });

  readonly hasResults = computed<boolean>(() => this.resultRows().length > 0 && this.resultColumns().length > 0);

  readonly rowCount = computed<number>(() => {
    const declaredCount = this.response()?.rowCount;
    return typeof declaredCount === 'number' ? declaredCount : this.resultRows().length;
  });

  readonly canExecute = computed<boolean>(() => this.queryDraft().trim().length > 0 && !this.loading());
  readonly showAutoCorrectionNotice = signal<boolean>(false);

  readonly activeErrorLog = computed<string>(() => {
    const backendError = this.response()?.error?.trim();
    const transportError = this.transportError()?.message?.trim();
    return backendError || transportError || '';
  });

  constructor() {
    effect(() => {
      const resourceError = this.transportError();

      if (!resourceError) {
        return;
      }

      this.previousErrorLog.set(resourceError.message);
      this.showAutoCorrectionNotice.set(false);
    });

    effect(() => {
      const response = this.response();

      if (!response) {
        return;
      }

      const fingerprint = this.buildResponseFingerprint(response);

      if (this.handledResponseFingerprint() === fingerprint) {
        return;
      }

      this.handledResponseFingerprint.set(fingerprint);

      const backendError = response.error?.trim();
      if (backendError) {
        this.previousErrorLog.set(backendError);
      }

      const shouldShowNotice =
        this.normalizeStatus(response.status) === QueryStatus.SUCCESS && Boolean(this.previousErrorLog());

      this.showAutoCorrectionNotice.set(shouldShowNotice);

      if (shouldShowNotice) {
        this.previousErrorLog.set('');
      }
    });
  }

  updateDraft(event: Event): void {
    const target = event.target as HTMLTextAreaElement | null;
    this.queryDraft.set(target?.value ?? '');
  }

  executeAnalysis(): void {
    this.showAutoCorrectionNotice.set(false);
    this.queryService.executeQuery(this.queryDraft());
  }

  toggleHistorySidebar(): void {
    this.historySidebarOpen.update((isOpen) => !isOpen);
  }

  refreshHistory(): void {
    this.queryService.refreshHistory();
  }

  reuseHistoryQuery(query: string): void {
    this.queryDraft.set(query);
  }

  formatHistoryDate(createdAt?: string | null): string {
    if (!createdAt) {
      return 'n/a';
    }

    const timestamp = Date.parse(createdAt);
    if (Number.isNaN(timestamp)) {
      return createdAt;
    }

    return this.historyDateFormatter.format(new Date(timestamp));
  }

  formatHistoryExecutionTime(executionTimeMs?: number | null): string {
    return typeof executionTimeMs === 'number' ? `${executionTimeMs} ms` : 'n/a';
  }

  formatHistoryResults(results?: string | null): string {
    const normalizedResults = results?.trim();

    if (!normalizedResults) {
      return 'Brak wynikow w logu.';
    }

    const condensedResults = normalizedResults.replace(/\s+/g, ' ');
    return condensedResults.length > 240 ? `${condensedResults.slice(0, 240)}...` : condensedResults;
  }

  formatHistoryStatus(status?: string | null): string {
    const normalizedStatus = this.normalizeStatus(status);
    return normalizedStatus === QueryStatus.PENDING ? 'UNKNOWN' : normalizedStatus;
  }

  historyStatusClass(status?: string | null): string {
    const normalizedStatus = this.normalizeStatus(status);

    if (normalizedStatus === QueryStatus.SUCCESS) {
      return 'border-emerald-300 bg-emerald-100 text-emerald-800';
    }

    if (
      normalizedStatus === QueryStatus.ERROR ||
      normalizedStatus === QueryStatus.INVALID_SQL ||
      normalizedStatus === QueryStatus.FAILED
    ) {
      return 'border-rose-300 bg-rose-100 text-rose-800';
    }

    return 'border-slate-300 bg-slate-200/70 text-slate-700';
  }

  formatColumnName(column: string): string {
    return column
      .replace(/_/g, ' ')
      .replace(/([a-z])([A-Z])/g, '$1 $2')
      .replace(/^./, (char) => char.toUpperCase());
  }

  formatCellValue(row: ResultRow, column: string): string {
    const value = row[column];

    if (value === null || value === undefined) {
      return 'NULL';
    }

    if (typeof value === 'object') {
      return JSON.stringify(value);
    }

    return String(value);
  }

  private normalizeStatus(status?: string | null): QueryStatus {
    switch (status) {
      case QueryStatus.SUCCESS:
        return QueryStatus.SUCCESS;
      case QueryStatus.FAILED:
        return QueryStatus.FAILED;
      case QueryStatus.ERROR:
        return QueryStatus.ERROR;
      case QueryStatus.INVALID_SQL:
        return QueryStatus.INVALID_SQL;
      default:
        return QueryStatus.PENDING;
    }
  }

  private buildResponseFingerprint(response: QueryResponse): string {
    return [
      response.id ?? 'no-id',
      response.status ?? 'no-status',
      response.executionTimeMs ?? 'no-time',
      response.generatedSql ?? 'no-sql',
      response.error ?? 'no-error'
    ].join('|');
  }
}
