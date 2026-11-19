import { ChangeDetectionStrategy, Component, effect, inject, input, output, signal, untracked } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { problemOf } from '../core/errors';
import { Vehicle } from '../core/rides.models';
import { RidesApi } from '../core/rides-api';
import { Icon } from './icon';

/** Popular makes in India, offered as suggestions (any make can be typed). */
const MAKES = ['Maruti Suzuki', 'Hyundai', 'Tata', 'Mahindra', 'Kia', 'Toyota', 'Honda', 'MG', 'Renault', 'Skoda',
  'Volkswagen', 'Nissan', 'Citroën', 'Jeep', 'BYD', 'BMW', 'Mercedes-Benz', 'Audi'];

const COLOURS = ['White', 'Silver', 'Grey', 'Black', 'Red', 'Blue', 'Brown'];

/** Add or edit a car. Emits the saved car. */
@Component({
  selector: 'app-vehicle-form',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, Icon],
  template: `
    <form [formGroup]="form" (submit)="save($event)" novalidate class="space-y-4">
      <div class="grid gap-4 sm:grid-cols-2">
        <div>
          <label [for]="uid + 'make'" class="label">Make</label>
          <input [id]="uid + 'make'" class="input" formControlName="make" [attr.list]="uid + 'makes'" placeholder="e.g. Maruti Suzuki" autocomplete="off" />
          <datalist [id]="uid + 'makes'">@for (m of makes; track m) { <option [value]="m"></option> }</datalist>
          @if (error('make'); as e) { <p class="field-error">{{ e }}</p> }
        </div>
        <div>
          <label [for]="uid + 'model'" class="label">Model</label>
          <input [id]="uid + 'model'" class="input" formControlName="model" placeholder="e.g. Swift" autocomplete="off" />
          @if (error('model'); as e) { <p class="field-error">{{ e }}</p> }
        </div>
      </div>
      <div>
        <span class="label" [id]="uid + 'colour'">Colour</span>
        <div class="flex flex-wrap gap-2" role="radiogroup" [attr.aria-labelledby]="uid + 'colour'">
          @for (c of colours; track c) {
            <button type="button" role="radio" class="chip py-1.5" [class.chip-on]="form.controls.color.value === c" [attr.aria-checked]="form.controls.color.value === c"
                    (click)="form.controls.color.setValue(form.controls.color.value === c ? '' : c); form.markAsDirty()">{{ c }}</button>
          }
          <input class="input w-36 rounded-full px-4 py-1.5 text-sm" formControlName="color" placeholder="Other colour" aria-label="Other colour" />
        </div>
        @if (error('color'); as e) { <p class="field-error">{{ e }}</p> }
      </div>
      <div class="grid gap-4 sm:grid-cols-2">
        <div>
          <label [for]="uid + 'plate'" class="label">Registration number</label>
          <input [id]="uid + 'plate'" class="input font-mono uppercase tracking-wider" formControlName="plate" placeholder="MH 12 AB 1234" autocomplete="off" />
          @if (error('plate'); as e) { <p class="field-error">{{ e }}</p> } @else { <p class="hint">Only passengers with a confirmed seat see it, so they can spot your car.</p> }
        </div>
        <div>
          <span class="label" [id]="uid + 'seats'">Seats for passengers</span>
          <div class="flex items-center gap-4 rounded-2xl border border-slate-200 px-4 py-2 dark:border-slate-700" role="group" [attr.aria-labelledby]="uid + 'seats'">
            <button type="button" class="stepper-btn" (click)="seats(-1)" [disabled]="form.controls.seats.value <= 1" aria-label="Fewer seats"><app-icon name="minus" [size]="16" /></button>
            <span class="w-6 text-center text-lg font-bold" aria-live="polite">{{ form.controls.seats.value }}</span>
            <button type="button" class="stepper-btn" (click)="seats(1)" [disabled]="form.controls.seats.value >= 8" aria-label="More seats"><app-icon name="plus" [size]="16" /></button>
          </div>
          <p class="hint">Not counting you. Most cars take 3 or 4.</p>
        </div>
      </div>
      @if (formError()) { <p class="field-error" role="alert">{{ formError() }}</p> }
      <div class="flex flex-wrap justify-end gap-2">
        @if (cancellable()) { <button type="button" class="btn btn-ghost" (click)="cancelled.emit()" [disabled]="saving()">Cancel</button> }
        <button type="submit" class="btn btn-primary" [disabled]="saving()">
          @if (saving()) { Saving… } @else { <app-icon name="check" [size]="18" /> {{ vehicle() ? 'Save car' : 'Add car' }} }
        </button>
      </div>
    </form>
  `,
})
export class VehicleForm {
  private readonly api = inject(RidesApi);

  readonly vehicle = input<Vehicle | null>(null);
  readonly cancellable = input(true);
  readonly saved = output<Vehicle>();
  readonly cancelled = output<void>();

  protected readonly uid = `car-${Math.random().toString(36).slice(2, 8)}-`;
  protected readonly makes = MAKES;
  protected readonly colours = COLOURS;
  protected readonly saving = signal(false);
  protected readonly formError = signal<string | null>(null);
  protected readonly serverErrors = signal<Record<string, string>>({});

  protected readonly form = inject(FormBuilder).nonNullable.group({
    make: ['', [Validators.required, Validators.maxLength(40)]],
    model: ['', [Validators.required, Validators.maxLength(40)]],
    color: ['', [Validators.maxLength(30)]],
    seats: [4],
    plate: ['', [Validators.required, Validators.maxLength(20)]],
  });

  constructor() {
    effect(() => {
      const v = this.vehicle();
      untracked(() => {
        if (v) this.form.reset({ make: v.make, model: v.model, color: v.color ?? '', seats: v.seats, plate: v.plate });
      });
    });
  }

  protected seats(delta: number): void {
    const c = this.form.controls.seats;
    c.setValue(Math.min(8, Math.max(1, c.value + delta)));
    c.markAsDirty();
  }

  protected error(field: 'make' | 'model' | 'color' | 'plate'): string | null {
    const server = this.serverErrors()[field];
    if (server) return server;
    const c = this.form.controls[field];
    if (!c.touched || c.valid) return null;
    if (c.hasError('required')) {
      return { make: 'Please enter the make, e.g. Maruti Suzuki', model: 'Please enter the model, e.g. Swift', color: '', plate: 'Please enter the registration number' }[field];
    }
    return 'That looks too long';
  }

  protected async save(event: Event): Promise<void> {
    event.preventDefault();
    this.form.markAllAsTouched();
    this.serverErrors.set({});
    this.formError.set(null);
    if (this.form.invalid) return;
    this.saving.set(true);
    const v = this.form.getRawValue();
    const body = { make: v.make.trim(), model: v.model.trim(), color: v.color.trim() || null, seats: v.seats, plate: v.plate.trim() };
    try {
      const existing = this.vehicle();
      const car = existing ? await this.api.updateVehicle(existing.id, body) : await this.api.addVehicle(body);
      this.form.markAsPristine();
      this.saved.emit(car);
    } catch (e) {
      const p = problemOf(e);
      if (p.errors) this.serverErrors.set(p.errors);
      else if (p.code === 'bad_plate') this.serverErrors.set({ plate: p.detail });
      else this.formError.set(p.detail);
    } finally {
      this.saving.set(false);
    }
  }
}
