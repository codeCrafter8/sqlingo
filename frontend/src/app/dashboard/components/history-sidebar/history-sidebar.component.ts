import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

import { QueryHistoryEntry, QueryStatus } from '../../../models';

@Component({
  selector: 'app-dashboard-history-sidebar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './history-sidebar.component.html',
  styleUrl: './history-sidebar.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class HistorySidebarComponent {
  private readonly historyDateFormatter = new Intl.DateTimeFormat('pl-PL', {
    dateStyle: 'short',
    timeStyle: 'short'
  });

  readonly loading = input<boolean>(false);
  readonly errorMessage = input<string | null>(null);
  readonly entries = input<QueryHistoryEntry[]>([]);

  readonly refreshRequested = output<void>();
  readonly queryReuseRequested = output<string>();

  readonly hasHistory = computed<boolean>(() => this.entries().length > 0);

  refreshHistory(): void {
    this.refreshRequested.emit();
  }

  reuseQuery(query: string): void {
    this.queryReuseRequested.emit(query);
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
}
