import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { IconName } from '../shared/icon';

/** The member's notification inbox (backend: notifications/NotificationController.java). */
export type NoticeTopic = 'TRIPS' | 'REMINDERS' | 'ACCOUNT' | 'REWARDS';

export interface NoticeView {
  id: number;
  topic: NoticeTopic;
  icon: string;
  title: string;
  body: string | null;
  link: string | null;
  createdAt: string;
  read: boolean;
}

export interface Inbox {
  items: NoticeView[];
  more: boolean;
  unread: number;
}

export interface TopicChoice {
  topic: NoticeTopic;
  label: string;
  canMute: boolean;
  muted: boolean;
}

@Injectable({ providedIn: 'root' })
export class NotificationsApi {
  private readonly http = inject(HttpClient);

  inbox(before?: number): Promise<Inbox> {
    const params = before ? new HttpParams().set('before', before) : undefined;
    return firstValueFrom(this.http.get<Inbox>('/api/notifications', { params }));
  }

  unread(): Promise<{ count: number }> {
    return firstValueFrom(this.http.get<{ count: number }>('/api/notifications/unread'));
  }

  markRead(ids: number[] | 'all'): Promise<void> {
    const body = ids === 'all' ? { all: true } : { ids };
    return firstValueFrom(this.http.post<void>('/api/notifications/read', body));
  }

  settings(): Promise<{ topics: TopicChoice[] }> {
    return firstValueFrom(this.http.get<{ topics: TopicChoice[] }>('/api/notifications/settings'));
  }

  saveSettings(muted: NoticeTopic[]): Promise<{ topics: TopicChoice[] }> {
    return firstValueFrom(this.http.put<{ topics: TopicChoice[] }>('/api/notifications/settings', { muted }));
  }
}

const KNOWN_ICONS = new Set<string>(['bell', 'car', 'check', 'checkCircle', 'close', 'star', 'chat', 'idCard', 'megaphone', 'trophy',
  'steering', 'user', 'clock', 'info', 'rupee', 'shield', 'calendar']);

/** The server names an icon; anything unexpected falls back to the bell. */
export function noticeIcon(name: string): IconName {
  return (KNOWN_ICONS.has(name) ? name : 'bell') as IconName;
}

/** "Today", "Yesterday" or "Mon 16 Nov", in the browser's time zone. */
export function dayLabel(iso: string, now = new Date()): string {
  const d = new Date(iso);
  const day = (x: Date) => new Date(x.getFullYear(), x.getMonth(), x.getDate()).getTime();
  const diff = Math.round((day(now) - day(d)) / 86_400_000);
  if (diff === 0) return 'Today';
  if (diff === 1) return 'Yesterday';
  return d.toLocaleDateString('en-IN', { weekday: 'short', day: 'numeric', month: 'short' });
}
