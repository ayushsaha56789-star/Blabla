import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ChatPreference, Gender, Me } from '../../core/api.models';
import { AuthService } from '../../core/auth.service';
import { problemOf } from '../../core/errors';
import { ToastService } from '../../core/toast.service';
import { UserApi } from '../../core/user-api';
import { squareJpeg } from '../../core/util';
import { Avatar } from '../../shared/avatar';
import { Icon, IconName } from '../../shared/icon';
import { PhonePipe } from '../../shared/phone.pipe';
import { CHAT_OPTIONS } from './prefs';
import { MyCars } from './my-cars';
import { FeaturesService } from '../../core/features.service';
import { POPULAR_CITIES } from '../../core/cities';

@Component({
  selector: 'app-profile-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, RouterLink, DatePipe, Avatar, Icon, PhonePipe, MyCars],
  templateUrl: './profile-page.html',
})
export class ProfilePage {
  protected readonly auth = inject(AuthService);
  protected readonly features = inject(FeaturesService);
  private readonly api = inject(UserApi);
  private readonly toast = inject(ToastService);

  protected readonly chatOptions = CHAT_OPTIONS;
  protected readonly cities = POPULAR_CITIES;
  protected readonly genders: ReadonlyArray<{ value: Gender; label: string }> = [
    { value: 'FEMALE', label: 'Woman' },
    { value: 'MALE', label: 'Man' },
    { value: 'OTHER', label: 'Other' },
  ];
  protected readonly toggles: ReadonlyArray<{ key: 'prefMusic' | 'prefSmoking' | 'prefPets'; icon: IconName; label: string }> = [
    { key: 'prefMusic', icon: 'music', label: 'Music in the car' },
    { key: 'prefSmoking', icon: 'smoke', label: 'Smoking is OK' },
    { key: 'prefPets', icon: 'paw', label: 'Pets welcome' },
  ];

  private readonly fb = inject(FormBuilder).nonNullable;
  protected readonly form = this.fb.group({
    firstName: ['', [Validators.required, Validators.maxLength(50)]],
    lastName: ['', [Validators.maxLength(50)]],
    email: ['', [Validators.email, Validators.maxLength(254)]],
    gender: [null as Gender | null, [Validators.required]],
    dateOfBirth: [''],
    city: ['', [Validators.maxLength(60)]],
    bio: ['', [Validators.maxLength(500)]],
    prefChat: ['SOMETIMES' as ChatPreference],
    prefMusic: [true],
    prefSmoking: [false],
    prefPets: [false],
  });

  protected readonly saving = signal(false);
  protected readonly uploading = signal(false);
  protected readonly serverErrors = signal<Record<string, string>>({});

  constructor() {
    const me = this.auth.user();
    if (me) this.fill(me);
  }

  protected async save(event: Event): Promise<void> {
    event.preventDefault();
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.toast.error('Please fix the highlighted fields.');
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.serverErrors.set({});
    try {
      const me = await this.api.updateMe({
        ...v,
        firstName: v.firstName.trim(),
        lastName: v.lastName.trim() || null,
        email: v.email.trim() || null,
        dateOfBirth: v.dateOfBirth || null,
        city: v.city.trim() || null,
        bio: v.bio.trim() || null,
      });
      this.auth.setUser(me);
      this.fill(me);
      this.toast.success('Profile saved.');
    } catch (e) {
      const p = problemOf(e);
      this.serverErrors.set(p.errors ?? {});
      this.toast.error(p.detail);
    } finally {
      this.saving.set(false);
    }
  }

  protected async onPhotoChosen(input: HTMLInputElement): Promise<void> {
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (!file.type.startsWith('image/')) {
      this.toast.error('Please choose a photo (JPG or PNG).');
      return;
    }
    this.uploading.set(true);
    try {
      let photo: Blob;
      try {
        photo = await squareJpeg(file, 512);
      } catch {
        this.toast.error("We couldn't read that photo. Please try a JPG or PNG.");
        return;
      }
      this.auth.setUser(await this.api.uploadAvatar(photo));
      this.toast.success('Looking good! Photo updated.');
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.uploading.set(false);
    }
  }

  protected async removePhoto(): Promise<void> {
    this.uploading.set(true);
    try {
      this.auth.setUser(await this.api.removeAvatar());
      this.toast.info('Photo removed.');
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.uploading.set(false);
    }
  }

  protected logout(): void {
    void this.auth.logout();
  }

  private fill(me: Me): void {
    this.form.reset({
      firstName: me.firstName ?? '',
      lastName: me.lastName ?? '',
      email: me.email ?? '',
      gender: me.gender,
      dateOfBirth: me.dateOfBirth ?? '',
      city: me.city ?? '',
      bio: me.bio ?? '',
      prefChat: me.prefChat,
      prefMusic: me.prefMusic,
      prefSmoking: me.prefSmoking,
      prefPets: me.prefPets,
    });
  }
}
