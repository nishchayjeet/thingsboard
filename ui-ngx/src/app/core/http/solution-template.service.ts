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
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { defaultHttpOptionsFromConfig, RequestConfig } from './http-utils';
import { PageData } from '@shared/models/page/page-data';
import { PageLink } from '@shared/models/page/page-link';

export interface SolutionTemplate {
  id?: { id: string };
  createdTime?: number;
  name: string;
  title?: string;
  category?: string;
  description?: string;
  previewImage?: string;
  bundle?: any;
  system?: boolean;
}

export interface InstallResult {
  templateId: { id: string };
  templateName: string;
  deviceProfileIds: string[];
  ruleChainIds: string[];
  dashboardIds: string[];
}

@Injectable({ providedIn: 'root' })
export class SolutionTemplateService {

  constructor(private http: HttpClient) {}

  list(pageLink: PageLink, category?: string, c?: RequestConfig): Observable<PageData<SolutionTemplate>> {
    let params = new HttpParams();
    if (category) params = params.set('category', category);
    const sep = pageLink.toQuery().includes('?') ? '&' : '?';
    const query = pageLink.toQuery() + (params.toString() ? sep + params.toString() : '');
    return this.http.get<PageData<SolutionTemplate>>(`/api/solution/templates${query}`, defaultHttpOptionsFromConfig(c));
  }

  get(id: string, c?: RequestConfig): Observable<SolutionTemplate> {
    return this.http.get<SolutionTemplate>(`/api/solution/template/${id}`, defaultHttpOptionsFromConfig(c));
  }

  install(id: string, c?: RequestConfig): Observable<InstallResult> {
    return this.http.post<InstallResult>(`/api/solution/template/${id}/install`, {}, defaultHttpOptionsFromConfig(c));
  }

  save(template: SolutionTemplate, c?: RequestConfig): Observable<SolutionTemplate> {
    return this.http.post<SolutionTemplate>('/api/solution/template', template, defaultHttpOptionsFromConfig(c));
  }

  delete(id: string, c?: RequestConfig): Observable<void> {
    return this.http.delete<void>(`/api/solution/template/${id}`, defaultHttpOptionsFromConfig(c));
  }
}
