import { ChangeDetectionStrategy, Component, computed, inject, OnInit, signal } from '@angular/core';
import { problemOf } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { FeaturesService } from '../../core/features.service';
import { Icon, IconName } from '../../shared/icon';
import { AdminApi } from '../admin-api';
import { Audience, FeatureRow } from '../admin.models';
import { timeAgo } from '../admin-ui';
import { ConfirmationCancelled } from '../confirm-code.service';

const AREAS: ReadonlyArray<{ key: string; title: string; icon: IconName; blurb: string }> = [
  { key: 'core', title: 'Core', icon: 'car', blurb: 'The website itself and carpooling.' },
  { key: 'trust', title: 'Trust & safety', icon: 'shield', blurb: 'Chat, reviews and safety tools.' },
  { key: 'payments', title: 'Payments', icon: 'rupee', blurb: 'How members pay.' },
  { key: 'rentals', title: 'Rentals & drivers', icon: 'steering', blurb: 'Car rentals and driver partners.' },
  { key: 'games', title: 'Games', icon: 'gamepad', blurb: 'Free games for members.' },
  { key: 'social', title: 'Social & rewards', icon: 'trophy', blurb: 'XP, referrals, squads and events.' },
  { key: 'promotions', title: 'Promotions', icon: 'megaphone', blurb: 'Members promoting their business.' },
];

const AUDIENCES: ReadonlyArray<{ value: Audience; label: string }> = [
  { value: 'EVERYONE', label: 'Everyone' },
  { value: 'VERIFIED', label: 'ID-verified' },
  { value: 'WOMEN', label: 'Women' },
  { value: 'BETA', label: 'Beta testers' },
  { value: 'CITIES', label: 'Cities' },
];

@Component({
  selector: 'app-admin-features',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  templateUrl: './features.html',
})
export class FeaturesPage implements OnInit {
  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);
  private readonly memberFeatures = inject(FeaturesService);

  protected readonly rows = signal<FeatureRow[] | null>(null);
  protected readonly saving = signal<ReadonlySet<string>>(new Set());
  protected readonly cityDrafts = signal<Record<string, string>>({});
  protected readonly filter = signal<'all' | 'on' | 'off'>('all');
  protected readonly error = signal<string | null>(null);
  protected readonly audiences = AUDIENCES;
  protected readonly timeAgo = timeAgo;

  protected readonly groups = computed(() => {
    const rows = this.rows() ?? [];
    const f = this.filter();
    return AREAS.map((area) => ({
      ...area,
      rows: rows.filter((r) => r.area === area.key && (f === 'all' || (f === 'on' ? r.enabled : !r.enabled))),
    })).filter((g) => g.rows.length > 0);
  });

  protected readonly onCount = computed(() => (this.rows() ?? []).filter((r) => r.enabled && !r.global).length);
  protected readonly total = computed(() => (this.rows() ?? []).filter((r) => !r.global).length);

  async ngOnInit(): Promise<void> {
    try {
      this.rows.set(await this.api.features());
    } catch (e) {
      this.error.set(problemOf(e).detail);
    }
  }

  protected row(key: string | null): FeatureRow | undefined {
    return key ? this.rows()?.find((r) => r.key === key) : undefined;
  }

  protected isSaving(key: string): boolean {
    return this.saving().has(key);
  }

  protected toggle(r: FeatureRow): void {
    void this.save(r, { enabled: !r.enabled, audience: r.audience, cities: r.cities });
  }

  protected setAudience(r: FeatureRow, audience: Audience): void {
    if (audience === r.audience) return;
    if (audience === 'CITIES') {
      // Pick cities first; saving happens with the city list.
      this.patch({ ...r, audience });
      this.cityDrafts.update((d) => ({ ...d, [r.key]: r.cities.join(', ') }));
      return;
    }
    void this.save(r, { enabled: r.enabled, audience, cities: [] });
  }

  protected saveCities(r: FeatureRow): void {
    const cities = (this.cityDrafts()[r.key] ?? '').split(',').map((c) => c.trim()).filter(Boolean);
    void this.save(r, { enabled: r.enabled, audience: 'CITIES', cities });
  }

  protected draft(key: string, value: string): void {
    this.cityDrafts.update((d) => ({ ...d, [key]: value }));
  }

  private async save(before: FeatureRow, change: { enabled: boolean; audience: Audience; cities: string[] }): Promise<void> {
    const original = this.row(before.key) ?? before;
    this.patch({ ...original, ...change });
    this.saving.update((s) => new Set(s).add(before.key));
    try {
      const saved = await this.api.updateFeature(before.key, change);
      this.patch(saved);
      void this.memberFeatures.load();
      this.toast.success(`${saved.label} is now ${saved.enabled ? 'on' : 'off'}.`);
    } catch (e) {
      this.patch(original);
      if (!(e instanceof ConfirmationCancelled)) this.toast.error(problemOf(e).detail);
    } finally {
      this.saving.update((s) => {
        const next = new Set(s);
        next.delete(before.key);
        return next;
      });
    }
  }

  private patch(updated: FeatureRow): void {
    this.rows.update((rows) => rows?.map((r) => (r.key === updated.key ? updated : r)) ?? rows);
  }
}
