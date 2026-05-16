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

export type IntegrationType =
  'HTTP' | 'MQTT' | 'AWS_IOT' | 'AZURE_IOT' | 'IBM_WATSON' |
  'KAFKA' | 'CHIRPSTACK' | 'THE_THINGS_NETWORK' | 'LORIOT' |
  'OPC_UA' | 'UDP' | 'TCP' | 'CUSTOM';

export const INTEGRATION_TYPE_LABELS: { value: IntegrationType; label: string; group: string }[] = [
  { value: 'HTTP',               label: 'HTTP webhook',           group: 'Generic' },
  { value: 'MQTT',               label: 'MQTT broker',            group: 'Generic' },
  { value: 'UDP',                label: 'UDP listener',           group: 'Generic' },
  { value: 'TCP',                label: 'TCP listener',           group: 'Generic' },
  { value: 'AWS_IOT',            label: 'AWS IoT Core',           group: 'Cloud' },
  { value: 'AZURE_IOT',          label: 'Azure IoT Hub',          group: 'Cloud' },
  { value: 'IBM_WATSON',         label: 'IBM Watson IoT',         group: 'Cloud' },
  { value: 'KAFKA',              label: 'Apache Kafka',           group: 'Cloud' },
  { value: 'CHIRPSTACK',         label: 'ChirpStack (LoRaWAN)',   group: 'LoRaWAN' },
  { value: 'THE_THINGS_NETWORK', label: 'The Things Network',     group: 'LoRaWAN' },
  { value: 'LORIOT',             label: 'Loriot',                 group: 'LoRaWAN' },
  { value: 'OPC_UA',             label: 'OPC-UA server',          group: 'Industrial' },
  { value: 'CUSTOM',             label: 'Custom',                 group: 'Other' }
];

export interface Integration {
  id?: { id: string };
  createdTime?: number;
  tenantId?: { id: string };
  name: string;
  routingKey: string;
  secret?: string;
  type: IntegrationType;
  enabled: boolean;
  remote?: boolean;
  allowCreateDevicesOrAssets?: boolean;
  defaultConverterId?: { id: string };
  downlinkConverterId?: { id: string };
  debugMode?: boolean;
  configuration: any;
  additionalInfo?: string;
}

export interface Converter {
  id?: { id: string };
  createdTime?: number;
  tenantId?: { id: string };
  name: string;
  type: 'UPLINK' | 'DOWNLINK';
  debugMode?: boolean;
  configuration: { scriptLang?: 'JS' | 'TBEL'; decoder?: string; encoder?: string };
  additionalInfo?: string;
}

@Injectable({ providedIn: 'root' })
export class IntegrationService {

  constructor(private http: HttpClient) {}

  // Integrations
  saveIntegration(i: Integration, c?: RequestConfig): Observable<Integration> {
    return this.http.post<Integration>('/api/integration', i, defaultHttpOptionsFromConfig(c));
  }
  getIntegration(id: string, c?: RequestConfig): Observable<Integration> {
    return this.http.get<Integration>(`/api/integration/${id}`, defaultHttpOptionsFromConfig(c));
  }
  getIntegrations(pageLink: PageLink, c?: RequestConfig): Observable<PageData<Integration>> {
    return this.http.get<PageData<Integration>>(`/api/integrations${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(c));
  }
  deleteIntegration(id: string, c?: RequestConfig): Observable<void> {
    return this.http.delete<void>(`/api/integration/${id}`, defaultHttpOptionsFromConfig(c));
  }

  // Converters
  saveConverter(v: Converter, c?: RequestConfig): Observable<Converter> {
    return this.http.post<Converter>('/api/converter', v, defaultHttpOptionsFromConfig(c));
  }
  getConverter(id: string, c?: RequestConfig): Observable<Converter> {
    return this.http.get<Converter>(`/api/converter/${id}`, defaultHttpOptionsFromConfig(c));
  }
  getConverters(pageLink: PageLink, c?: RequestConfig): Observable<PageData<Converter>> {
    return this.http.get<PageData<Converter>>(`/api/converters${pageLink.toQuery()}`, defaultHttpOptionsFromConfig(c));
  }
  deleteConverter(id: string, c?: RequestConfig): Observable<void> {
    return this.http.delete<void>(`/api/converter/${id}`, defaultHttpOptionsFromConfig(c));
  }
}
