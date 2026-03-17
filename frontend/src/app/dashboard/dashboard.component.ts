import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';

import { QueryHistoryEntry, QueryResponse, QueryStatus } from '../models';
import { QueryService } from '../services/query.service';
import { AnalysisPanelComponent } from './components/analysis-panel/analysis-panel.component';
import { ExplainabilityPanelComponent } from './components/explainability-panel/explainability-panel.component';
import { HistorySidebarComponent } from './components/history-sidebar/history-sidebar.component';
import { ResultsPanelComponent } from './components/results-panel/results-panel.component';
import { TechnicalInspectorComponent } from './components/technical-inspector/technical-inspector.component';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule,
    AnalysisPanelComponent,
    ExplainabilityPanelComponent,
    HistorySidebarComponent,
    ResultsPanelComponent,
    TechnicalInspectorComponent
  ],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class DashboardComponent {
  private readonly queryService = inject(QueryService);
  private readonly previousErrorLog = signal<string>('');
  private readonly handledResponseFingerprint = signal<string>('');

  readonly historySidebarOpen = signal<boolean>(false);
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
  readonly historyErrorMessage = computed<string | null>(() => this.historyError()?.message?.trim() ?? null);
  readonly showAutoCorrectionNotice = signal<boolean>(false);

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

  setQueryDraft(query: string): void {
    this.queryDraft.set(query);
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
    this.setQueryDraft(query);
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
