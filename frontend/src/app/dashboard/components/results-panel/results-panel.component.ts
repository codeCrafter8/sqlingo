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
  readonly rowCount = input<number>(0);
  readonly columns = input<string[]>([]);
  readonly rows = input<ResultRow[]>([]);

  readonly hasResults = computed<boolean>(() => this.rows().length > 0 && this.columns().length > 0);

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
