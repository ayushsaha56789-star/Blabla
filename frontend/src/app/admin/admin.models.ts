/** Shapes of the staff console API (backend: admin/AdminDtos.java and auth/Staff*.java). */
import { Gender } from '../core/api.models';
import { MemberCard, PassengerView, RideDetails, RideStatus } from '../core/rides.models';
import { SupportCategory, SupportMessage } from '../core/support-api';
import { DocumentType, RejectReason } from '../core/verification-api';
import { RefundView } from '../core/payments-api';

export interface StaffStatus {
  staff: boolean;
  role: string | null;
  roleName: string | null;
  totpEnabled: boolean;
  recoveryCodesLeft: number;
}

export interface TotpSetup {
  secret: string;
  otpauthUri: string;
  qrCode: string;
}

export interface ConsoleSession {
  token: string;
  expiresAt: string;
  idleTimeoutSeconds: number;
}

export interface SetupComplete {
  recoveryCodes: string[];
  session: ConsoleSession;
}

export interface AdminMe {
  userId: string;
  displayName: string;
  avatarUrl: string | null;
  role: string;
  roleName: string;
  permissions: string[];
}

export interface RoleInfo {
  code: string;
  name: string;
  description: string;
  permissions: { code: string; label: string }[];
}

export interface DailyCount {
  day: string;
  count: number;
}

export interface MemberStats {
  total: number;
  joinedToday: number;
  joinedLast7Days: number;
  lastDays: DailyCount[];
}

export interface AuditEntry {
  id: number;
  occurredAt: string;
  actorId: string | null;
  actorLabel: string;
  action: string;
  targetType: string | null;
  targetId: string | null;
  summary: string;
  details: string | null;
  ip: string | null;
  requestId: string | null;
}

export interface RideStats {
  upcoming: number;
  publishedLast7Days: number;
  bookingsLast7Days: number;
  seatsBookedUpcoming: number;
  cancelledLast7Days: number;
}

export interface AdminRideRow {
  id: string;
  status: RideStatus;
  departureAt: string;
  fromName: string;
  toName: string;
  stopCount: number;
  driver: MemberCard;
  seatsTotal: number;
  seatsBooked: number;
  pendingRequests: number;
  pricePerSeat: number;
  ladiesOnly: boolean;
  createdAt: string;
}

export interface ReportRow {
  id: string;
  status: 'OPEN' | 'ACTIONED' | 'DISMISSED';
  targetType: 'MEMBER' | 'RIDE' | 'MESSAGE' | 'REVIEW';
  targetId: string;
  rideId: string | null;
  reason: 'SAFETY' | 'HARASSMENT' | 'FAKE_PROFILE' | 'NO_SHOW' | 'SPAM' | 'INAPPROPRIATE' | 'OTHER';
  details: string | null;
  snapshot: string | null;
  reporter: MemberCard;
  reported: MemberCard;
  reportsAboutMember: number;
  createdAt: string;
  acknowledgedAt: string | null;
  acknowledgeBy: string;
  resolveBy: string;
  overdue: boolean;
  resolvedAt: string | null;
  handledBy: string | null;
  resolution: string | null;
}

export interface SosRow {
  id: string;
  status: 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED';
  member: MemberCard;
  maskedPhone: string | null;
  ride: { id: string; fromName: string; toName: string } | null;
  lat: number | null;
  lng: number | null;
  accuracy: number | null;
  near: string | null;
  contactsNotified: number;
  createdAt: string;
  acknowledgedAt: string | null;
  acknowledgedBy: string | null;
  resolvedAt: string | null;
  resolvedBy: string | null;
  notes: string | null;
}

export interface AdminRideDetails {
  ride: RideDetails;
  bookings: PassengerView[];
}

/** Money in the console. Amounts are in paise. */
export interface PaymentsSummary {
  onlineReady: boolean;
  gateway: string | null;
  heldForTripsPaise: number;
  owedToDriversPaise: number;
  creditsOutstandingPaise: number;
  feesEarnedPaise: number;
  goodwillPaise: number;
  refundsPending: number;
  refundsFailed: number;
  ledgerBalanced: boolean;
  walletsMatch: boolean;
  escrowMatches: boolean;
}

