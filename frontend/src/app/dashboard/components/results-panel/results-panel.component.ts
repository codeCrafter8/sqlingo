import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

type ResultRow = Record<string, unknown>;

@Component({
  selector: 'app-dashboard-results-panel',
  standalone: true,
  templateUrl: './results-panel.component.html',
  styleUrl: './results-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ResultsPanelComponent {
  readonly loading = input<boolean>(false);
  readonly declaredRowCount = input<number | null | undefined>(undefined);
  readonly results = input<unknown[] | null | undefined>([]);

  readonly resultRows = computed<ResultRow[]>(() => {
    const rows = this.results();
    if (!Array.isArray(rows)) {
      return [];
    }

    return rows.filter((row): row is ResultRow => typeof row === 'object' && row !== null);
  });

  readonly columns = computed<string[]>(() => {
    const columnSet = new Set<string>();

    for (const row of this.resultRows()) {
      for (const key of Object.keys(row)) {
        columnSet.add(key);
      }
    }

    return Array.from(columnSet);
  });

  readonly rowCount = computed<number>(() => {
    const declaredCount = this.declaredRowCount();
    return typeof declaredCount === 'number' ? declaredCount : this.resultRows().length;
  });

  readonly hasResults = computed<boolean>(() => this.resultRows().length > 0 && this.columns().length > 0);

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
}
