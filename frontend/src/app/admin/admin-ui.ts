import { IconName } from '../shared/icon';

/** "just now", "5 min ago", "3 h ago", "2 days ago", then a date. */
export function timeAgo(iso: string | null | undefined, now = Date.now()): string {
  if (!iso) return 'never';
  const seconds = Math.round((now - new Date(iso).getTime()) / 1000);
  if (seconds < 45) return 'just now';
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) return `${minutes} min ago`;
  const hours = Math.round(minutes / 60);
  if (hours < 24) return `${hours} h ago`;
  const days = Math.round(hours / 24);
  if (days < 7) return days === 1 ? 'yesterday' : `${days} days ago`;
  return new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' });
}

export function fullTime(iso: string | null | undefined): string {
  return iso ? new Date(iso).toLocaleString('en-IN', { dateStyle: 'medium', timeStyle: 'short' }) : '';
}

/** Icon and colour for an audit entry, by the kind of action. */
export function auditStyle(action: string): { icon: IconName; tint: string } {
  if (action.includes('failed') || action === 'member.suspended') {
    return { icon: 'shieldAlert', tint: 'bg-rose-50 text-rose-600 dark:bg-rose-500/15 dark:text-rose-300' };
  }
  if (action.startsWith('feature.')) {
    return { icon: 'toggles', tint: 'bg-brand-50 text-brand-700 dark:bg-brand-500/15 dark:text-brand-300' };
  }
  if (action.startsWith('member.')) {
    return { icon: 'user', tint: 'bg-sky-50 text-sky-700 dark:bg-sky-500/15 dark:text-sky-300' };
  }
  if (action.startsWith('ride.')) {
    return { icon: 'car', tint: 'bg-rose-50 text-rose-600 dark:bg-rose-500/15 dark:text-rose-300' };
  }
  if (action.includes('2fa') || action.includes('recovery') || action.includes('console')) {
    return { icon: 'key', tint: 'bg-violet-50 text-violet-700 dark:bg-violet-500/15 dark:text-violet-300' };
  }
  return { icon: 'users', tint: 'bg-accent-100 text-accent-600 dark:bg-accent-600/15 dark:text-accent-300' };
}

/** Lets people save one-time codes as a text file. */
export function downloadText(fileName: string, text: string): void {
  const url = URL.createObjectURL(new Blob([text], { type: 'text/plain' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = fileName;
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
