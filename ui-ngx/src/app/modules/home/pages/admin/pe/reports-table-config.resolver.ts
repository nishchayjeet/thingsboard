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
import { ReportConfig, ReportService } from '@core/http/report.service';
import { ReportEntityComponent } from './report-entity.component';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { ActionNotificationShow } from '@core/notification/notification.actions';

@Injectable()
export class ReportsTableConfigResolver {

  private readonly config: EntityTableConfig<ReportConfig> = new EntityTableConfig<ReportConfig>();

  constructor(private reportService: ReportService,
              private translate: TranslateService,
              private datePipe: DatePipe,
              private store: Store<AppState>) {
    this.config.entityType = EntityType.REPORT_CONFIG;
    this.config.entityComponent = ReportEntityComponent;
    this.config.entityTranslations = entityTypeTranslations.get(EntityType.REPORT_CONFIG);
    this.config.entityResources = entityTypeResources.get(EntityType.REPORT_CONFIG);

    this.config.columns.push(
      new DateEntityTableColumn<ReportConfig>('createdTime', 'report.report', this.datePipe, '180px'),
      new EntityTableColumn<ReportConfig>('name', 'report.name', '30%'),
      new EntityTableColumn<ReportConfig>('format', 'report.format', '15%'),
      new EntityTableColumn<ReportConfig>('lastRunStatus', 'report.last-run', '15%',
        e => e.lastRunStatus || '—'),
      new EntityTableColumn<ReportConfig>('recipients', 'report.recipients', '20%',
        e => (e.recipients || []).join(', '))
    );

    this.config.cellActionDescriptors.push({
      name: this.translate.instant('report.run-now'),
      icon: 'play_arrow',
      type: CellActionDescriptorType.DEFAULT,
      isEnabled: () => true,
      onAction: ($event, entity) => this.runNow($event, entity)
    });

    this.config.deleteEntityTitle = r =>
      this.translate.instant('report.delete-report-title', { reportName: r.name });
    this.config.deleteEntityContent = () => this.translate.instant('report.delete-report-text');
    this.config.deleteEntitiesTitle = count =>
      this.translate.instant('report.delete-reports-title', { count });
    this.config.deleteEntitiesContent = () => this.translate.instant('report.delete-reports-text');

    this.config.entitiesFetchFunction = pageLink => this.reportService.list(pageLink);
    this.config.loadEntity = id => this.reportService.get(id.id);
    this.config.saveEntity = r => this.reportService.save(r);
    this.config.deleteEntity = id => this.reportService.delete(id.id);
  }

  resolve(): EntityTableConfig<ReportConfig> {
    this.config.tableTitle = this.translate.instant('report.reports');
    return this.config;
  }

  private runNow($event: Event, entity: ReportConfig) {
    if ($event) $event.stopPropagation();
    if (!entity.id) return;
    this.reportService.runNow(entity.id.id).subscribe(() => {
      this.store.dispatch(new ActionNotificationShow({
        message: this.translate.instant('report.run-now') + ': ' + entity.name,
        type: 'success',
        duration: 2500,
        verticalPosition: 'bottom',
        horizontalPosition: 'right'
      }));
    });
  }
}
