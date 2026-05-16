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
import { INTEGRATION_TYPE_LABELS, Integration, IntegrationService } from '@core/http/integration.service';
import { IntegrationEntityComponent } from './integration-entity.component';

@Injectable()
export class IntegrationsTableConfigResolver {

  private readonly config: EntityTableConfig<Integration> = new EntityTableConfig<Integration>();
  private readonly typeMap = new Map(INTEGRATION_TYPE_LABELS.map(t => [t.value, t.label]));

  constructor(private integrationService: IntegrationService,
              private translate: TranslateService,
              private datePipe: DatePipe) {
    this.config.entityType = EntityType.INTEGRATION;
    this.config.entityComponent = IntegrationEntityComponent;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.INTEGRATION);
    this.config.entityResources = entityTypeResources.get(EntityType.INTEGRATION);

    this.config.columns.push(
      new DateEntityTableColumn<Integration>('createdTime', 'integration.integration', this.datePipe, '180px'),
      new EntityTableColumn<Integration>('name', 'integration.name', '30%'),
      new EntityTableColumn<Integration>('type', 'integration.type', '20%',
        e => this.typeMap.get(e.type) ?? e.type),
      new EntityTableColumn<Integration>('enabled', 'integration.enabled', '10%',
        e => e.enabled ? '✓' : ''),
      new EntityTableColumn<Integration>('routingKey', 'integration.routing-key', '20%')
    );

    this.config.deleteEntityTitle = e =>
      this.translate.instant('integration.delete-integration-title', { integrationName: e.name });
    this.config.deleteEntityContent = () => this.translate.instant('integration.delete-integration-text');
    this.config.deleteEntitiesTitle = count =>
      this.translate.instant('integration.delete-integrations-title', { count });
    this.config.deleteEntitiesContent = () => this.translate.instant('integration.delete-integrations-text');

    this.config.entitiesFetchFunction = pageLink => this.integrationService.getIntegrations(pageLink);
    this.config.loadEntity = id => this.integrationService.getIntegration(id.id);
    this.config.saveEntity = e => this.integrationService.saveIntegration(e);
    this.config.deleteEntity = id => this.integrationService.deleteIntegration(id.id);
  }

  resolve(): EntityTableConfig<Integration> {
    this.config.tableTitle = this.translate.instant('integration.integrations');
    return this.config;
  }
}
