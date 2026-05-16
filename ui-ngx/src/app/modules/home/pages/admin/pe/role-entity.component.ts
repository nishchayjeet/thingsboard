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
import { Role } from '@core/http/role.service';

@Component({
  selector: 'tb-role-entity',
  templateUrl: './role-entity.component.html',
  styleUrls: [],
  standalone: false
})
export class RoleEntityComponent extends EntityComponent<Role> {

  entityForm: UntypedFormGroup;

  constructor(protected store: Store<AppState>,
              @Inject('entity') protected entityValue: Role,
              @Inject('entitiesTableConfig') protected entitiesTableConfigValue: EntityTableConfig<Role>,
              public fb: UntypedFormBuilder,
              protected cd: ChangeDetectorRef) {
    super(store, fb, entityValue, entitiesTableConfigValue, cd);
  }

  buildForm(entity: Role): UntypedFormGroup {
    return this.fb.group({
      name: [entity ? entity.name : '', [Validators.required, Validators.maxLength(255)]],
      type: [entity ? entity.type : 'GENERIC', [Validators.required]],
      permissions: [entity ? this.stringify(entity.permissions) : '{}'],
      additionalInfo: [entity ? entity.additionalInfo : '']
    });
  }

  updateForm(entity: Role) {
    this.entityForm.patchValue({
      name: entity.name,
      type: entity.type,
      permissions: this.stringify(entity.permissions),
      additionalInfo: entity.additionalInfo || ''
    });
  }

  prepareFormValue(formValue: any): any {
    let permissions: Role['permissions'];
    try {
      permissions = JSON.parse(formValue.permissions || '{}');
    } catch {
      permissions = formValue.type === 'GROUP' ? { operations: [] } : {};
    }
    return {
      ...formValue,
      permissions
    };
  }

  private stringify(obj: any): string {
    try {
      return JSON.stringify(obj ?? {}, null, 2);
    } catch {
      return '{}';
    }
  }
}