export interface AdminPaymentRow {
  id: string;
  bookingId: string;
  payer: MemberCard;
  driver: MemberCard;
  route: string | null;
  departureAt: string | null;
  seatPaise: number;
  feePaise: number;
  creditsPaise: number;
  onlinePaise: number;
  gateway: string;
  status: 'CREATED' | 'PAID' | 'EXPIRED';
  outcome: 'SETTLED' | 'REFUNDED' | 'SPLIT' | null;
  createdAt: string;
  paidAt: string | null;
  refunds: RefundView[];
}

export interface OwedRow {
  driver: MemberCard;
  earningsPaise: number;
  lastPayoutAt: string | null;
}

export interface StaffIdCheck {
  id: string;
  member: MemberCard;
  fullName: string | null;
  gender: Gender | null;
  dateOfBirth: string | null;
  age: number | null;
  documentType: DocumentType;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  submittedAt: string;
  decidedAt: string | null;
  decidedBy: string | null;
  rejectReason: RejectReason | null;
  rejectNote: string | null;
  photoAvailable: boolean;
}

export interface StaffTicketRow {
  id: string;
  member: MemberCard;
  category: SupportCategory;
  subject: string;
  status: 'OPEN' | 'ANSWERED' | 'CLOSED';
  createdAt: string;
  updatedAt: string;
  firstResponseAt: string | null;
  respondBy: string;
  overdue: boolean;
}

export interface StaffTicketView {
  ticket: StaffTicketRow;
  maskedPhone: string | null;
  rideId: string | null;
  messages: SupportMessage[];
}

export interface Dashboard {
  members: MemberStats;
  rides: RideStats;
  openSosAlerts: number;
  openReports: number;
  overdueReports: number;
  ticketsWaiting: number;
  idChecksWaiting: number;
  rentalsToCheck: number;
  rentalDisputes: number;
  driversToCheck: number;
  staffCount: number;
  featuresOn: number;
  featuresTotal: number;
  maintenance: boolean;
  signupsOpen: boolean;
  recentActivity: AuditEntry[];
}

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface MemberRow {
  id: string;
  displayName: string;
  maskedPhone: string;
  avatarUrl: string | null;
  gender: Gender | null;
  city: string | null;
  memberSince: string;
  betaTester: boolean;
  suspended: boolean;
  restrictedAreas: string[];
  staffRole: string | null;
  staffRoleName: string | null;
}

export interface Restriction {
  id: string;
  area: string;
  areaLabel: string;
  reason: string;
  startsAt: string;
  endsAt: string | null;
  liftedAt: string | null;
  active: boolean;
}

export interface MemberDetails {
  id: string;
  displayName: string;
  firstName: string | null;
  lastName: string | null;
  maskedPhone: string;
  avatarUrl: string | null;
  gender: Gender | null;
  age: number | null;
  city: string | null;
  bio: string | null;
  memberSince: string;
  phoneVerified: boolean;
  idVerified: boolean;
  betaTester: boolean;
  profileComplete: boolean;
  suspended: boolean;
  restrictions: Restriction[];
}

export interface MemberDetailResponse {
  member: MemberDetails;
  staffRole: string | null;
  staffRoleName: string | null;
  activity: AuditEntry[];
  areas: { key: string; label: string }[];
}

export type Audience = 'EVERYONE' | 'VERIFIED' | 'WOMEN' | 'BETA' | 'CITIES';

export interface FeatureRow {
  key: string;
  parent: string | null;
  area: string;
  label: string;
  description: string;
  phase: number;
  built: boolean;
  critical: boolean;
  global: boolean;
  enabled: boolean;
  audience: Audience;
  cities: string[];
  updatedAt: string | null;
  updatedByName: string | null;
}

export interface StaffRow {
  userId: string;
  displayName: string;
  maskedPhone: string | null;
  avatarUrl: string | null;
  role: string;
  roleName: string;
  totpEnabled: boolean;
  addedAt: string;
  lastActiveAt: string | null;
  you: boolean;
}

export interface ChainCheck {
  intact: boolean;
  checked: number;
  firstBrokenId: number | null;
}
