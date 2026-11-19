import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { Me, PublicProfile, UpdateProfileRequest } from './api.models';

@Injectable({ providedIn: 'root' })
export class UserApi {
  private readonly http = inject(HttpClient);

  updateMe(body: UpdateProfileRequest): Promise<Me> {
    return firstValueFrom(this.http.put<Me>('/api/users/me', body));
  }

  uploadAvatar(photo: Blob): Promise<Me> {
    const form = new FormData();
    form.append('file', photo, 'avatar.jpg');
    return firstValueFrom(this.http.post<Me>('/api/users/me/avatar', form));
  }

  removeAvatar(): Promise<Me> {
    return firstValueFrom(this.http.delete<Me>('/api/users/me/avatar'));
  }

  publicProfile(id: string): Promise<PublicProfile> {
    return firstValueFrom(this.http.get<PublicProfile>(`/api/users/${encodeURIComponent(id)}`));
  }
}
