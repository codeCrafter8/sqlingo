import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Highlight } from 'ngx-highlightjs';

import { QueryStatus } from '../../../models';

@Component({
  selector: 'app-dashboard-technical-inspector',
  standalone: true,
  imports: [CommonModule, Highlight],
  templateUrl: './technical-inspector.component.html',
  styleUrl: './technical-inspector.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TechnicalInspectorComponent {
  readonly status = input<string | null | undefined>(QueryStatus.PENDING);
  readonly loading = input<boolean>(false);
  readonly executionTimeMs = input<number | null | undefined>(null);
  readonly showAutoCorrectionNotice = input<boolean>(false);
  readonly responseError = input<string | null | undefined>(null);
  readonly transportErrorMessage = input<string | null | undefined>(null);
  readonly generatedSqlRaw = input<string | null | undefined>(null);

  readonly normalizedStatus = computed<QueryStatus>(() => this.normalizeStatus(this.status()));

  readonly isSuccess = computed<boolean>(() => this.normalizedStatus() === QueryStatus.SUCCESS);

  readonly isError = computed<boolean>(() => {
    const status = this.normalizedStatus();
    return status === QueryStatus.ERROR || status === QueryStatus.INVALID_SQL || status === QueryStatus.FAILED;
  });

  readonly statusLabel = computed<string>(() => {
    switch (this.normalizedStatus()) {
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
    switch (this.normalizedStatus()) {
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
    const executionTime = this.executionTimeMs();
    return typeof executionTime === 'number' ? `${executionTime} ms` : 'n/a';
  });

  readonly activeErrorLog = computed<string>(() => {
    const backendError = this.responseError()?.trim();
    const transportError = this.transportErrorMessage()?.trim();
    return backendError || transportError || '';
  });

  readonly generatedSql = computed<string>(() => {
    const sql = this.generatedSqlRaw()?.trim();
    return sql && sql.length > 0 ? sql : '-- SQL pojawi sie po wykonaniu analizy --';
  });

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
