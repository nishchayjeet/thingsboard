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

import { ChangeDetectorRef, Component, Inject } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { UntypedFormBuilder, UntypedFormGroup, Validators } from '@angular/forms';
import { EntityComponent } from '@home/components/entity/entity.component';
import { EntityTableConfig } from '@home/models/entity/entities-table-config.models';
import { ReportConfig } from '@core/http/report.service';

@Component({
  selector: 'tb-report-entity',
  templateUrl: './report-entity.component.html',
  styleUrls: [],
  standalone: false
})
export class ReportEntityComponent extends EntityComponent<ReportConfig> {

  entityForm: UntypedFormGroup;

  constructor(protected store: Store<AppState>,
              @Inject('entity') protected entityValue: ReportConfig,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<ReportConfig>,
              public fb: UntypedFormBuilder,
              protected cd: ChangeDetectorRef) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  buildForm(entity: ReportConfig): UntypedFormGroup {
    return this.fb.group({
      name: [entity?.name ?? '', [Validators.required, Validators.maxLength(255)]],
      dashboardIdRaw: [entity?.dashboardId?.id ?? '', [Validators.required]],
      stateId: [entity?.stateId ?? ''],
      format: [entity?.format ?? 'PDF', [Validators.required]],
      timezone: [entity?.timezone ?? ''],
      recipientsRaw: [(entity?.recipients ?? []).join(', ')],
      useDashboardTimewindow: [entity?.useDashboardTimewindow ?? true]
    });
  }

  updateForm(entity: ReportConfig) {
    this.entityForm.patchValue({
      name: entity.name,
      dashboardIdRaw: entity.dashboardId?.id ?? '',
      stateId: entity.stateId ?? '',
      format: entity.format ?? 'PDF',
      timezone: entity.timezone ?? '',
      recipientsRaw: (entity.recipients ?? []).join(', '),
      useDashboardTimewindow: entity.useDashboardTimewindow ?? true
    });
  }

  prepareFormValue(formValue: any): any {
    const { dashboardIdRaw, recipientsRaw, ...rest } = formValue;
    return {
      ...rest,
      dashboardId: { id: dashboardIdRaw },
      recipients: (recipientsRaw || '')
        .split(/[,\n]/).map((s: string) => s.trim()).filter((s: string) => s.length > 0)
    };
  }
}
