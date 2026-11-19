import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding, withInMemoryScrolling, withViewTransitions } from '@angular/router';
import { adminInterceptor } from './admin/admin.interceptor';
import { routes } from './app.routes';
import { authInterceptor, statusInterceptor } from './core/auth.interceptor';
import { AuthService } from './core/auth.service';
import { FeaturesService } from './core/features.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' }),
      withViewTransitions({
        skipInitialTransition: true,
        // Respect the device's "reduce motion" setting: no animated page changes.
        onViewTransitionCreated: ({ transition }) => {
          // (Development builds log an AbortError for each skipped transition; production builds don't.)
          if (window.matchMedia?.('(prefers-reduced-motion: reduce)').matches) transition.skipTransition();
        },
      }),
    ),
    provideHttpClient(withFetch(), withInterceptors([statusInterceptor, authInterceptor, adminInterceptor])),
    // Restore the login session (if any) and load the feature switches before the first page renders.
    provideAppInitializer(async () => {
      const auth = inject(AuthService);
      const features = inject(FeaturesService);
      await auth.restoreSession();
      await features.load();
      features.start();
    }),
  ],
};
