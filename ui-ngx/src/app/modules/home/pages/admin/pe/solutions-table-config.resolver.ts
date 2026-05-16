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
  CellActionDescriptorType,
  DateEntityTableColumn,
  EntityTableColumn,
  EntityTableConfig
} from '@home/models/entity/entities-table-config.models';
import { EntityType, entityTypeResources, entityTypeTranslations } from '@shared/models/entity-type.models';
import { SolutionTemplate, SolutionTemplateService } from '@core/http/solution-template.service';
import { SolutionEntityComponent } from './solution-entity.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { ActionNotificationShow } from '@core/notification/notification.actions';

@Injectable()
export class SolutionsTableConfigResolver {

  private readonly config: EntityTableConfig<SolutionTemplate> = new EntityTableConfig<SolutionTemplate>();

  constructor(private solutionService: SolutionTemplateService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private store: Store<AppState>) {
    this.config.entityType = EntityType.SOLUTION_TEMPLATE;
    this.config.entityComponent = SolutionEntityComponent;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.SOLUTION_TEMPLATE);
    this.config.entityResources = entityTypeResources.get(EntityType.SOLUTION_TEMPLATE);

    this.config.columns.push(
      new DateEntityTableColumn<SolutionTemplate>('createdTime', 'solution.template', this.datePipe, '180px'),
      new EntityTableColumn<SolutionTemplate>('name', 'solution.name', '30%'),
      new EntityTableColumn<SolutionTemplate>('category', 'solution.category', '25%',
        e => e.category ?? ''),
      new EntityTableColumn<SolutionTemplate>('description', 'solution.description', '35%',
        e => e.description ?? '')
    );

    this.config.cellActionDescriptors.push({
      name: this.translate.instant('solution.install'),
      icon: 'cloud_download',
      type: CellActionDescriptorType.DEFAULT,
      isEnabled: () => true,
      onAction: ($event, entity) => this.install($event, entity)
    });

    this.config.deleteEntityTitle = t =>
      this.translate.instant('solution.delete-template-title', { templateName: t.name });
    this.config.deleteEntityContent = () => this.translate.instant('solution.delete-template-text');
    this.config.deleteEntitiesTitle = count =>
      this.translate.instant('solution.delete-templates-title', { count });
    this.config.deleteEntitiesContent = () => this.translate.instant('solution.delete-templates-text');

    this.config.entitiesFetchFunction = pageLink => this.solutionService.list(pageLink);
    this.config.loadEntity = id => this.solutionService.get(id.id);
    this.config.saveEntity = t => this.solutionService.save(t);
    this.config.deleteEntity = id => this.solutionService.delete(id.id);
  }

  resolve(): EntityTableConfig<SolutionTemplate> {
    this.config.tableTitle = this.translate.instant('solution.templates');
    return this.config;
  }

  private install($event: Event, entity: SolutionTemplate) {
    if ($event) $event.stopPropagation();
    if (!entity.id) return;
    this.solutionService.install(entity.id.id).subscribe(result => {
      this.store.dispatch(new ActionNotificationShow({
        message: this.translate.instant('solution.install') + ': ' + entity.name +
          ' (' + result.deviceProfileIds.length + ' profiles, ' +
          result.ruleChainIds.length + ' rule chains, ' +
          result.dashboardIds.length + ' dashboards)',
        type: 'success',
        duration: 3500,
        verticalPosition: 'bottom',
        horizontalPosition: 'right'
      }));
    });
  }
}
