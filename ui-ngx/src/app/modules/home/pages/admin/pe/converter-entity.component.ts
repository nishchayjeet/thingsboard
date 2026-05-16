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
import { Converter } from '@core/http/integration.service';

@Component({
  selector: 'tb-converter-entity',
  templateUrl: './converter-entity.component.html',
  styleUrls: [],
  standalone: false
})
export class ConverterEntityComponent extends EntityComponent<Converter> {

  entityForm: UntypedFormGroup;

  constructor(protected store: Store<AppState>,
              @Inject('entity') protected entityValue: Converter,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<Converter>,
              public fb: UntypedFormBuilder,
              protected cd: ChangeDetectorRef) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  buildForm(entity: Converter): UntypedFormGroup {
    return this.fb.group({
      name: [entity?.name ?? '', [Validators.required, Validators.maxLength(255)]],
      type: [entity?.type ?? 'UPLINK', [Validators.required]],
      debugMode: [entity?.debugMode ?? false],
      scriptLang: [entity?.configuration?.scriptLang ?? 'JS'],
      decoder: [entity?.configuration?.decoder ?? ''],
      encoder: [entity?.configuration?.encoder ?? '']
    });
  }

  updateForm(entity: Converter) {
    this.entityForm.patchValue({
      name: entity.name,
      type: entity.type,
      debugMode: entity.debugMode ?? false,
      scriptLang: entity.configuration?.scriptLang ?? 'JS',
      decoder: entity.configuration?.decoder ?? '',
      encoder: entity.configuration?.encoder ?? ''
    });
  }

  prepareFormValue(formValue: any): any {
    const { scriptLang, decoder, encoder, ...rest } = formValue;
    return {
      ...rest,
      configuration: { scriptLang, decoder, encoder }
    };
  }
}
