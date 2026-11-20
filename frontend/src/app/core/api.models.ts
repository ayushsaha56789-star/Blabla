/** Shapes of the SaathChalo API — mirrors the *Dtos.java records in the backend. */

export type Gender = 'FEMALE' | 'MALE' | 'OTHER';
export type ChatPreference = 'QUIET' | 'SOMETIMES' | 'CHATTY';

export interface Me {
  id: string;
  phone: string;
  firstName: string | null;
  lastName: string | null;
  displayName: string;
  email: string | null;
  gender: Gender | null;
  dateOfBirth: string | null;
  city: string | null;
  bio: string | null;
  avatarUrl: string | null;
  prefChat: ChatPreference;
  prefMusic: boolean;
  prefSmoking: boolean;
  prefPets: boolean;
  phoneVerified: boolean;
  idVerified: boolean;
  emailVerified: boolean;
  betaTester: boolean;
  profileComplete: boolean;
  memberSince: string;
}

export interface PublicProfile {
  id: string;
  displayName: string;
  avatarUrl: string | null;
  bio: string | null;
  age: number | null;
  city: string | null;
  prefChat: ChatPreference;
  prefMusic: boolean;
  prefSmoking: boolean;
  prefPets: boolean;
  phoneVerified: boolean;
  idVerified: boolean;
  emailVerified: boolean;
  memberSince: string;
  /** Average stars from trip reviews; null until someone reviews them. */
  rating: number | null;
  reviewCount: number;
}

export interface UpdateProfileRequest {
  firstName: string;
  lastName: string | null;
  email: string | null;
  gender: Gender | null;
  dateOfBirth: string | null;
  city: string | null;
  bio: string | null;
  prefChat: ChatPreference;
  prefMusic: boolean;
  prefSmoking: boolean;
  prefPets: boolean;
}

export interface OtpRequestResponse {
  phone: string;
  expiresInSeconds: number;
  resendInSeconds: number;
  /** Only present in local development. */
  devCode: string | null;
}

/** Present when the member is on the SaathChalo staff. */
export interface StaffInfo {
  role: string;
  roleName: string;
  totpEnabled: boolean;
}

export interface AuthResponse {
  accessToken: string;
  expiresAt: string;
  user: Me;
  staff: StaffInfo | null;
  /** This login cancelled the member's request to delete their account. */
  accountRestored?: boolean;
}

export interface FeaturesResponse {
  enabled: string[];
  maintenance: boolean;
}

/** RFC 9457 problem details, as returned by the API for every error. */
export interface ApiProblem {
  status: number;
  detail: string;
  code?: string;
  errors?: Record<string, string>;
  /** Suspensions: why, and until when (ISO time, or absent for "until further notice"). */
  reason?: string;
  until?: string;
  /** Wrong 2FA codes: how many tries are left before a short lock. */
  attemptsLeft?: number;
}
