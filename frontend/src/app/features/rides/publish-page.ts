import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../core/auth.service';
import { Icon } from '../../shared/icon';

/** Offer a ride. The full flow (route, stops, seats, price) arrives in phase 2. */
@Component({
  selector: 'app-publish-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterLink, Icon],
  template: `
    <div class="container-page py-10 sm:py-16">
      <section class="card mx-auto max-w-2xl p-8 text-center motion-safe:animate-fade-up sm:p-12">
        <span class="mx-auto flex h-16 w-16 items-center justify-center rounded-3xl bg-accent-100 text-accent-600 dark:bg-accent-600/15 dark:text-accent-300">
          <app-icon name="car" [size]="30" />
        </span>
        <h1 class="mt-5 text-3xl font-extrabold tracking-tight">Offer a ride</h1>
        <p class="mx-auto mt-3 max-w-md text-slate-600 dark:text-slate-400">
          Thanks for getting ready to share your car, {{ auth.user()?.firstName }}! Publishing rides — route, stops,
          seats and price per seat — arrives in the next update.
        </p>
        <ul class="mx-auto mt-8 max-w-sm space-y-3 text-left text-sm">
          <li class="flex items-center gap-3"><app-icon name="checkCircle" class="text-brand-600" /> Your profile is ready</li>
          <li class="flex items-center gap-3"><app-icon name="camera" class="text-slate-400" /> Tip: add a friendly photo — riders book faces they can see</li>
          <li class="flex items-center gap-3"><app-icon name="shield" class="text-slate-400" /> ID verification comes with the trust & safety update</li>
        </ul>
        <a routerLink="/profile" class="btn btn-secondary mt-8">Polish my profile</a>
      </section>
    </div>
  `,
})
export class PublishPage {
  protected readonly auth = inject(AuthService);
}
