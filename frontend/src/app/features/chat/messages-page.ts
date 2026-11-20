import { DatePipe } from '@angular/common';
import { afterNextRender, ChangeDetectionStrategy, Component, computed, DestroyRef, effect, ElementRef, inject, Injector, input, signal, untracked, viewChild } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { ChatApi, ChatMessage, Conversation, InboxItem } from '../../core/chat-api';
import { problemOf } from '../../core/errors';
import { bookingStatus } from '../../core/rides-format';
import { BookingStatus } from '../../core/rides.models';
import { ModerationApi, ReportTarget } from '../../core/moderation-api';
import { ToastService } from '../../core/toast.service';
import { TripsService } from '../../core/trips.service';
import { Avatar } from '../../shared/avatar';
import { Icon } from '../../shared/icon';
import { ReportDialog } from '../../shared/report-dialog';

type Row = { kind: 'day'; label: string; key: string } | { kind: 'msg'; m: ChatMessage; key: string; seen: boolean };

const IST = 5.5 * 3600_000;
const istDay = (iso: string) => new Date(Date.parse(iso) + IST).toISOString().slice(0, 10);

/** Messages: the inbox and one chat. On laptops both side by side; on phones one at a time. */
@Component({
  selector: 'app-messages-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [DatePipe, RouterLink, Icon, Avatar, ReportDialog],
  host: { '(document:click)': 'menuOpen.set(false)' },
  templateUrl: './messages-page.html',
})
export class MessagesPage {
  private readonly api = inject(ChatApi);
  private readonly router = inject(Router);
  private readonly badges = inject(TripsService);
  private readonly injector = inject(Injector);
  private readonly moderation = inject(ModerationApi);
  private readonly toast = inject(ToastService);

  /** The open chat, from /messages/:id. */
  readonly id = input<string>();

  protected readonly inbox = signal<InboxItem[] | null>(null);
  protected readonly convo = signal<Conversation | null>(null);
  protected readonly messages = signal<ChatMessage[]>([]);
  protected readonly draft = signal('');
  protected readonly sending = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly convoError = signal<string | null>(null);
  protected readonly menuOpen = signal(false);
  protected readonly confirmBlock = signal(false);
  protected readonly reporting = signal<{ type: ReportTarget; id: string; name: string | null } | null>(null);

  private readonly scroller = viewChild<ElementRef<HTMLElement>>('scroller');

  protected readonly rows = computed<Row[]>(() => {
    const list = this.messages();
    const seenAt = this.convo()?.seenAt;
    const lastMine = [...list].reverse().find((m) => m.mine);
    const today = istDay(new Date().toISOString());
    const yesterday = istDay(new Date(Date.now() - 86_400_000).toISOString());
    const rows: Row[] = [];
    let day = '';
    for (const m of list) {
      const d = istDay(m.createdAt);
      if (d !== day) {
        day = d;
        const label = d === today ? 'Today' : d === yesterday ? 'Yesterday' : '';
        rows.push({ kind: 'day', label: label || d, key: 'd' + d });
      }
      rows.push({ kind: 'msg', m, key: m.id, seen: !!seenAt && m === lastMine && seenAt >= m.createdAt });
    }
    return rows;
  });

  protected readonly otherRole = computed(() => {
    const c = this.convo();
    if (!c) return '';
    if (c.otherIsDriver) return 'Driver';
    const b = c.passengerBooking as BookingStatus | null;
    return b ? `Passenger · ${bookingStatus(b).text.toLowerCase()}` : 'Asking about your ride';
  });

  private requestNo = 0;

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => {
        this.convo.set(null);
        this.messages.set([]);
        this.convoError.set(null);
        this.draft.set('');
        if (id) void this.loadConversation(id, true);
      });
    });
    void this.loadInbox();
    const fast = setInterval(() => {
      if (document.visibilityState === 'visible' && this.convo()) void this.poll();
    }, 4000);
    const slow = setInterval(() => {
      if (document.visibilityState === 'visible') void this.loadInbox();
    }, 15_000);
    inject(DestroyRef).onDestroy(() => {
      clearInterval(fast);
      clearInterval(slow);
    });
  }

  protected isDayLabel(label: string): boolean {
    return label === 'Today' || label === 'Yesterday';
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (event.key === 'Enter' && !event.shiftKey && !event.isComposing) {
      event.preventDefault();
      void this.send();
    }
  }

  protected async send(): Promise<void> {
    const c = this.convo();
    const text = this.draft().trim();
    if (!c || !text || this.sending()) return;
    this.sending.set(true);
    this.error.set(null);
    try {
      const m = await this.api.send(c.id, text);
      this.messages.update((list) => (list.some((x) => x.id === m.id) ? list : [...list, m]));
      this.draft.set('');
      this.scrollToEnd();
      void this.loadInbox();
    } catch (e) {
      this.error.set(problemOf(e).detail);
    } finally {
      this.sending.set(false);
    }
  }

  protected async blockOther(): Promise<void> {
    const c = this.convo();
    if (!c?.other.id) return;
    try {
      await this.moderation.block(c.other.id);
      this.toast.success(`${c.other.displayName} is blocked. You can't message each other now.`);
      this.confirmBlock.set(false);
      await this.loadConversation(c.id, false);
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    }
  }

  protected back(): void {
    void this.router.navigate(['/messages']);
  }

  private async loadInbox(): Promise<void> {
    try {
      this.inbox.set(await this.api.inbox());
    } catch (e) {
      if (!this.inbox()) this.error.set(problemOf(e).detail);
    }
  }

  private async loadConversation(id: string, first: boolean): Promise<void> {
    const no = ++this.requestNo;
    try {
      const c = await this.api.conversation(id);
      if (no !== this.requestNo) return;
      this.convo.set(c);
      this.messages.set(c.messages);
      if (first) this.scrollToEnd();
      void this.badges.refreshChats();
      void this.loadInbox();
    } catch (e) {
      if (no === this.requestNo) this.convoError.set(problemOf(e).detail);
    }
  }

  /** Fetches only messages newer than the last one we have. */
  private async poll(): Promise<void> {
    const c = this.convo();
    if (!c) return;
    const last = this.messages().at(-1)?.createdAt;
    try {
      const update = await this.api.conversation(c.id, last);
      if (this.convo()?.id !== c.id) return;
      this.convo.set({ ...update, messages: [] });
      if (update.messages.length) {
        const known = new Set(this.messages().map((m) => m.id));
        const fresh = update.messages.filter((m) => !known.has(m.id));
        if (fresh.length) {
          const el = this.scroller()?.nativeElement;
          const atEnd = !el || el.scrollHeight - el.scrollTop - el.clientHeight < 80;
          this.messages.update((list) => [...list, ...fresh]);
          if (atEnd) this.scrollToEnd();
          void this.badges.refreshChats();
        }
      }
    } catch {
      // Try again on the next tick.
    }
  }

  private scrollToEnd(): void {
    afterNextRender(() => {
      const el = this.scroller()?.nativeElement;
      if (el) el.scrollTop = el.scrollHeight;
    }, { injector: this.injector });
  }
}
