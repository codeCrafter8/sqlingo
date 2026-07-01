import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';

@Component({
  selector: 'app-dashboard-analysis-panel',
  standalone: true,
  templateUrl: './analysis-panel.component.html',
  styleUrl: './analysis-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class AnalysisPanelComponent {
  readonly queryDraft = input<string>('');
  readonly loading = input<boolean>(false);
  readonly canExecute = computed<boolean>(() => this.queryDraft().trim().length > 0 && !this.loading());

  readonly queryDraftChanged = output<string>();
  readonly executionRequested = output<void>();

  updateDraft(event: Event): void {
    const target = event.target as HTMLTextAreaElement | null;
    this.queryDraftChanged.emit(target?.value ?? '');
  }

  executeAnalysis(): void {
    this.executionRequested.emit();
  }
}
