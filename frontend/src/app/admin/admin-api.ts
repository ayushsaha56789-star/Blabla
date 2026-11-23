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
  ReportRow,
  SosRow,
  StaffRow,
  StaffStatus,
  StaffIdCheck,
  PaymentsSummary,
  AdminPaymentRow,
  OwedRow,
  StaffTicketRow,
  StaffTicketView,
  TotpSetup,
} from './admin.models';
import { PayoutView, WalletView } from '../core/payments-api';
import { AdminListingRow, RentalView } from '../core/rentals-api';
import { AdminDriverRow, TripView } from '../core/drivers-api';
import { EventCard } from '../core/social-api';
import { AdminPromotionRow } from '../core/promotions-api';

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
  reports(open: boolean) {
    return firstValueFrom(this.http.get<ReportRow[]>('/api/admin/reports', { params: new HttpParams().set('open', open) }));
  }
  acknowledgeReport(id: string) {
    return firstValueFrom(this.http.post<ReportRow>(`/api/admin/reports/${encodeURIComponent(id)}/acknowledge`, null));
  }
  resolveReport(id: string, action: string, resolution: string) {
    return firstValueFrom(this.http.post<ReportRow>(`/api/admin/reports/${encodeURIComponent(id)}/resolve`, { action, resolution }));
  }
  sosAlerts(open: boolean) {
    return firstValueFrom(this.http.get<SosRow[]>('/api/admin/safety/sos', { params: new HttpParams().set('open', open) }));
  }
  acknowledgeSos(id: string) {
    return firstValueFrom(this.http.post<SosRow>(`/api/admin/safety/sos/${encodeURIComponent(id)}/acknowledge`, null));
  }
  resolveSos(id: string, notes: string | null) {
    return firstValueFrom(this.http.post<SosRow>(`/api/admin/safety/sos/${encodeURIComponent(id)}/resolve`, { notes }));
  }
  cancelRide(id: string, reason: string) {
    return firstValueFrom(this.http.post<AdminRideDetails>(`/api/admin/rides/${encodeURIComponent(id)}/cancel`, { reason }));
  }
  rentalListings(status: 'IN_REVIEW' | 'ALL') {
    return firstValueFrom(this.http.get<AdminListingRow[]>('/api/admin/rentals/listings', { params: new HttpParams().set('status', status) }));
  }
  approveListing(id: string) {
    return firstValueFrom(this.http.post<AdminListingRow>(`/api/admin/rentals/listings/${encodeURIComponent(id)}/approve`, null));
  }
  rejectListing(id: string, note: string) {
    return firstValueFrom(this.http.post<AdminListingRow>(`/api/admin/rentals/listings/${encodeURIComponent(id)}/reject`, { note }));
  }
  rentalBookings(filter: 'all' | 'disputes') {
    return firstValueFrom(this.http.get<RentalView[]>('/api/admin/rentals/bookings', { params: new HttpParams().set('filter', filter) }));
  }
  resolveRental(id: string, extraCharges: number, resolution: string) {
    return firstValueFrom(this.http.post<RentalView>(`/api/admin/rentals/bookings/${encodeURIComponent(id)}/resolve`, { extraCharges, resolution }));
  }
  cancelRental(id: string) {
    return firstValueFrom(this.http.post<RentalView>(`/api/admin/rentals/bookings/${encodeURIComponent(id)}/cancel`, null));
  }
  promotions(status: 'IN_REVIEW' | 'LIVE' | 'ALL') {
    return firstValueFrom(this.http.get<AdminPromotionRow[]>('/api/admin/promotions', { params: new HttpParams().set('status', status) }));
  }
  promotionAction(id: string, action: 'approve' | 'reject' | 'take-down', note: string | null) {
    return firstValueFrom(this.http.post<AdminPromotionRow>(`/api/admin/promotions/${encodeURIComponent(id)}/${action}`, note ? { note } : null));
  }
  events() {
    return firstValueFrom(this.http.get<EventCard[]>('/api/admin/events'));
  }
  saveEvent(id: string | null, body: { title: string; venue: string; placeId: number; startsAt: string; description: string | null }) {
    return firstValueFrom(id ? this.http.put<EventCard>(`/api/admin/events/${encodeURIComponent(id)}`, body)
      : this.http.post<EventCard>('/api/admin/events', body));
  }
  cancelEvent(id: string) {
    return firstValueFrom(this.http.post<EventCard>(`/api/admin/events/${encodeURIComponent(id)}/cancel`, null));
  }
  drivers(status: 'IN_REVIEW' | 'ALL') {
    return firstValueFrom(this.http.get<AdminDriverRow[]>('/api/admin/drivers', { params: new HttpParams().set('status', status) }));
  }
  driverLicenceNumber(id: string) {
    return firstValueFrom(this.http.post<{ number: string | null }>(`/api/admin/drivers/${encodeURIComponent(id)}/licence-number`, null));
  }
  approveDriver(id: string, policeChecked: boolean) {
    return firstValueFrom(this.http.post<AdminDriverRow>(`/api/admin/drivers/${encodeURIComponent(id)}/approve`, { policeChecked }));
  }
  rejectDriver(id: string, note: string) {
    return firstValueFrom(this.http.post<AdminDriverRow>(`/api/admin/drivers/${encodeURIComponent(id)}/reject`, { note }));
  }
  driverPoliceChecked(id: string) {
    return firstValueFrom(this.http.post<AdminDriverRow>(`/api/admin/drivers/${encodeURIComponent(id)}/police-checked`, null));
  }
  driverTrips() {
    return firstValueFrom(this.http.get<TripView[]>('/api/admin/drivers/trips'));
  }
  cancelDriverTrip(id: string, reason: string | null) {
    return firstValueFrom(this.http.post<TripView>(`/api/admin/drivers/trips/${encodeURIComponent(id)}/cancel`, { reason }));
  }
  paymentsSummary() {
    return firstValueFrom(this.http.get<PaymentsSummary>('/api/admin/payments/summary'));
  }
  payments(filter: 'all' | 'problems') {
    return firstValueFrom(this.http.get<AdminPaymentRow[]>('/api/admin/payments', { params: new HttpParams().set('filter', filter) }));
  }
  retryRefund(id: string) {
    return firstValueFrom(this.http.post<void>(`/api/admin/refunds/${encodeURIComponent(id)}/retry`, null));
  }
  payoutsOwed() {
    return firstValueFrom(this.http.get<OwedRow[]>('/api/admin/payouts/owed'));
  }
  payouts() {
    return firstValueFrom(this.http.get<PayoutView[]>('/api/admin/payouts'));
  }
  payout(driverId: string, method: 'LINK' | 'MANUAL', reference: string | null) {
    return firstValueFrom(this.http.post<PayoutView>('/api/admin/payouts', { driverId, method, reference }));
  }
  memberWallet(id: string) {
    return firstValueFrom(this.http.get<WalletView>(`/api/admin/members/${encodeURIComponent(id)}/wallet`));
  }
  grantCredits(id: string, amountPaise: number, reason: string) {
    return firstValueFrom(this.http.post<WalletView>(`/api/admin/members/${encodeURIComponent(id)}/credits`, { amountPaise, reason }));
  }
  verifications(status: 'PENDING' | 'APPROVED' | 'REJECTED') {
    return firstValueFrom(this.http.get<StaffIdCheck[]>('/api/admin/verifications', { params: new HttpParams().set('status', status) }));
  }
  verificationPhoto(id: string) {
    return firstValueFrom(this.http.get(`/api/admin/verifications/${encodeURIComponent(id)}/photo`, { responseType: 'blob' }));
  }
  approveVerification(id: string) {
    return firstValueFrom(this.http.post<StaffIdCheck>(`/api/admin/verifications/${encodeURIComponent(id)}/approve`, null));
  }
  rejectVerification(id: string, reason: string, note: string | null) {
    return firstValueFrom(this.http.post<StaffIdCheck>(`/api/admin/verifications/${encodeURIComponent(id)}/reject`, { reason, note }));
  }
  tickets(status: 'OPEN' | 'ANSWERED' | 'CLOSED' | 'ALL') {
    return firstValueFrom(this.http.get<StaffTicketRow[]>('/api/admin/support/tickets', { params: new HttpParams().set('status', status) }));
  }
  ticket(id: string) {
    return firstValueFrom(this.http.get<StaffTicketView>(`/api/admin/support/tickets/${encodeURIComponent(id)}`));
  }
  replyToTicket(id: string, body: string) {
    return firstValueFrom(this.http.post<StaffTicketView>(`/api/admin/support/tickets/${encodeURIComponent(id)}/messages`, { body }));
  }
  closeTicket(id: string) {
    return firstValueFrom(this.http.post<StaffTicketView>(`/api/admin/support/tickets/${encodeURIComponent(id)}/close`, null));
  }
}
