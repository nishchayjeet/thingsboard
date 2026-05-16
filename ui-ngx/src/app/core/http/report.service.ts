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
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { defaultHttpOptionsFromConfig, RequestConfig } from './http-utils';
import { PageData } from '@shared/models/page/page-data';
import { PageLink } from '@shared/models/page/page-link';

export interface ReportConfig {
  id?: { id: string };
  createdTime?: number;
  tenantId?: { id: string };
  name: string;
  dashboardId: { id: string };
  stateId?: string;
  format: 'PDF' | 'PNG';
  timezone?: string;
  recipients: string[];
  schedule?: any;
  useDashboardTimewindow?: boolean;
  timewindow?: any;
  lastRunStatus?: string;
  lastRunTime?: number;
  lastRunError?: string;
}

@Injectable({ providedIn: 'root' })
export class ReportService {

  constructor(private http: HttpClient) {}

  save(report: ReportConfig, c?: RequestConfig): Observable<ReportConfig> {
    return this.http.post<ReportConfig>('/api/report/config', report, defaultHttpOptionsFromConfig(c));
  }

  get(id: string, c?: RequestConfig): Observable<ReportConfig> {
    return this.http.get<ReportConfig>(`/api/report/config/${id}`, defaultHttpOptionsFromConfig(c));
  }

  list(pageLink: PageLink, c?: RequestConfig): Observable<PageData<ReportConfig>> {
    return this.http.get<PageData<ReportConfig>>(`/api/report/configs${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(c));
  }

  delete(id: string, c?: RequestConfig): Observable<void> {
    return this.http.delete<void>(`/api/report/config/${id}`, defaultHttpOptionsFromConfig(c));
  }

  runNow(id: string, c?: RequestConfig): Observable<void> {
    return this.http.post<void>(`/api/report/config/${id}/runNow`, {}, defaultHttpOptionsFromConfig(c));
  }
}
