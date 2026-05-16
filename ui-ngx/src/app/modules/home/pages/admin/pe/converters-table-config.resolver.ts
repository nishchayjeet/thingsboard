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
import { Converter, IntegrationService } from '@core/http/integration.service';
import { ConverterEntityComponent } from './converter-entity.component';

@Injectable()
export class ConvertersTableConfigResolver {

  private readonly config: EntityTableConfig<Converter> = new EntityTableConfig<Converter>();

  constructor(private integrationService: IntegrationService,
              private translate: TranslateService,
              private datePipe: DatePipe) {
    this.config.entityType = EntityType.CONVERTER;
    this.config.entityComponent = ConverterEntityComponent;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.CONVERTER);
    this.config.entityResources = entityTypeResources.get(EntityType.CONVERTER);

    this.config.columns.push(
      new DateEntityTableColumn<Converter>('createdTime', 'converter.converter', this.datePipe, '180px'),
      new EntityTableColumn<Converter>('name', 'converter.name', '50%'),
      new EntityTableColumn<Converter>('type', 'converter.type', '20%')
    );

    this.config.deleteEntityTitle = c =>
      this.translate.instant('converter.delete-converter-title', { converterName: c.name });
    this.config.deleteEntityContent = () => this.translate.instant('converter.delete-converter-text');
    this.config.deleteEntitiesTitle = count =>
      this.translate.instant('converter.delete-converters-title', { count });
    this.config.deleteEntitiesContent = () => this.translate.instant('converter.delete-converters-text');

    this.config.entitiesFetchFunction = pageLink => this.integrationService.getConverters(pageLink);
    this.config.loadEntity = id => this.integrationService.getConverter(id.id);
    this.config.saveEntity = c => this.integrationService.saveConverter(c);
    this.config.deleteEntity = id => this.integrationService.deleteConverter(id.id);
  }

  resolve(): EntityTableConfig<Converter> {
    this.config.tableTitle = this.translate.instant('converter.converters');
    return this.config;
  }
}
