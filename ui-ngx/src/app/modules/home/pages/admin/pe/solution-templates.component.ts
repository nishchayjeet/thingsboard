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
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { PageComponent } from '@shared/components/page.component';
import { InstallResult, SolutionTemplate, SolutionTemplateService } from '@core/http/solution-template.service';
import { PageLink } from '@shared/models/page/page-link';

/**
 * PE-compatible Solution Templates page. Browse template cards, click "Install"
 * to replay the bundle into the current tenant. After install, the toast lists
 * the IDs of created device profiles, rule chains, and dashboards.
 */
@Component({
  selector: 'tb-solution-templates',
  templateUrl: './solution-templates.component.html',
  styleUrls: ['./pe-pages.scss'],
  standalone: false
})
export class SolutionTemplatesComponent extends PageComponent implements OnInit {

  templates: SolutionTemplate[] = [];
  selected: SolutionTemplate | null = null;
  total = 0;
  categoryFilter = '';
  installing: { [id: string]: boolean } = {};
  installResult: InstallResult | null = null;

  constructor(protected store: Store<AppState>,
              private service: SolutionTemplateService) {
    super(store);
  }

  ngOnInit() { this.load(); }

  load() {
    this.service.list(new PageLink(100), this.categoryFilter || undefined).subscribe(p => {
      this.templates = p.data || [];
      this.total = p.totalElements;
    });
  }

  categories(): string[] {
    return [...new Set(this.templates.map(t => t.category).filter(Boolean) as string[])];
  }

  select(t: SolutionTemplate) { this.selected = t; this.installResult = null; }

  countOf(t: SolutionTemplate, key: string): number {
    const v = (t.bundle && t.bundle[key]);
    return Array.isArray(v) ? v.length : 0;
  }

  install(t: SolutionTemplate) {
    if (!t.id) return;
    if (!confirm(`Install "${t.name}" into your tenant?\n\n` +
      `This will create ${this.countOf(t, 'deviceProfiles')} device profiles, ` +
      `${this.countOf(t, 'ruleChains')} rule chains, ` +
      `${this.countOf(t, 'dashboards')} dashboards.`)) return;
    this.installing[t.id.id] = true;
    this.installResult = null;
    this.service.install(t.id.id).subscribe(
      result => {
        this.installing[t.id!.id] = false;
        this.installResult = result;
      },
      err => {
        this.installing[t.id!.id] = false;
        alert('Install failed: ' + (err.error?.message || err.message));
      });
  }
}
