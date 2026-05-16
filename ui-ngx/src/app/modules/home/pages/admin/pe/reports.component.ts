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

import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { PageComponent } from '@shared/components/page.component';
import { ReportConfig, ReportService } from '@core/http/report.service';
import { PageLink } from '@shared/models/page/page-link';

/**
 * PE-compatible Reports page. A report is a dashboard snapshot configured with
 * a target dashboard + state + time window + recipients + (optional) schedule.
 * For scheduled execution, create a Scheduler event of type GENERATE_REPORT and
 * link the report ID via configuration.reportId.
 */
@Component({
  selector: 'tb-reports',
  templateUrl: './reports.component.html',
  styleUrls: ['./pe-pages.scss'],
  standalone: false
})
export class ReportsComponent extends PageComponent implements OnInit {

  reports: ReportConfig[] = [];
  selected: ReportConfig | null = null;
  total = 0;
  form: FormGroup;

  constructor(protected store: Store<AppState>,
              private fb: FormBuilder,
              private service: ReportService) {
    super(store);
    this.form = this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(255)]],
      dashboardId: ['', Validators.required],
      stateId: [''],
      format: ['PDF', Validators.required],
      timezone: ['UTC'],
      recipients: ['', Validators.required],
      useDashboardTimewindow: [true]
    });
  }

  ngOnInit() { this.load(); }

  load() {
    this.service.list(new PageLink(100)).subscribe(p => {
      this.reports = p.data || []; this.total = p.totalElements;
    });
  }

  newReport() {
    this.selected = null;
    this.form.reset({
      name: '', dashboardId: '', stateId: '', format: 'PDF',
      timezone: 'UTC', recipients: '', useDashboardTimewindow: true
    });
  }

  select(r: ReportConfig) {
    this.selected = r;
    this.form.reset({
      name: r.name,
      dashboardId: r.dashboardId?.id || '',
      stateId: r.stateId || '',
      format: r.format,
      timezone: r.timezone || 'UTC',
      recipients: (r.recipients || []).join(', '),
      useDashboardTimewindow: r.useDashboardTimewindow ?? true
    });
  }

  save() {
    if (this.form.invalid) return;
    const v = this.form.value;
    const recipients = (v.recipients as string).split(/[,\s]+/).map(s => s.trim()).filter(Boolean);
    const payload: ReportConfig = {
      ...(this.selected || {}),
      name: v.name,
      dashboardId: { id: v.dashboardId },
      stateId: v.stateId || undefined,
      format: v.format,
      timezone: v.timezone,
      recipients,
      useDashboardTimewindow: v.useDashboardTimewindow
    } as ReportConfig;

    this.service.save(payload).subscribe(() => {
      this.selected = null; this.load();
    });
  }

  remove(r: ReportConfig, e: MouseEvent) {
    e.stopPropagation();
    if (!r.id || !confirm(`Delete report "${r.name}"?`)) return;
    this.service.delete(r.id.id).subscribe(() => {
      if (this.selected?.id?.id === r.id?.id) this.selected = null;
      this.load();
    });
  }

  runNow(r: ReportConfig, e: MouseEvent) {
    e.stopPropagation();
    if (!r.id) return;
    if (!confirm(`Generate "${r.name}" now and email it to ${(r.recipients || []).length} recipient(s)?`)) return;
    this.service.runNow(r.id.id).subscribe(
      () => { alert('Report queued — check your inbox.'); this.load(); },
      err => alert('Run failed: ' + (err.error?.message || err.message))
    );
  }

  formatRunStatus(r: ReportConfig): string {
    if (!r.lastRunTime) return '—';
    const when = new Date(r.lastRunTime).toLocaleString();
    return `${r.lastRunStatus || '?'} · ${when}`;
  }
}
