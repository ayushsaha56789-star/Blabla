import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { problemOf } from '../../core/errors';
import { Icon } from '../../shared/icon';
import { AdminApi } from '../admin-api';
import { AuditEntry, ChainCheck } from '../admin.models';
import { auditStyle, fullTime, timeAgo } from '../admin-ui';

const CATEGORIES = [
  { label: 'Everything', action: '' },
  { label: 'Staff', action: 'staff.' },
  { label: 'Members', action: 'member.' },
  { label: 'Switches', action: 'feature.' },
] as const;

@Component({
  selector: 'app-admin-audit',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  templateUrl: './audit.html',
})
export class AuditPage implements OnInit {
  private readonly api = inject(AdminApi);

  protected readonly categories = CATEGORIES;
  protected readonly category = signal('');
  protected readonly query = signal('');
  protected readonly entries = signal<AuditEntry[] | null>(null);
  protected readonly total = signal(0);
  protected readonly page = signal(0);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly check = signal<ChainCheck | null>(null);
  protected readonly checking = signal(false);
  protected readonly open = signal<number | null>(null);
  protected readonly timeAgo = timeAgo;
  protected readonly fullTime = fullTime;
  protected readonly auditStyle = auditStyle;
  private debounce: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.debounce));
  }

  ngOnInit(): void {
    void this.load(true);
  }

  protected pick(action: string): void {
    this.category.set(action);
    void this.load(true);
  }

  protected onSearch(value: string): void {
    this.query.set(value);
    clearTimeout(this.debounce);
    this.debounce = setTimeout(() => void this.load(true), 300);
  }

  protected more(): void {
    this.page.update((p) => p + 1);
    void this.load(false);
  }

  protected async verify(): Promise<void> {
    this.checking.set(true);
    try {
      this.check.set(await this.api.verifyAudit());
    } catch (e) {
      this.error.set(problemOf(e).detail);
    } finally {
      this.checking.set(false);
    }
  }

  protected pretty(details: string | null): string {
    if (!details) return '';
    try {
      return JSON.stringify(JSON.parse(details), null, 2);
    } catch {
      return details;
    }
  }

  private async load(reset: boolean): Promise<void> {
    if (reset) this.page.set(0);
    this.loading.set(true);
    this.error.set(null);
    try {
      const result = await this.api.audit({ action: this.category(), q: this.query().trim() }, this.page());
      this.entries.set(reset ? result.items : [...(this.entries() ?? []), ...result.items]);
      this.total.set(result.total);
    } catch (e) {
      this.error.set(problemOf(e).detail);
    } finally {
      this.loading.set(false);
    }
  }
}
