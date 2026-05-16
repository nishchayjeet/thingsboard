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

export interface PayloadCodec {
  id?: { id: string };
  createdTime?: number;
  name: string;
  vendor?: string;
  model?: string;
  category?: string;
  description?: string;
  decoderType: 'JS' | 'TBEL';
  decoderFunction: string;
  sampleInput?: string;
  sampleOutput?: string;
  documentationUrl?: string;
  system?: boolean;
}

@Injectable({ providedIn: 'root' })
export class PayloadCodecService {

  constructor(private http: HttpClient) {}

  list(pageLink: PageLink, category?: string, vendor?: string, c?: RequestConfig): Observable<PageData<PayloadCodec>> {
    let params = new HttpParams();
    if (category) params = params.set('category', category);
    if (vendor) params = params.set('vendor', vendor);
    const sep = pageLink.toQuery().includes('?') ? '&' : '?';
    const query = pageLink.toQuery() + (params.toString() ? sep + params.toString() : '');
    return this.http.get<PageData<PayloadCodec>>(`/api/codec/library${query}`, defaultHttpOptionsFromConfig(c));
  }

  get(id: string, c?: RequestConfig): Observable<PayloadCodec> {
    return this.http.get<PayloadCodec>(`/api/codec/library/${id}`, defaultHttpOptionsFromConfig(c));
  }

  save(codec: PayloadCodec, c?: RequestConfig): Observable<PayloadCodec> {
    return this.http.post<PayloadCodec>('/api/codec/library', codec, defaultHttpOptionsFromConfig(c));
  }

  delete(id: string, c?: RequestConfig): Observable<void> {
    return this.http.delete<void>(`/api/codec/library/${id}`, defaultHttpOptionsFromConfig(c));
  }

  vendors(c?: RequestConfig): Observable<string[]> {
    return this.http.get<string[]>('/api/codec/library/vendors', defaultHttpOptionsFromConfig(c));
  }

  categories(c?: RequestConfig): Observable<string[]> {
    return this.http.get<string[]>('/api/codec/library/categories', defaultHttpOptionsFromConfig(c));
  }
}
