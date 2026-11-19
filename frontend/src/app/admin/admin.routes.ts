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
