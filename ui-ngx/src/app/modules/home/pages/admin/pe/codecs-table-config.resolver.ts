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
import { PayloadCodec, PayloadCodecService } from '@core/http/payload-codec.service';
import { CodecEntityComponent } from './codec-entity.component';

@Injectable()
export class CodecsTableConfigResolver {

  private readonly config: EntityTableConfig<PayloadCodec> = new EntityTableConfig<PayloadCodec>();

  constructor(private codecService: PayloadCodecService,
              private translate: TranslateService,
              private datePipe: DatePipe) {
    this.config.entityType = EntityType.PAYLOAD_CODEC;
    this.config.entityComponent = CodecEntityComponent;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.PAYLOAD_CODEC);
    this.config.entityResources = entityTypeResources.get(EntityType.PAYLOAD_CODEC);

    this.config.columns.push(
      new DateEntityTableColumn<PayloadCodec>('createdTime', 'codec.codec', this.datePipe, '180px'),
      new EntityTableColumn<PayloadCodec>('name', 'codec.name', '30%'),
      new EntityTableColumn<PayloadCodec>('vendor', 'codec.vendor', '20%', e => e.vendor ?? ''),
      new EntityTableColumn<PayloadCodec>('model', 'codec.model', '20%', e => e.model ?? ''),
      new EntityTableColumn<PayloadCodec>('category', 'codec.category', '20%', e => e.category ?? '')
    );

    this.config.deleteEntityTitle = c =>
      this.translate.instant('codec.delete-codec-title', { codecName: c.name });
    this.config.deleteEntityContent = () => this.translate.instant('codec.delete-codec-text');
    this.config.deleteEntitiesTitle = count =>
      this.translate.instant('codec.delete-codecs-title', { count });
    this.config.deleteEntitiesContent = () => this.translate.instant('codec.delete-codecs-text');

    this.config.entitiesFetchFunction = pageLink => this.codecService.list(pageLink);
    this.config.loadEntity = id => this.codecService.get(id.id);
    this.config.saveEntity = c => this.codecService.save(c);
    this.config.deleteEntity = id => this.codecService.delete(id.id);
  }

  resolve(): EntityTableConfig<PayloadCodec> {
    this.config.tableTitle = this.translate.instant('codec.library');
    return this.config;
  }
}
