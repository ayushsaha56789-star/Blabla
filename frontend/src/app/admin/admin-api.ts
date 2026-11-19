import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import {
  AdminMe,
  AdminRideDetails,
  AdminRideRow,
  Audience,
  AuditEntry,
  ChainCheck,
  ConsoleSession,
  Dashboard,
  FeatureRow,
  MemberDetailResponse,
  MemberRow,
  PageResult,
  Restriction,
  RoleInfo,
  SetupComplete,
  StaffRow,
  StaffStatus,
  TotpSetup,
} from './admin.models';

/** Calls to the staff console API. Console tokens and 2FA confirmations are added by adminInterceptor. */
@Injectable({ providedIn: 'root' })
export class AdminApi {
  private readonly http = inject(HttpClient);

  // Staff sign-in (uses the normal member token)
  staffStatus() {
    return firstValueFrom(this.http.get<StaffStatus>('/api/auth/staff/status'));
  }
  startSetup() {
    return firstValueFrom(this.http.post<TotpSetup>('/api/auth/staff/totp/setup', {}));
  }
  confirmSetup(code: string) {
    return firstValueFrom(this.http.post<SetupComplete>('/api/auth/staff/totp/confirm', { code }));
  }
  signIn(code: string | null, recoveryCode: string | null) {
    return firstValueFrom(this.http.post<ConsoleSession>('/api/auth/staff/session', { code, recoveryCode }));
  }

  // Console (uses the console token)
  signOut() {
    return firstValueFrom(this.http.post<void>('/api/auth/staff/logout', {}));
  }
  newRecoveryCodes() {
    return firstValueFrom(this.http.post<{ recoveryCodes: string[] }>('/api/auth/staff/recovery-codes', {}));
  }
  me() {
    return firstValueFrom(this.http.get<AdminMe>('/api/admin/me'));
  }
  roles() {
    return firstValueFrom(this.http.get<RoleInfo[]>('/api/admin/roles'));
  }
  dashboard() {
    return firstValueFrom(this.http.get<Dashboard>('/api/admin/dashboard'));
  }
  members(q: string, page: number, size = 20) {
    const params = new HttpParams().set('q', q).set('page', page).set('size', size);
    return firstValueFrom(this.http.get<PageResult<MemberRow>>('/api/admin/members', { params }));
  }
  member(id: string) {
    return firstValueFrom(this.http.get<MemberDetailResponse>(`/api/admin/members/${encodeURIComponent(id)}`));
  }
  revealPhone(id: string) {
    return firstValueFrom(this.http.post<{ phone: string }>(`/api/admin/members/${encodeURIComponent(id)}/contact`, {}));
  }
  restrict(id: string, body: { area: string; reason: string; until: string | null }) {
    return firstValueFrom(this.http.post<Restriction>(`/api/admin/members/${encodeURIComponent(id)}/restrictions`, body));
  }
  lift(id: string, restrictionId: string) {
    return firstValueFrom(
      this.http.delete<Restriction>(`/api/admin/members/${encodeURIComponent(id)}/restrictions/${encodeURIComponent(restrictionId)}`),
    );
  }
  setBetaTester(id: string, betaTester: boolean) {
    return firstValueFrom(this.http.put<MemberDetailResponse>(`/api/admin/members/${encodeURIComponent(id)}/beta-tester`, { betaTester }));
  }
  features() {
    return firstValueFrom(this.http.get<FeatureRow[]>('/api/admin/features'));
  }
  updateFeature(key: string, body: { enabled: boolean; audience: Audience; cities: string[] }) {
    return firstValueFrom(this.http.put<FeatureRow>(`/api/admin/features/${encodeURIComponent(key)}`, body));
  }
  staff() {
    return firstValueFrom(this.http.get<StaffRow[]>('/api/admin/staff'));
  }
  addStaff(phone: string, role: string) {
    return firstValueFrom(this.http.post<StaffRow>('/api/admin/staff', { phone, role }));
  }
  changeRole(userId: string, role: string) {
    return firstValueFrom(this.http.put<StaffRow>(`/api/admin/staff/${encodeURIComponent(userId)}`, { role }));
  }
  removeStaff(userId: string) {
    return firstValueFrom(this.http.delete<void>(`/api/admin/staff/${encodeURIComponent(userId)}`));
  }
  resetTwoFactor(userId: string) {
    return firstValueFrom(this.http.post<void>(`/api/admin/staff/${encodeURIComponent(userId)}/reset-2fa`, {}));
  }
  audit(filter: { action?: string; q?: string; targetType?: string; targetId?: string }, page: number, size = 30) {
    let params = new HttpParams().set('page', page).set('size', size);
    for (const [k, v] of Object.entries(filter)) {
      if (v) params = params.set(k, v);
    }
    return firstValueFrom(this.http.get<PageResult<AuditEntry>>('/api/admin/audit', { params }));
  }
  verifyAudit() {
    return firstValueFrom(this.http.get<ChainCheck>('/api/admin/audit/verify'));
  }
  rides(show: string, q: string, page: number, size = 25) {
    const params = new HttpParams().set('show', show).set('q', q).set('page', page).set('size', size);
    return firstValueFrom(this.http.get<PageResult<AdminRideRow>>('/api/admin/rides', { params }));
  }
  ride(id: string) {
    return firstValueFrom(this.http.get<AdminRideDetails>(`/api/admin/rides/${encodeURIComponent(id)}`));
  }
  cancelRide(id: string, reason: string) {
    return firstValueFrom(this.http.post<AdminRideDetails>(`/api/admin/rides/${encodeURIComponent(id)}/cancel`, { reason }));
  }
}
