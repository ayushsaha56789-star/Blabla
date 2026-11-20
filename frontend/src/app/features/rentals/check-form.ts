import { ChangeDetectionStrategy, Component, computed, input, OnDestroy, output, signal } from '@angular/core';
import { CheckForm, Fuel, FUEL_LABELS } from '../../core/rentals-api';
import { rupees } from '../../core/rides-format';
import { shrinkPhoto } from '../../core/verification-api';
import { Icon } from '../../shared/icon';

/** Recording the car at pickup or return: odometer, fuel, notes and photos (and charges at return). */
@Component({
  selector: 'app-check-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon],
  host: { class: 'block' },
  template: `
    <form class="space-y-3 rounded-2xl bg-slate-50 p-4 dark:bg-slate-800/50" (submit)="send($event)">
      <p class="font-semibold">{{ title() }}</p>
      <div class="grid grid-cols-2 gap-2">
        <label class="text-sm"><span class="label text-xs">Odometer (km)</span>
          <input class="input" type="number" inputmode="numeric" min="0" [value]="odometer()" (input)="odometer.set($any($event.target).value)" /></label>
        <label class="text-sm"><span class="label text-xs">Fuel</span>
          <select class="input" [value]="fuel()" (change)="fuel.set($any($event.target).value)">
            @for (f of fuels; track f) { <option [value]="f">{{ fuelLabels[f] }}</option> }
          </select></label>
      </div>
      <label class="block text-sm"><span class="label text-xs">Notes (scratches, dents, anything worth recording)</span>
        <textarea class="input min-h-16 text-sm" maxlength="500" [value]="notes()" (input)="notes.set($any($event.target).value)"></textarea></label>
      @if (returning()) {
        @if (extraKm() > 0) {
          <p class="rounded-xl bg-white p-3 text-sm dark:bg-slate-900">{{ kmDriven() }} km driven, {{ kmIncluded() }} included: <b>{{ money(extraKm()) }}</b> for extra km is added automatically.</p>
        }
        <div class="grid gap-2 sm:grid-cols-[8rem_1fr]">
          <label class="text-sm"><span class="label text-xs">Other charges (₹)</span>
            <input class="input" type="number" min="0" [value]="charges()" (input)="charges.set($any($event.target).value)" /></label>
          <label class="text-sm"><span class="label text-xs">Why (fuel, damage), with photos</span>
            <input class="input" maxlength="300" [value]="chargesNote()" (input)="chargesNote.set($any($event.target).value)" /></label>
        </div>
        <p class="text-xs text-slate-500 dark:text-slate-400">Charges come from the deposit (never more than it). The renter can accept them or ask SaathChalo to check.</p>
      }
      <div>
        <span class="label text-xs">Photos (all four sides and the dashboard is best, up to 6)</span>
        <div class="flex flex-wrap gap-2">
          @for (p of previews(); track p) {
            <span class="relative h-20 w-24 overflow-hidden rounded-xl"><img [src]="p" alt="" class="h-full w-full object-cover" />
              <button type="button" class="absolute top-1 right-1 rounded-full bg-black/60 p-0.5 text-white" (click)="removePhoto($index)" aria-label="Remove photo"><app-icon name="close" [size]="14" /></button></span>
          }
          @if (photos().length < 6) {
            <label class="flex h-20 w-24 cursor-pointer flex-col items-center justify-center rounded-xl border-2 border-dashed border-slate-300 text-xs text-slate-500 dark:border-slate-600">
              <app-icon name="camera" [size]="20" /> Add
              <input type="file" accept="image/jpeg,image/png" capture="environment" multiple class="sr-only" (change)="pick($event)" /></label>
          }
        </div>
      </div>
      @if (error()) { <p class="field-error" role="alert">{{ error() }}</p> }
      <div class="flex justify-end gap-2">
        <button type="button" class="btn btn-ghost btn-sm" (click)="cancelled.emit()">Cancel</button>
        <button type="submit" class="btn btn-primary btn-sm" [disabled]="busy()">{{ busy() ? 'Saving…' : 'Save' }}</button>
      </div>
    </form>
  `,
})
export class CheckFormComponent implements OnDestroy {
  readonly title = input('The car right now');
  readonly returning = input(false);
  readonly startOdometer = input<number | null>(null);
  readonly kmIncluded = input(0);
  readonly extraKmPrice = input(0);
  readonly busy = input(false);
  readonly photoRequired = input(true);
  readonly saved = output<CheckForm>();
  readonly cancelled = output<void>();

  protected readonly fuels = Object.keys(FUEL_LABELS) as Fuel[];
  protected readonly fuelLabels = FUEL_LABELS;
  protected readonly money = rupees;
  protected readonly odometer = signal('');
  protected readonly fuel = signal<Fuel>('FULL');
  protected readonly notes = signal('');
  protected readonly charges = signal('');
  protected readonly chargesNote = signal('');
  protected readonly photos = signal<Blob[]>([]);
  protected readonly previews = signal<string[]>([]);
  protected readonly error = signal<string | null>(null);

  protected readonly kmDriven = computed(() => {
    const start = this.startOdometer();
    const end = Number(this.odometer());
    return start === null || !end ? 0 : Math.max(0, end - start);
  });
  protected readonly extraKm = computed(() => Math.max(0, this.kmDriven() - this.kmIncluded()) * this.extraKmPrice());

  ngOnDestroy(): void {
    this.previews().forEach((u) => URL.revokeObjectURL(u));
  }

  protected async pick(event: Event): Promise<void> {
    const input = event.target as HTMLInputElement;
    const files = Array.from(input.files ?? []).slice(0, 6 - this.photos().length);
    input.value = '';
    for (const f of files) {
      const small = await shrinkPhoto(f);
      this.photos.update((p) => [...p, small]);
      this.previews.update((p) => [...p, URL.createObjectURL(small)]);
    }
  }

  protected removePhoto(i: number): void {
    URL.revokeObjectURL(this.previews()[i]);
    this.photos.update((p) => p.filter((_, j) => j !== i));
    this.previews.update((p) => p.filter((_, j) => j !== i));
  }

  protected send(event: Event): void {
    event.preventDefault();
    const odometer = Number(this.odometer());
    if (!Number.isInteger(odometer) || odometer < 0) {
      this.error.set('Enter the odometer reading in km.');
      return;
    }
    if (this.photoRequired() && !this.photos().length) {
      this.error.set('Add at least one photo of the car.');
      return;
    }
    const charges = Number(this.charges() || 0);
    if (charges > 0 && !this.chargesNote().trim()) {
      this.error.set('Say why you are charging extra.');
      return;
    }
    this.error.set(null);
    this.saved.emit({ odometer, fuel: this.fuel(), notes: this.notes(), photos: this.photos(), otherCharges: charges, chargesNote: this.chargesNote() });
  }
}
