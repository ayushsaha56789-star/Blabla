import { DatePipe, DecimalPipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { AdminApi } from '../admin-api';
import { MemberRow, PageResult } from '../admin.models';

@Component({
  selector: 'app-admin-members',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, DatePipe, DecimalPipe, Avatar, Icon],
  templateUrl: './members.html',
})
export class MembersPage implements OnInit {
  private readonly api = inject(AdminApi);

  protected readonly query = signal('');
  protected readonly page = signal(0);
  protected readonly result = signal<PageResult<MemberRow> | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal<string | null>(null);
  private debounce: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    inject(DestroyRef).onDestroy(() => clearTimeout(this.debounce));
  }

  ngOnInit(): void {
    void this.load();
  }

  protected onSearch(value: string): void {
    this.query.set(value);
    clearTimeout(this.debounce);
    this.debounce = setTimeout(() => {
      this.page.set(0);
      void this.load();
    }, 300);
  }

  protected go(delta: number): void {
    this.page.update((p) => Math.max(0, p + delta));
    void this.load();
  }

  protected pages(r: PageResult<MemberRow>): number {
    return Math.max(1, Math.ceil(r.total / r.size));
  }

  private async load(): Promise<void> {
    this.loading.set(true);
    this.error.set(null);
    try {
      this.result.set(await this.api.members(this.query().trim(), this.page()));
    } catch (e) {
      this.error.set(problemOf(e).detail);
    } finally {
      this.loading.set(false);
    }
  }
}
