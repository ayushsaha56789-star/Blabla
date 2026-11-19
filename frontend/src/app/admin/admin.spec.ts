import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router, UrlTree } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { featureGuard, FeaturesService } from '../core/features.service';
import { adminInterceptor } from './admin.interceptor';
import { timeAgo } from './admin-ui';
import { ConfirmCodeService } from './confirm-code.service';
import { ConsoleSessionService } from './console-session.service';

describe('admin interceptor', () => {
  let http: HttpClient;
  let backend: HttpTestingController;
  let session: ConsoleSessionService;
  let confirm: ConfirmCodeService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(withInterceptors([adminInterceptor])), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpClient);
    backend = TestBed.inject(HttpTestingController);
    session = TestBed.inject(ConsoleSessionService);
    confirm = TestBed.inject(ConfirmCodeService);
    // Pretend a console session is open.
    (session as unknown as { token: { set(v: string): void } }).token.set('console-token');
  });

  afterEach(() => backend.verify());

  it('adds the console token to console calls only', () => {
    void firstValueFrom(http.get('/api/admin/me'));
    expect(backend.expectOne('/api/admin/me').request.headers.get('Authorization')).toBe('Bearer console-token');
    void firstValueFrom(http.get('/api/features'));
    expect(backend.expectOne('/api/features').request.headers.has('Authorization')).toBe(false);
  });

  it('asks for a 2FA code when the server wants one, then repeats the request with it', async () => {
    const result = firstValueFrom(http.post('/api/admin/staff', { phone: '9876543210', role: 'ADMIN' }));
    backend.expectOne('/api/admin/staff').flush(
      { code: 'confirmation_required', detail: 'Please confirm' },
      { status: 403, statusText: 'Forbidden' },
    );
    await Promise.resolve();
    expect(confirm.open()).toBe(true);
    confirm.submit('123456');
    await Promise.resolve();
    const retry = backend.expectOne('/api/admin/staff');
    expect(retry.request.headers.get('X-Confirm-Code')).toBe('123456');
    retry.flush({ ok: true });
    await expect(result).resolves.toEqual({ ok: true });
  });

  it('ends the console session when the server says it is over', async () => {
    const result = firstValueFrom(http.get('/api/admin/me'));
    backend.expectOne('/api/admin/me').flush(null, { status: 401, statusText: 'Unauthorized' });
    await expect(result).rejects.toBeTruthy();
    expect(session.consoleToken()).toBeNull();
    expect(session.timedOut()).toBe(true);
  });
});

describe('featureGuard', () => {
  it('lets members in only while the switch is on', () => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    const features = TestBed.inject(FeaturesService);
    const guard = () => TestBed.runInInjectionContext(() => featureGuard('games')({} as never, {} as never));
    expect(guard()).toBeInstanceOf(UrlTree);
    (features as unknown as { enabled: { set(v: Set<string>): void } }).enabled.set(new Set(['games']));
    expect(guard()).toBe(true);
    expect(TestBed.inject(Router)).toBeTruthy();
  });
});

describe('timeAgo', () => {
  const now = Date.parse('2026-11-19T12:00:00Z');
  it('speaks like a person', () => {
    expect(timeAgo('2026-11-19T11:59:50Z', now)).toBe('just now');
    expect(timeAgo('2026-11-19T11:55:00Z', now)).toBe('5 min ago');
    expect(timeAgo('2026-11-19T09:00:00Z', now)).toBe('3 h ago');
    expect(timeAgo('2026-11-18T10:00:00Z', now)).toBe('yesterday');
    expect(timeAgo(null, now)).toBe('never');
  });
});
