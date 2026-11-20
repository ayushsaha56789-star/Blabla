import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { HelpPage } from '../features/help/help-page';
import { LEGAL_DOCS } from '../features/legal/legal-docs';
import { Me } from './api.models';
import { AuthService } from './auth.service';
import { ticketStatus } from './support-api';
import { DOCUMENTS, REJECT_REASONS } from './verification-api';

describe('help requests', () => {
  it('say where each request stands in plain words', () => {
    expect(ticketStatus('OPEN')).toEqual({ text: 'Waiting for us', tone: 'warning' });
    expect(ticketStatus('ANSWERED').text).toBe('We replied');
    expect(ticketStatus('CLOSED').tone).toBe('muted');
  });

  it('need a topic, a subject and a message before anything is sent', async () => {
    TestBed.configureTestingModule({
      imports: [HelpPage],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    TestBed.inject(AuthService).setUser({ id: 'u1', firstName: 'Priya', displayName: 'Priya' } as Me);
    const backend = TestBed.inject(HttpTestingController);
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(HelpPage);
    fixture.detectChanges();
    backend.expectOne({ method: 'GET', url: '/api/support/tickets' }).flush([]);
    await fixture.whenStable();
    const el = fixture.nativeElement as HTMLElement;
    const form = el.querySelector<HTMLFormElement>('#contact form')!;

    form.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
    expect(el.textContent).toContain('Please choose a topic');
    backend.expectNone({ method: 'POST', url: '/api/support/tickets' });

    const topic = el.querySelector<HTMLSelectElement>('#h-cat')!;
    topic.value = 'BOOKING';
    topic.dispatchEvent(new Event('change'));
    const subject = el.querySelector<HTMLInputElement>('#h-subject')!;
    subject.value = '  Driver was late ';
    subject.dispatchEvent(new Event('input'));
    const body = el.querySelector<HTMLTextAreaElement>('#h-body')!;
    body.value = 'I waited an hour at the pickup point.';
    body.dispatchEvent(new Event('input'));
    form.dispatchEvent(new Event('submit'));

    const sent = backend.expectOne({ method: 'POST', url: '/api/support/tickets' });
    expect(sent.request.body).toEqual({ category: 'BOOKING', subject: 'Driver was late', body: 'I waited an hour at the pickup point.', rideId: null });
    sent.flush({ id: 't1' });
    await fixture.whenStable();
    expect(navigate).toHaveBeenCalledWith(['/help/requests', 't1']);
    backend.verify();
  });
});

describe('policies', () => {
  it('cover the terms, privacy, community rules and complaints, each with real content', () => {
    for (const key of ['terms', 'privacy', 'community', 'grievance'] as const) {
      const doc = LEGAL_DOCS[key];
      expect(doc.sections.length).toBeGreaterThan(1);
      for (const s of doc.sections) {
        expect((s.paragraphs?.length ?? 0) + (s.points?.length ?? 0)).toBeGreaterThan(0);
      }
    }
    const grievance = JSON.stringify(LEGAL_DOCS.grievance);
    expect(grievance).toContain('24 hours');
    expect(grievance).toContain('15 days');
  });
});

describe('ID checks', () => {
  it('explain every rejection reason the server can send, and ask for masked Aadhaar', () => {
    expect(Object.keys(REJECT_REASONS)).toEqual(['UNREADABLE', 'NAME_MISMATCH', 'NOT_ACCEPTED', 'EXPIRED', 'AADHAAR_NOT_MASKED', 'OTHER']);
    expect(Object.keys(DOCUMENTS)).toEqual(['DRIVING_LICENCE', 'AADHAAR', 'PAN', 'PASSPORT', 'VOTER_ID']);
    expect(DOCUMENTS.AADHAAR).toContain('masked');
  });
});
