import { Routes } from '@angular/router';
import { permissionGuard } from './console-session.service';
import { AdminShell } from './shell/admin-shell';

/** The staff console. The shell shows the 2FA gate until a console session is open. */
export const ADMIN_ROUTES: Routes = [
  {
    path: '',
    component: AdminShell,
    children: [
      {
        path: '',
        title: 'Dashboard · Staff console',
        canActivate: [permissionGuard('users.view')],
        loadComponent: () => import('./pages/dashboard').then((m) => m.DashboardPage),
      },
      {
        path: 'members',
        title: 'Members · Staff console',
        canActivate: [permissionGuard('users.view')],
        loadComponent: () => import('./pages/members').then((m) => m.MembersPage),
      },
      {
        path: 'members/:id',
        title: 'Member · Staff console',
        canActivate: [permissionGuard('users.view')],
        loadComponent: () => import('./pages/member-detail').then((m) => m.MemberDetailPage),
      },
      {
        path: 'rides',
        title: 'Rides · Staff console',
        canActivate: [permissionGuard('users.view')],
        loadComponent: () => import('./pages/rides').then((m) => m.RidesPage),
      },
      {
        path: 'rides/:id',
        title: 'Ride · Staff console',
        canActivate: [permissionGuard('users.view')],
        loadComponent: () => import('./pages/ride-detail').then((m) => m.RideDetailPage),
      },
      {
        path: 'safety',
        title: 'SOS alerts · Staff console',
        canActivate: [permissionGuard('support.tickets')],
        loadComponent: () => import('./pages/safety').then((m) => m.SafetyPage),
      },
      {
        path: 'support',
        title: 'Help requests · Staff console',
        canActivate: [permissionGuard('support.tickets')],
        loadComponent: () => import('./pages/support').then((m) => m.SupportPage),
      },
      {
        path: 'support/:id',
        title: 'Help request · Staff console',
        canActivate: [permissionGuard('support.tickets')],
        loadComponent: () => import('./pages/support-ticket').then((m) => m.SupportTicketPage),
      },
      {
        path: 'verifications',
        title: 'ID checks · Staff console',
        canActivate: [permissionGuard('verification.review')],
        loadComponent: () => import('./pages/verifications').then((m) => m.VerificationsPage),
      },
      {
        path: 'reports',
        title: 'Reports · Staff console',
        canActivate: [permissionGuard('content.moderate')],
        loadComponent: () => import('./pages/reports').then((m) => m.ReportsPage),
      },
      {
        path: 'features',
        title: 'Feature switches · Staff console',
        canActivate: [permissionGuard('features.manage')],
        loadComponent: () => import('./pages/features').then((m) => m.FeaturesPage),
      },
      {
        path: 'staff',
        title: 'Staff · Staff console',
        canActivate: [permissionGuard('staff.manage')],
        loadComponent: () => import('./pages/staff').then((m) => m.StaffPage),
      },
      {
        path: 'audit',
        title: 'Audit log · Staff console',
        canActivate: [permissionGuard('audit.view')],
        loadComponent: () => import('./pages/audit').then((m) => m.AuditPage),
      },
      {
        path: 'security',
        title: 'My security · Staff console',
        loadComponent: () => import('./pages/security').then((m) => m.SecurityPage),
      },
    ],
  },
];
