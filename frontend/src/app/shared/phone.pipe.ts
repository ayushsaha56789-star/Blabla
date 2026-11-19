import { Pipe, PipeTransform } from '@angular/core';

/** "+919876543210" → "+91 98765 43210". */
@Pipe({ name: 'phone' })
export class PhonePipe implements PipeTransform {
  transform(e164: string | null | undefined): string {
    const m = /^\+91(\d{5})(\d{5})$/.exec(e164 ?? '');
    return m ? `+91 ${m[1]} ${m[2]}` : (e164 ?? '');
  }
}
