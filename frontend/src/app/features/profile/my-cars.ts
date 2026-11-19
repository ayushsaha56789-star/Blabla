import { ChangeDetectionStrategy, Component, inject, OnInit, signal } from '@angular/core';
import { problemOf } from '../../core/errors';
import { Vehicle } from '../../core/rides.models';
import { RidesApi } from '../../core/rides-api';
import { ToastService } from '../../core/toast.service';
import { Icon } from '../../shared/icon';
import { VehicleForm } from '../../shared/vehicle-form';

/** "My cars" on the profile page: add, edit and remove the cars a member offers rides in. */
@Component({
  selector: 'app-my-cars',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, VehicleForm],
  host: { class: 'block' },
  template: `
    <section class="card p-6 sm:p-8" aria-labelledby="cars-heading">
      <div class="flex flex-wrap items-center justify-between gap-3">
        <div>
          <h2 id="cars-heading" class="text-lg font-bold">My cars</h2>
          <p class="text-sm text-slate-500 dark:text-slate-400">The cars you offer rides in.</p>
        </div>
        @if (cars()?.length && editing() === null) {
          <button type="button" class="btn btn-secondary btn-sm" (click)="editing.set('new')"><app-icon name="plus" [size]="16" /> Add a car</button>
        }
      </div>

      @if (cars() === null) {
        <div class="skeleton mt-5 h-16"></div>
      } @else {
        @if (cars()!.length) {
          <ul class="mt-5 space-y-3">
            @for (c of cars(); track c.id) {
              <li class="rounded-2xl border border-slate-200 p-4 dark:border-slate-700">
                @if (editing() === c.id) {
                  <app-vehicle-form [vehicle]="c" (saved)="onSaved($event)" (cancelled)="editing.set(null)" />
                } @else {
                  <div class="flex flex-wrap items-center gap-3">
                    <span class="flex h-11 w-11 items-center justify-center rounded-2xl bg-slate-100 text-slate-600 dark:bg-slate-800 dark:text-slate-300"><app-icon name="car" [size]="22" /></span>
                    <span class="min-w-0 flex-1">
                      <span class="block font-semibold">{{ c.make }} {{ c.model }}</span>
                      <span class="block text-sm text-slate-500 dark:text-slate-400">{{ c.color ? c.color + ' · ' : '' }}{{ c.seats }} {{ c.seats === 1 ? 'seat' : 'seats' }} for passengers</span>
                    </span>
                    <span class="rounded-lg border-2 border-slate-900 bg-white px-2 py-0.5 font-mono text-xs font-bold tracking-wider text-slate-900 dark:border-slate-200">{{ c.plate }}</span>
                    @if (confirmRemove() === c.id) {
                      <span class="flex w-full items-center justify-end gap-2 sm:w-auto">
                        <span class="text-sm">Remove this car?</span>
                        <button type="button" class="btn btn-ghost btn-sm" (click)="confirmRemove.set(null)">Keep</button>
                        <button type="button" class="btn btn-sm bg-rose-600 text-white hover:bg-rose-700" (click)="remove(c)" [disabled]="busy()">Remove</button>
                      </span>
                    } @else {
                      <span class="flex gap-1">
                        <button type="button" class="btn btn-ghost btn-icon" (click)="editing.set(c.id)" [attr.aria-label]="'Edit ' + c.make + ' ' + c.model"><app-icon name="edit" [size]="18" /></button>
                        <button type="button" class="btn btn-ghost btn-icon text-rose-600 dark:text-rose-400" (click)="confirmRemove.set(c.id)" [attr.aria-label]="'Remove ' + c.make + ' ' + c.model"><app-icon name="trash" [size]="18" /></button>
                      </span>
                    }
                  </div>
                }
              </li>
            }
          </ul>
        }
        @if (editing() === 'new' || !cars()!.length) {
          <div class="mt-5 rounded-2xl border border-dashed border-slate-300 p-4 sm:p-5 dark:border-slate-700">
            @if (!cars()!.length && editing() !== 'new') {
              <div class="flex flex-wrap items-center justify-between gap-3">
                <p class="text-sm text-slate-600 dark:text-slate-400">Add your car to start offering rides.</p>
                <button type="button" class="btn btn-primary btn-sm" (click)="editing.set('new')"><app-icon name="plus" [size]="16" /> Add a car</button>
              </div>
            } @else {
              <app-vehicle-form (saved)="onSaved($event)" (cancelled)="editing.set(null)" />
            }
          </div>
        }
      }
    </section>
  `,
})
export class MyCars implements OnInit {
  private readonly api = inject(RidesApi);
  private readonly toast = inject(ToastService);

  protected readonly cars = signal<Vehicle[] | null>(null);
  /** The car being edited, 'new' while adding one, or null. */
  protected readonly editing = signal<string | null>(null);
  protected readonly confirmRemove = signal<string | null>(null);
  protected readonly busy = signal(false);

  async ngOnInit(): Promise<void> {
    try {
      this.cars.set(await this.api.myVehicles());
    } catch (e) {
      this.cars.set([]);
      this.toast.error(problemOf(e).detail);
    }
  }

  protected onSaved(car: Vehicle): void {
    const list = this.cars() ?? [];
    const exists = list.some((c) => c.id === car.id);
    this.cars.set(exists ? list.map((c) => (c.id === car.id ? car : c)) : [...list, car]);
    this.editing.set(null);
    this.toast.success(exists ? 'Car updated.' : `${car.make} ${car.model} added.`);
  }

  protected async remove(car: Vehicle): Promise<void> {
    this.busy.set(true);
    try {
      await this.api.removeVehicle(car.id);
      this.cars.update((list) => (list ?? []).filter((c) => c.id !== car.id));
      this.toast.success('Car removed. Rides you already offered with it are not affected.');
    } catch (e) {
      this.toast.error(problemOf(e).detail);
    } finally {
      this.confirmRemove.set(null);
      this.busy.set(false);
    }
  }
}
