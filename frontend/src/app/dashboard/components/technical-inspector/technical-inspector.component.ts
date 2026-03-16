import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { Highlight } from 'ngx-highlightjs';

@Component({
  selector: 'app-dashboard-technical-inspector',
  standalone: true,
  imports: [CommonModule, Highlight],
  templateUrl: './technical-inspector.component.html',
  styleUrl: './technical-inspector.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush
})
export class TechnicalInspectorComponent {
  readonly statusBadgeClass = input<string>('border-sky-300 bg-sky-100 text-sky-800');
  readonly statusLabel = input<string>('PENDING');
  readonly statusIconClass = input<string>('border-sky-300 bg-sky-100 text-sky-800');
  readonly statusNote = input<string>('Oczekiwanie na uruchomienie.');
  readonly isSuccess = input<boolean>(false);
  readonly isError = input<boolean>(false);
  readonly executionTimeLabel = input<string>('n/a');
  readonly showAutoCorrectionNotice = input<boolean>(false);
  readonly activeErrorLog = input<string>('');
  readonly generatedSql = input<string>('-- SQL pojawi sie po wykonaniu analizy --');
}
