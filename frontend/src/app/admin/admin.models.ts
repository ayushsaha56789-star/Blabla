/** Shapes of the staff console API (backend: admin/AdminDtos.java and auth/Staff*.java). */
import { Gender } from '../core/api.models';

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

export interface Dashboard {
  members: MemberStats;
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
