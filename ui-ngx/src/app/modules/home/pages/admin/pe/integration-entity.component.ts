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
import { INTEGRATION_TYPE_LABELS, Integration } from '@core/http/integration.service';

@Component({
  selector: 'tb-integration-entity',
  templateUrl: './integration-entity.component.html',
  styleUrls: [],
  standalone: false
})
export class IntegrationEntityComponent extends EntityComponent<Integration> {

  entityForm: UntypedFormGroup;
  readonly TYPES = INTEGRATION_TYPE_LABELS;
  readonly TYPE_GROUPS = Array.from(new Set(INTEGRATION_TYPE_LABELS.map(t => t.group)));

  constructor(protected store: Store<AppState>,
              @Inject('entity') protected entityValue: Integration,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<Integration>,
              public fb: UntypedFormBuilder,
              protected cd: ChangeDetectorRef) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  groupedTypes(group: string) {
    return this.TYPES.filter(t => t.group === group);
  }

  buildForm(entity: Integration): UntypedFormGroup {
    return this.fb.group({
      name: [entity?.name ?? '', [Validators.required, Validators.maxLength(255)]],
      type: [entity?.type ?? 'HTTP', [Validators.required]],
      routingKey: [entity?.routingKey ?? ''],
      enabled: [entity?.enabled ?? true],
      debugMode: [entity?.debugMode ?? false],
      allowCreateDevicesOrAssets: [entity?.allowCreateDevicesOrAssets ?? false],
      defaultConverterIdRaw: [entity?.defaultConverterId?.id ?? ''],
      downlinkConverterIdRaw: [entity?.downlinkConverterId?.id ?? ''],
      secret: [entity?.secret ?? ''],
      configuration: [entity ? JSON.stringify(entity.configuration ?? {}, null, 2) : '{}']
    });
  }

  updateForm(entity: Integration) {
    this.entityForm.patchValue({
      name: entity.name,
      type: entity.type,
      routingKey: entity.routingKey ?? '',
      enabled: entity.enabled,
      debugMode: entity.debugMode ?? false,
      allowCreateDevicesOrAssets: entity.allowCreateDevicesOrAssets ?? false,
      defaultConverterIdRaw: entity.defaultConverterId?.id ?? '',
      downlinkConverterIdRaw: entity.downlinkConverterId?.id ?? '',
      secret: entity.secret ?? '',
      configuration: JSON.stringify(entity.configuration ?? {}, null, 2)
    });
  }

  prepareFormValue(formValue: any): any {
    const { defaultConverterIdRaw, downlinkConverterIdRaw, configuration, ...rest } = formValue;
    let cfg: any = {};
    try { cfg = JSON.parse(configuration || '{}'); } catch { /* empty */ }
    const payload: any = { ...rest, configuration: cfg };
    if (defaultConverterIdRaw) payload.defaultConverterId = { id: defaultConverterIdRaw };
    if (downlinkConverterIdRaw) payload.downlinkConverterId = { id: downlinkConverterIdRaw };
    return payload;
  }
}
