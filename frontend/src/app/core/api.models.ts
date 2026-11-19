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
  bio: string | null;
  avatarUrl: string | null;
  prefChat: ChatPreference;
  prefMusic: boolean;
  prefSmoking: boolean;
  prefPets: boolean;
  phoneVerified: boolean;
  idVerified: boolean;
  role: 'USER' | 'ADMIN';
  profileComplete: boolean;
  memberSince: string;
}

export interface PublicProfile {
  id: string;
  displayName: string;
  avatarUrl: string | null;
  bio: string | null;
  age: number | null;
  prefChat: ChatPreference;
  prefMusic: boolean;
  prefSmoking: boolean;
  prefPets: boolean;
  phoneVerified: boolean;
  idVerified: boolean;
  memberSince: string;
}

export interface UpdateProfileRequest {
  firstName: string;
  lastName: string | null;
  email: string | null;
  gender: Gender | null;
  dateOfBirth: string | null;
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

export interface AuthResponse {
  accessToken: string;
  expiresAt: string;
  user: Me;
}

/** RFC 9457 problem details, as returned by the API for every error. */
export interface ApiProblem {
  status: number;
  detail: string;
  code?: string;
  errors?: Record<string, string>;
}
