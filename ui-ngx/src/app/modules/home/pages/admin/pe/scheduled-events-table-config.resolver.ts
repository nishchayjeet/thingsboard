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
import { ScheduledEvent, ScheduledEventService } from '@core/http/scheduled-event.service';
import { ScheduledEventEntityComponent } from './scheduled-event-entity.component';

@Injectable()
export class ScheduledEventsTableConfigResolver {

  private readonly config: EntityTableConfig<ScheduledEvent> = new EntityTableConfig<ScheduledEvent>();

  constructor(private scheduledEventService: ScheduledEventService,
              private translate: TranslateService,
              private datePipe: DatePipe) {
    this.config.entityType = EntityType.SCHEDULED_EVENT;
    this.config.entityComponent = ScheduledEventEntityComponent;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.SCHEDULED_EVENT);
    this.config.entityResources = entityTypeResources.get(EntityType.SCHEDULED_EVENT);

    this.config.columns.push(
      new DateEntityTableColumn<ScheduledEvent>('createdTime', 'scheduler.scheduled-event', this.datePipe, '180px'),
      new EntityTableColumn<ScheduledEvent>('name', 'scheduler.name', '35%'),
      new EntityTableColumn<ScheduledEvent>('type', 'scheduler.type', '20%'),
      new EntityTableColumn<ScheduledEvent>('schedule', 'scheduler.repeat', '20%',
        e => e.schedule?.repeat || 'NONE')
    );

    this.config.deleteEntityTitle = e =>
      this.translate.instant('scheduler.delete-event-title', { eventName: e.name });
    this.config.deleteEntityContent = () => this.translate.instant('scheduler.delete-event-text');
    this.config.deleteEntitiesTitle = count =>
      this.translate.instant('scheduler.delete-events-title', { count });
    this.config.deleteEntitiesContent = () => this.translate.instant('scheduler.delete-events-text');

    this.config.entitiesFetchFunction = pageLink => this.scheduledEventService.getScheduledEvents(pageLink);
    this.config.loadEntity = id => this.scheduledEventService.getScheduledEvent(id.id);
    this.config.saveEntity = e => this.scheduledEventService.saveScheduledEvent(e);
    this.config.deleteEntity = id => this.scheduledEventService.deleteScheduledEvent(id.id);
  }

  resolve(): EntityTableConfig<ScheduledEvent> {
    this.config.tableTitle = this.translate.instant('scheduler.scheduled-events');
    return this.config;
  }
}
