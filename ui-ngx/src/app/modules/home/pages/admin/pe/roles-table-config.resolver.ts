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

import { Injectable } from '@angular/core';
import { DatePipe } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import {
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { Role, RoleService } from '@core/http/role.service';
import { RoleEntityComponent } from './role-entity.component';

@Injectable()
export class RolesTableConfigResolver {

  private readonly config: EntityTableConfig<Role> = new EntityTableConfig<Role>();

  constructor(private roleService: RoleService,
              private translate: TranslateService,
              private datePipe: DatePipe) {

    this.config.entityType = EntityType.ROLE;
    this.config.entityComponent = RoleEntityComponent;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.ROLE);
    this.config.entityResources = entityTypeResources.get(EntityType.ROLE);

    this.config.columns.push(
      new DateEntityTableColumn<Role>('createdTime', 'role.created-time', this.datePipe, '180px'),
      new EntityTableColumn<Role>('name', 'role.name', '40%'),
      new EntityTableColumn<Role>('type', 'role.type', '20%',
        entity => this.translate.instant(entity.type === 'GROUP' ? 'role.group' : 'role.generic'))
    );

    this.config.deleteEntityTitle = role =>
      this.translate.instant('role.delete-role-title', { roleName: role.name });
    this.config.deleteEntityContent = () => this.translate.instant('role.delete-role-text');
    this.config.deleteEntitiesTitle = count =>
      this.translate.instant('role.delete-roles-title', { count });
    this.config.deleteEntitiesContent = () => this.translate.instant('role.delete-roles-text');

    this.config.entitiesFetchFunction = pageLink => this.roleService.getRoles(pageLink);
    this.config.loadEntity = id => this.roleService.getRole(id.id);
    this.config.saveEntity = role => this.roleService.saveRole(role);
    this.config.deleteEntity = id => this.roleService.deleteRole(id.id);
  }

  resolve(): EntityTableConfig<Role> {
    this.config.tableTitle = this.translate.instant('role.roles');
    return this.config;
  }
}
