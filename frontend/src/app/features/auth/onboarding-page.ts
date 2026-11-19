import { ChangeDetectionStrategy, Component, inject, input, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Gender } from '../../core/api.models';
import { AuthService } from '../../core/auth.service';
import { problemOf } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { UserApi } from '../../core/user-api';
import { safeReturnUrl } from '../../core/util';
import { Icon } from '../../shared/icon';

@Component({
  selector: 'app-onboarding-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, Icon],
  templateUrl: './onboarding-page.html',
})
export class OnboardingPage {
  private readonly auth = inject(AuthService);
  private readonly api = inject(UserApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly returnUrl = input<string>();

  protected readonly genders: ReadonlyArray<{ value: Gender; label: string }> = [
    { value: 'FEMALE', label: 'Woman' },
    { value: 'MALE', label: 'Man' },
    { value: 'OTHER', label: 'Other' },
  ];

  protected readonly form = inject(FormBuilder).nonNullable.group({
    firstName: [this.auth.user()?.firstName ?? '', [Validators.required, Validators.maxLength(50)]],
    lastName: [this.auth.user()?.lastName ?? '', [Validators.maxLength(50)]],
    gender: [this.auth.user()?.gender ?? (null as Gender | null), [Validators.required]],
    dateOfBirth: [this.auth.user()?.dateOfBirth ?? ''],
  });

  protected readonly saving = signal(false);
  protected readonly serverErrors = signal<Record<string, string>>({});

  protected async save(event: Event): Promise<void> {
    event.preventDefault();
    this.form.markAllAsTouched();
    const me = this.auth.user();
    if (this.form.invalid || !me) return;
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.serverErrors.set({});
    try {
      const updated = await this.api.updateMe({
        firstName: v.firstName.trim(),
        lastName: v.lastName.trim() || null,
        gender: v.gender,
        dateOfBirth: v.dateOfBirth || null,
        email: me.email,
        bio: me.bio,
        prefChat: me.prefChat,
        prefMusic: me.prefMusic,
        prefSmoking: me.prefSmoking,
        prefPets: me.prefPets,
      });
      this.auth.setUser(updated);
      this.toast.success(`Welcome to SaathChalo, ${updated.firstName}! 🎉`);
      await this.router.navigateByUrl(safeReturnUrl(this.returnUrl()));
    } catch (e) {
      const p = problemOf(e);
      this.serverErrors.set(p.errors ?? {});
      if (!p.errors) this.toast.error(p.detail);
    } finally {
      this.saving.set(false);
    }
  }

  protected skip(): void {
    void this.router.navigateByUrl('/');
  }
}
