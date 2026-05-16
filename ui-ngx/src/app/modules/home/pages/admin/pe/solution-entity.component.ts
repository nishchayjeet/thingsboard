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
import { SolutionTemplate } from '@core/http/solution-template.service';

@Component({
  selector: 'tb-solution-entity',
  templateUrl: './solution-entity.component.html',
  styleUrls: [],
  standalone: false
})
export class SolutionEntityComponent extends EntityComponent<SolutionTemplate> {

  entityForm: UntypedFormGroup;

  constructor(protected store: Store<AppState>,
              @Inject('entity') protected entityValue: SolutionTemplate,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<SolutionTemplate>,
              public fb: UntypedFormBuilder,
              protected cd: ChangeDetectorRef) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  buildForm(entity: SolutionTemplate): UntypedFormGroup {
    return this.fb.group({
      name: [entity?.name ?? '', [Validators.required, Validators.maxLength(255)]],
      title: [entity?.title ?? ''],
      category: [entity?.category ?? ''],
      description: [entity?.description ?? ''],
      previewImage: [entity?.previewImage ?? ''],
      bundle: [entity ? JSON.stringify(entity.bundle ?? {}, null, 2) : '{}']
    });
  }

  updateForm(entity: SolutionTemplate) {
    this.entityForm.patchValue({
      name: entity.name,
      title: entity.title ?? '',
      category: entity.category ?? '',
      description: entity.description ?? '',
      previewImage: entity.previewImage ?? '',
      bundle: JSON.stringify(entity.bundle ?? {}, null, 2)
    });
  }

  prepareFormValue(formValue: any): any {
    let bundle: any = {};
    try { bundle = JSON.parse(formValue.bundle || '{}'); } catch { /* empty */ }
    return { ...formValue, bundle };
  }
}
