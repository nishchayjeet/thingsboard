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
import { PayloadCodec } from '@core/http/payload-codec.service';

@Component({
  selector: 'tb-codec-entity',
  templateUrl: './codec-entity.component.html',
  styleUrls: [],
  standalone: false
})
export class CodecEntityComponent extends EntityComponent<PayloadCodec> {

  entityForm: UntypedFormGroup;

  constructor(protected store: Store<AppState>,
              @Inject('entity') protected entityValue: PayloadCodec,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<PayloadCodec>,
              public fb: UntypedFormBuilder,
              protected cd: ChangeDetectorRef) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  buildForm(entity: PayloadCodec): UntypedFormGroup {
    return this.fb.group({
      name: [entity?.name ?? '', [Validators.required, Validators.maxLength(255)]],
      vendor: [entity?.vendor ?? ''],
      model: [entity?.model ?? ''],
      category: [entity?.category ?? ''],
      description: [entity?.description ?? ''],
      decoderType: [entity?.decoderType ?? 'JS', [Validators.required]],
      decoderFunction: [entity?.decoderFunction ?? '', [Validators.required]],
      sampleInput: [entity?.sampleInput ?? ''],
      sampleOutput: [entity?.sampleOutput ?? ''],
      documentationUrl: [entity?.documentationUrl ?? '']
    });
  }

  updateForm(entity: PayloadCodec) {
    this.entityForm.patchValue({
      name: entity.name,
      vendor: entity.vendor ?? '',
      model: entity.model ?? '',
      category: entity.category ?? '',
      description: entity.description ?? '',
      decoderType: entity.decoderType ?? 'JS',
      decoderFunction: entity.decoderFunction ?? '',
      sampleInput: entity.sampleInput ?? '',
      sampleOutput: entity.sampleOutput ?? '',
      documentationUrl: entity.documentationUrl ?? ''
    });
  }
}
