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

export interface Role {
  id?: { id: string };
  createdTime?: number;
  tenantId?: { id: string };
  customerId?: { id: string };
  name: string;
  type: 'GENERIC' | 'GROUP';
  permissions: { [resource: string]: string[] } | { operations: string[] };
  additionalInfo?: string;
  version?: number;
}

@Injectable({ providedIn: 'root' })
export class RoleService {

  constructor(private http: HttpClient) {}

  public saveRole(role: Role, config?: RequestConfig): Observable<Role> {
    return this.http.post<Role>('/api/role', role, defaultHttpOptionsFromConfig(config));
  }

  public getRole(id: string, config?: RequestConfig): Observable<Role> {
    return this.http.get<Role>(`/api/role/${id}`, defaultHttpOptionsFromConfig(config));
  }

  public getRoles(pageLink: PageLink, config?: RequestConfig): Observable<PageData<Role>> {
    return this.http.get<PageData<Role>>(`/api/roles${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(config));
  }

  public deleteRole(id: string, config?: RequestConfig): Observable<void> {
    return this.http.delete<void>(`/api/role/${id}`, defaultHttpOptionsFromConfig(config));
  }

  public assignRole(userId: string, roleId: string, customerId?: string, config?: RequestConfig): Observable<void> {
    const query = customerId ? `?customerId=${customerId}` : '';
    return this.http.post<void>(`/api/user/${userId}/role/${roleId}${query}`, {}, defaultHttpOptionsFromConfig(config));
  }

  public revokeRole(userId: string, roleId: string, config?: RequestConfig): Observable<void> {
    return this.http.delete<void>(`/api/user/${userId}/role/${roleId}`, defaultHttpOptionsFromConfig(config));
  }

  public getUserRoles(userId: string, config?: RequestConfig): Observable<Role[]> {
    return this.http.get<Role[]>(`/api/user/${userId}/roles`, defaultHttpOptionsFromConfig(config));
  }
}
