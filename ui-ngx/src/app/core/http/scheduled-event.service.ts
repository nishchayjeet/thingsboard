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

export interface ScheduleConfig {
  startTime?: number;
  endsOn?: number;
  timezone?: string;
  repeat?: 'NONE' | 'MINUTELY' | 'HOURLY' | 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY' | 'TIMER' | 'CRON';
  repeatIntervalMs?: number;
  cron?: string;
}

export interface ScheduledEvent {
  id?: { id: string };
  createdTime?: number;
  tenantId?: { id: string };
  customerId?: { id: string };
  name: string;
  type: string;
  schedule: ScheduleConfig;
  configuration: any;
  originatorId?: { id: string; entityType: string };
  enabled: boolean;
  nextFireTime?: number;
  lastFireTime?: number;
  additionalInfo?: string;
  version?: number;
}

@Injectable({ providedIn: 'root' })
export class ScheduledEventService {

  constructor(private http: HttpClient) {}

  public saveScheduledEvent(event: ScheduledEvent, config?: RequestConfig): Observable<ScheduledEvent> {
    return this.http.post<ScheduledEvent>('/api/scheduler/event', event, defaultHttpOptionsFromConfig(config));
  }

  public getScheduledEvent(id: string, config?: RequestConfig): Observable<ScheduledEvent> {
    return this.http.get<ScheduledEvent>(`/api/scheduler/event/${id}`, defaultHttpOptionsFromConfig(config));
  }

  public getScheduledEvents(pageLink: PageLink, config?: RequestConfig): Observable<PageData<ScheduledEvent>> {
    return this.http.get<PageData<ScheduledEvent>>(
      `/api/scheduler/events${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public deleteScheduledEvent(id: string, config?: RequestConfig): Observable<void> {
    return this.http.delete<void>(`/api/scheduler/event/${id}`, defaultHttpOptionsFromConfig(config));
  }
}
