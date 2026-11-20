import { Injectable, signal } from '@angular/core';
import { CheckoutView, ConfirmBody } from './payments-api';

interface RazorpayResponse {
  razorpay_payment_id: string;
  razorpay_order_id: string;
  razorpay_signature: string;
}

interface RazorpayInstance {
  open(): void;
  on(event: string, handler: (response: unknown) => void): void;
}

type RazorpayConstructor = new (options: Record<string, unknown>) => RazorpayInstance;

const RAZORPAY_SCRIPT = 'https://checkout.razorpay.com/v1/checkout.js';

/**
 * Opens the payment window: Razorpay's own checkout in production (card and UPI details never touch SaathChalo),
 * or a pretend checkout in development. Resolves with the gateway's answer, or null when the member closes it.
 */
@Injectable({ providedIn: 'root' })
export class CheckoutService {
  /** The pretend checkout, shown by the app shell while set. */
  readonly pretend = signal<{ view: CheckoutView; done: (answer: ConfirmBody | null) => void } | null>(null);

  private razorpay: Promise<RazorpayConstructor> | null = null;

  pay(view: CheckoutView): Promise<ConfirmBody | null> {
    if (view.gateway === 'FAKE') {
      return new Promise((resolve) => this.pretend.set({ view, done: (answer) => {
        this.pretend.set(null);
        resolve(answer);
      } }));
    }
    return this.payWithRazorpay(view);
  }

  private async payWithRazorpay(view: CheckoutView): Promise<ConfirmBody | null> {
    const Razorpay = await this.loadRazorpay();
    return new Promise((resolve) => {
      let finished = false;
      const checkout = new Razorpay({
        key: view.keyId,
        order_id: view.orderId,
        amount: view.onlinePaise,
        currency: 'INR',
        name: 'SaathChalo',
        description: view.description,
        prefill: { name: view.prefill.name ?? undefined, email: view.prefill.email ?? undefined, contact: view.prefill.contact ?? undefined },
        theme: { color: '#059669' },
        handler: (r: RazorpayResponse) => {
          finished = true;
          resolve({ gatewayPaymentId: r.razorpay_payment_id, signature: r.razorpay_signature });
        },
        modal: { ondismiss: () => !finished && resolve(null) },
      });
      checkout.open();
    });
  }

  private loadRazorpay(): Promise<RazorpayConstructor> {
    this.razorpay ??= new Promise((resolve, reject) => {
      const existing = (window as unknown as { Razorpay?: RazorpayConstructor }).Razorpay;
      if (existing) {
        resolve(existing);
        return;
      }
      const script = document.createElement('script');
      script.src = RAZORPAY_SCRIPT;
      script.async = true;
      script.onload = () => resolve((window as unknown as { Razorpay: RazorpayConstructor }).Razorpay);
      script.onerror = () => {
        this.razorpay = null;
        reject(new Error("We couldn't open the payment window. Check your internet connection and try again."));
      };
      document.head.appendChild(script);
    });
    return this.razorpay;
  }
}
