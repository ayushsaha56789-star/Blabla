import { ChangeDetectionStrategy, Component, inject, input, OnInit, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { problemOf } from '../../core/errors';
import { SocialApi } from '../../core/social-api';
import { ToastService } from '../../core/toast.service';

/** An invite link (/invite/CODE): after logging in, the friend's code is added to the new member's account. */
@Component({
  selector: 'app-invite-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink],
  template: `
    <div class="container-page max-w-md py-16 text-center">
      @if (problem(); as p) {
        <p class="text-4xl" aria-hidden="true">💌</p>
        <h1 class="mt-3 text-2xl font-extrabold">About that invite…</h1>
        <p class="mt-2 text-slate-600 dark:text-slate-400">{{ p }}</p>
        <a routerLink="/rewards" class="btn btn-primary mt-6">See my journey</a>
      } @else {
        <div class="skeleton mx-auto h-24 w-64"></div>
      }
    </div>
  `,
})
export class InvitePage implements OnInit {
  private readonly api = inject(SocialApi);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);

  readonly code = input.required<string>();
  protected readonly problem = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    try {
      await this.api.claim(this.code());
      this.toast.success('Welcome! Take your first shared ride and you and your friend both get credits.');
      void this.router.navigateByUrl('/rewards');
    } catch (e) {
      this.problem.set(problemOf(e).detail);
    }
  }
}
