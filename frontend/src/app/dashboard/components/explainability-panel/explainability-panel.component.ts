import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

@Component({
  selector: 'app-dashboard-explainability-panel',
  standalone: true,
  templateUrl: './explainability-panel.component.html',
  styleUrl: './explainability-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ExplainabilityPanelComponent {
  readonly sqlExplanation = input<string | null | undefined>(null);

  readonly explainability = computed<string>(() => {
    const explanation = this.sqlExplanation()?.trim();
    return explanation && explanation.length > 0
      ? explanation
      : 'Uruchom analize, aby zobaczyc tekstowe uzasadnienie wygenerowanego SQL.';
  });
}
