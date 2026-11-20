import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { MemberCard } from './rides.models';

/** Amounts are in paise (₹1 = 100 paise). */
export interface PaymentOptions {
  /** The owner switched online payments on and a gateway is set up. */
  online: boolean;
  /** Paying the driver directly (cash or UPI) is allowed. */
  cash: boolean;
  /** SaathChalo credits can be used. */
  credits: boolean;
  creditsPaise: number;
  serviceFeePercent: number;
  gateway: 'RAZORPAY' | 'FAKE' | null;
  fullRefundHours: number;
  lateCancelRefundPercent: number;
}

export interface CheckoutRequest {
  rideId: string;
  fromPosition: number;
  toPosition: number;
  seats: number;
  message: string | null;
  useCredits: boolean;
}

export interface CheckoutView {
  paymentId: string;
  bookingId: string;
  status: 'CREATED' | 'PAID';
  seatPaise: number;
  feePaise: number;
  creditsPaise: number;
  onlinePaise: number;
  gateway: 'RAZORPAY' | 'FAKE' | 'CREDITS';
  keyId: string | null;
  orderId: string | null;
  holdUntil: string;
  description: string;
  prefill: { name: string | null; email: string | null; contact: string | null };
}

export interface ConfirmBody {
  gatewayPaymentId: string;
  signature: string;
}

export interface RefundView {
  id: string;
  onlinePaise: number;
  creditsPaise: number;
  status: 'PENDING' | 'PROCESSED' | 'FAILED';
  reason: string;
  createdAt: string;
  processedAt: string | null;
}

export interface PaymentView {
  id: string;
  bookingId: string;
  status: 'CREATED' | 'PAID' | 'EXPIRED';
  outcome: 'SETTLED' | 'REFUNDED' | 'SPLIT' | null;
  seatPaise: number;
  feePaise: number;
  creditsPaise: number;
  onlinePaise: number;
  gateway: string;
  holdUntil: string;
  paidAt: string | null;
  /** False when the money came too late to keep the seat (it's refunded in full). */
  seatHeld: boolean;
  refunds: RefundView[];
}

export interface RefundPreview {
  paid: boolean;
  backOnlinePaise: number;
  backCreditsPaise: number;
  keptPaise: number;
  explanation: string;
}

export interface PayoutView {
  id: string;
  driver: MemberCard;
  amountPaise: number;
  method: 'LINK' | 'MANUAL';
  status: 'SENT' | 'PAID' | 'CANCELLED';
  linkUrl: string | null;
  reference: string | null;
  createdAt: string;
  paidAt: string | null;
}

export interface WalletLine {
  at: string;
  kind: string;
  account: 'CREDITS' | 'EARNINGS';
  amountPaise: number;
  memo: string | null;
  paymentId: string | null;
}

export interface WalletView {
  creditsPaise: number;
  earningsPaise: number;
  paidOutPaise: number;
  history: WalletLine[];
  payouts: PayoutView[];
}

@Injectable({ providedIn: 'root' })
export class PaymentsApi {
  private readonly http = inject(HttpClient);

  options(): Promise<PaymentOptions> {
    return firstValueFrom(this.http.get<PaymentOptions>('/api/payments/options'));
  }

  checkout(body: CheckoutRequest): Promise<CheckoutView> {
    return firstValueFrom(this.http.post<CheckoutView>('/api/payments/checkout', body));
  }

  resume(paymentId: string): Promise<CheckoutView> {
    return firstValueFrom(this.http.post<CheckoutView>(`/api/payments/${encodeURIComponent(paymentId)}/resume`, null));
  }

  confirm(paymentId: string, body: ConfirmBody): Promise<PaymentView> {
    return firstValueFrom(this.http.post<PaymentView>(`/api/payments/${encodeURIComponent(paymentId)}/confirm`, body));
  }

  /** Development only: the pretend checkout's answer. */
  pretendPay(paymentId: string): Promise<ConfirmBody> {
    return firstValueFrom(this.http.post<ConfirmBody>(`/api/payments/${encodeURIComponent(paymentId)}/pretend-pay`, null));
  }

  forBookings(bookingIds: string[]): Promise<PaymentView[]> {
    if (!bookingIds.length) return Promise.resolve([]);
    const params = new HttpParams().set('ids', bookingIds.join(','));
    return firstValueFrom(this.http.get<PaymentView[]>('/api/payments/bookings', { params }));
  }

  refundPreview(bookingId: string): Promise<RefundPreview> {
    return firstValueFrom(this.http.get<RefundPreview>(`/api/payments/bookings/${encodeURIComponent(bookingId)}/refund-preview`));
  }

  wallet(): Promise<WalletView> {
    return firstValueFrom(this.http.get<WalletView>('/api/wallet'));
  }
}

const INR = new Intl.NumberFormat('en-IN', { maximumFractionDigits: 2, minimumFractionDigits: 0 });

/** 60000 → "₹600", 905 → "₹9.05". */
export function paise(amount: number): string {
  const r = amount / 100;
  return `${r < 0 ? '−' : ''}₹${INR.format(Math.abs(r))}`;
}

/** The service fee the server will charge, worked out the same way (rounded to the nearest paisa). */
export function feeFor(seatPaise: number, percent: number): number {
  return Math.round((seatPaise * percent) / 100);
}

/** How a ledger line reads to the member. */
export function walletLineLabel(line: WalletLine): string {
  switch (line.kind) {
    case 'CREDITS_HOLD': return 'Paid for a seat with credits';
    case 'CREDITS_RELEASE': return 'Credits back (payment not finished)';
    case 'REFUND': return line.account === 'EARNINGS' ? 'Late cancellation: your share' : 'Refund as credits';
    case 'SETTLE': return 'Earned from a trip';
    case 'GOODWILL': return 'Gift from SaathChalo';
    case 'PAYOUT': return 'Paid out to you';
    case 'PAYOUT_CANCELLED': return 'Payout link not used: back in your earnings';
    case 'CREDITS_FORFEITED': return 'Credits removed';
    default: return line.memo ?? line.kind;
  }
}
