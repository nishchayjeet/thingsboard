///
/// Copyright © 2016-2026 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { PageComponent } from '@shared/components/page.component';
import { ScheduledEvent, ScheduledEventService } from '@core/http/scheduled-event.service';
import { PageLink } from '@shared/models/page/page-link';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

/**
 * PE-compatible scheduler events page. Two-pane layout: list on the left, inline
 * form on the right. Matches PE's "Configuration tab + Schedule tab" structure
 * but rendered as two collapsible sections in one form (no modal).
 */
@Component({
  selector: 'tb-scheduler-events',
  templateUrl: './scheduler-events.component.html',
  styleUrls: ['./pe-pages.scss'],
  standalone: false
})
export class SchedulerEventsComponent extends PageComponent implements OnInit, OnDestroy {

  // Match PE's built-in event types from the docs.
  readonly EVENT_TYPES = [
    { value: 'CUSTOM',           label: 'Custom rule-engine message' },
    { value: 'GENERATE_REPORT',  label: 'Generate report' },
    { value: 'UPDATE_ATTRIBUTES', label: 'Update attributes' },
    { value: 'SEND_RPC',         label: 'Send RPC request to device' }
  ];

  readonly REPEAT_TYPES = [
    { value: 'NONE',     label: 'Once (no repeat)' },
    { value: 'TIMER',    label: 'Every N milliseconds (timer)' },
    { value: 'MINUTELY', label: 'Every minute' },
    { value: 'HOURLY',   label: 'Every hour' },
    { value: 'DAILY',    label: 'Daily' },
    { value: 'WEEKLY',   label: 'Weekly' },
    { value: 'MONTHLY',  label: 'Monthly' },
    { value: 'YEARLY',   label: 'Yearly' },
    { value: 'CRON',     label: 'Cron expression' }
  ];

  events: ScheduledEvent[] = [];
  selected: ScheduledEvent | null = null;
  totalEvents = 0;

  form: FormGroup;

  private destroy$ = new Subject<void>();

  constructor(protected store: Store<AppState>,
              private fb: FormBuilder,
              private service: ScheduledEventService) {
    super(store);
    this.form = this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(255)]],
      type: ['CUSTOM', Validators.required],
      enabled: [true],
      configuration: ['{}', Validators.required],
      schedule: this.fb.group({
        startTime: [Date.now() + 60_000],
        timezone: ['UTC'],
        repeat: ['NONE', Validators.required],
        repeatIntervalMs: [60000],
        cron: [''],
        endsOn: [null]
      })
    });
  }

  ngOnInit() { this.load(); }

  ngOnDestroy() {
    super.ngOnDestroy();
    this.destroy$.next();
    this.destroy$.complete();
  }

  load() {
    const pageLink = new PageLink(50);
    this.service.getScheduledEvents(pageLink).pipe(takeUntil(this.destroy$)).subscribe(page => {
      this.events = page.data || [];
      this.totalEvents = page.totalElements;
    });
  }

  newEvent() {
    this.selected = null;
    this.form.reset({
      name: '',
      type: 'CUSTOM',
      enabled: true,
      configuration: '{\n  "msgBody": "{}"\n}',
      schedule: {
        startTime: Date.now() + 60_000,
        timezone: 'UTC',
        repeat: 'NONE',
        repeatIntervalMs: 60000,
        cron: '',
        endsOn: null
      }
    });
  }

  select(event: ScheduledEvent) {
    this.selected = event;
    this.form.reset({
      name: event.name,
      type: event.type,
      enabled: event.enabled,
      configuration: JSON.stringify(event.configuration || {}, null, 2),
      schedule: {
        startTime: event.schedule?.startTime ?? Date.now(),
        timezone: event.schedule?.timezone ?? 'UTC',
        repeat: event.schedule?.repeat ?? 'NONE',
        repeatIntervalMs: event.schedule?.repeatIntervalMs ?? 60000,
        cron: event.schedule?.cron ?? '',
        endsOn: event.schedule?.endsOn ?? null
      }
    });
  }

  save() {
    if (this.form.invalid) return;
    let config: any = {};
    try { config = JSON.parse(this.form.value.configuration || '{}'); }
    catch (e) { alert('Configuration is not valid JSON'); return; }

    const payload: ScheduledEvent = {
      ...(this.selected || {}),
      name: this.form.value.name,
      type: this.form.value.type,
      enabled: this.form.value.enabled,
      configuration: config,
      schedule: this.form.value.schedule
    } as ScheduledEvent;

    this.service.saveScheduledEvent(payload).subscribe(() => {
      this.selected = null;
      this.load();
    });
  }

  remove(event: ScheduledEvent) {
    if (!event.id || !confirm(`Delete scheduled event "${event.name}"?`)) return;
    this.service.deleteScheduledEvent(event.id.id).subscribe(() => {
      if (this.selected?.id?.id === event.id?.id) this.selected = null;
      this.load();
    });
  }

  formatSchedule(ev: ScheduledEvent): string {
    const r = ev.schedule?.repeat;
    if (!r || r === 'NONE') return 'Once';
    if (r === 'CRON') return 'Cron: ' + (ev.schedule?.cron || '?');
    if (r === 'TIMER') return `Every ${ev.schedule?.repeatIntervalMs ?? '?'} ms`;
    return r.charAt(0) + r.slice(1).toLowerCase();
  }

  formatNextFire(ts?: number) { return ts ? new Date(ts).toLocaleString() : '—'; }
}
