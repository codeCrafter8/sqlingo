import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-dashboard-explainability-panel',
  standalone: true,
  templateUrl: './explainability-panel.component.html',
  styleUrl: './explainability-panel.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class ExplainabilityPanelComponent {
  readonly explainability = input<string>('Uruchom analize, aby zobaczyc tekstowe uzasadnienie wygenerowanego SQL.');
}
