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
import { ScheduledEvent } from '@core/http/scheduled-event.service';

@Component({
  selector: 'tb-scheduled-event-entity',
  templateUrl: './scheduled-event-entity.component.html',
  styleUrls: [],
  standalone: false
})
export class ScheduledEventEntityComponent extends EntityComponent<ScheduledEvent> {

  entityForm: UntypedFormGroup;

  readonly REPEAT_OPTIONS = ['NONE', 'MINUTELY', 'HOURLY', 'DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY', 'TIMER', 'CRON'];
  readonly EVENT_TYPES = ['GENERATE_REPORT', 'TRIGGER_RULE_CHAIN', 'EXECUTE_HTTP_REQUEST', 'CUSTOM'];

  constructor(protected store: Store<AppState>,
              @Inject('entity') protected entityValue: ScheduledEvent,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<ScheduledEvent>,
              public fb: UntypedFormBuilder,
              protected cd: ChangeDetectorRef) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  buildForm(entity: ScheduledEvent): UntypedFormGroup {
    return this.fb.group({
      name: [entity?.name ?? '', [Validators.required, Validators.maxLength(255)]],
      type: [entity?.type ?? 'CUSTOM', [Validators.required]],
      enabled: [entity?.enabled ?? true],
      schedule: this.fb.group({
        repeat: [entity?.schedule?.repeat ?? 'NONE', [Validators.required]],
        startTime: [entity?.schedule?.startTime ?? null],
        timezone: [entity?.schedule?.timezone ?? null],
        repeatIntervalMs: [entity?.schedule?.repeatIntervalMs ?? null],
        cron: [entity?.schedule?.cron ?? null]
      }),
      configuration: [entity ? JSON.stringify(entity.configuration ?? {}, null, 2) : '{}']
    });
  }

  updateForm(entity: ScheduledEvent) {
    this.entityForm.patchValue({
      name: entity.name,
      type: entity.type,
      enabled: entity.enabled,
      schedule: {
        repeat: entity.schedule?.repeat ?? 'NONE',
        startTime: entity.schedule?.startTime ?? null,
        timezone: entity.schedule?.timezone ?? null,
        repeatIntervalMs: entity.schedule?.repeatIntervalMs ?? null,
        cron: entity.schedule?.cron ?? null
      },
      configuration: JSON.stringify(entity.configuration ?? {}, null, 2)
    });
  }

  prepareFormValue(formValue: any): any {
    let configuration: any = {};
    try { configuration = JSON.parse(formValue.configuration || '{}'); } catch { /* empty */ }
    return { ...formValue, configuration };
  }
}
