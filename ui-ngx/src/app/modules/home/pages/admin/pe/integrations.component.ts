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

import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { PageComponent } from '@shared/components/page.component';
import {
  Converter, INTEGRATION_TYPE_LABELS, Integration, IntegrationService, IntegrationType
} from '@core/http/integration.service';
import { PageLink } from '@shared/models/page/page-link';
import { forkJoin, Subject } from 'rxjs';

/**
 * PE-compatible Integrations page following the doc's 4-step flow (Basic Settings →
 * Uplink Converter → Downlink Converter → Connection Settings), rendered as four
 * collapsible sections so we don't need a wizard component.
 */
@Component({
  selector: 'tb-integrations',
  templateUrl: './integrations.component.html',
  styleUrls: ['./pe-pages.scss'],
  standalone: false
})
export class IntegrationsComponent extends PageComponent implements OnInit, OnDestroy {

  readonly TYPES = INTEGRATION_TYPE_LABELS;
  readonly TYPE_GROUPS = ['Generic', 'Cloud', 'LoRaWAN', 'Industrial', 'Other'];

  integrations: Integration[] = [];
  converters: Converter[] = [];
  selected: Integration | null = null;
  totalIntegrations = 0;
  form: FormGroup;

  private destroy$ = new Subject<void>();

  constructor(protected store: Store<AppState>,
              private fb: FormBuilder,
              private service: IntegrationService) {
    super(store);
    this.form = this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(255)]],
      type: ['HTTP', Validators.required],
      routingKey: [''],
      secret: [''],
      enabled: [true],
      allowCreateDevicesOrAssets: [true],
      debugMode: [false],
      defaultConverterId: [''],
      downlinkConverterId: [''],
      configuration: ['{\n  "host": "broker.example.com",\n  "port": 1883,\n  "topics": ["#"]\n}',
        Validators.required]
    });
  }

  ngOnInit() { this.load(); }

  ngOnDestroy() {
    super.ngOnDestroy();
    this.destroy$.next(); this.destroy$.complete();
  }

  load() {
    forkJoin({
      integrations: this.service.getIntegrations(new PageLink(100)),
      converters:   this.service.getConverters(new PageLink(100))
    }).subscribe(({ integrations, converters }) => {
      this.integrations = integrations.data || [];
      this.totalIntegrations = integrations.totalElements;
      this.converters = converters.data || [];
    });
  }

  newIntegration() {
    this.selected = null;
    this.form.reset({
      name: '', type: 'HTTP', routingKey: '', secret: '',
      enabled: true, allowCreateDevicesOrAssets: true, debugMode: false,
      defaultConverterId: '', downlinkConverterId: '',
      configuration: this.defaultConfig('HTTP')
    });
  }

  select(integration: Integration) {
    this.selected = integration;
    this.form.reset({
      name: integration.name,
      type: integration.type,
      routingKey: integration.routingKey,
      secret: integration.secret || '',
      enabled: integration.enabled,
      allowCreateDevicesOrAssets: integration.allowCreateDevicesOrAssets ?? true,
      debugMode: integration.debugMode ?? false,
      defaultConverterId: integration.defaultConverterId?.id || '',
      downlinkConverterId: integration.downlinkConverterId?.id || '',
      configuration: JSON.stringify(integration.configuration || {}, null, 2)
    });
  }

  onTypeChange(t: IntegrationType) {
    if (!this.selected) {
      this.form.patchValue({ configuration: this.defaultConfig(t) });
    }
  }

  uplinkConverters(): Converter[]   { return this.converters.filter(c => c.type === 'UPLINK'); }
  downlinkConverters(): Converter[] { return this.converters.filter(c => c.type === 'DOWNLINK'); }

  save() {
    if (this.form.invalid) return;
    let config: any = {};
    try { config = JSON.parse(this.form.value.configuration || '{}'); }
    catch (e) { alert('Connection settings is not valid JSON'); return; }

    const payload: Integration = {
      ...(this.selected || {}),
      name: this.form.value.name,
      type: this.form.value.type,
      routingKey: this.form.value.routingKey,
      secret: this.form.value.secret || undefined,
      enabled: this.form.value.enabled,
      allowCreateDevicesOrAssets: this.form.value.allowCreateDevicesOrAssets,
      debugMode: this.form.value.debugMode,
      defaultConverterId: this.form.value.defaultConverterId ? { id: this.form.value.defaultConverterId } : undefined,
      downlinkConverterId: this.form.value.downlinkConverterId ? { id: this.form.value.downlinkConverterId } : undefined,
      configuration: config
    } as Integration;

    this.service.saveIntegration(payload).subscribe(() => {
      this.selected = null;
      this.load();
    });
  }

  toggle(integration: Integration, event: MouseEvent) {
    event.stopPropagation();
    const updated = { ...integration, enabled: !integration.enabled };
    this.service.saveIntegration(updated).subscribe(() => this.load());
  }

  remove(integration: Integration, event: MouseEvent) {
    event.stopPropagation();
    if (!integration.id || !confirm(`Delete integration "${integration.name}"?`)) return;
    this.service.deleteIntegration(integration.id.id).subscribe(() => {
      if (this.selected?.id?.id === integration.id?.id) this.selected = null;
      this.load();
    });
  }

  typeLabel(t: IntegrationType): string {
    return this.TYPES.find(x => x.value === t)?.label || t;
  }

  groupedTypes(group: string) {
    return this.TYPES.filter(t => t.group === group);
  }

  private defaultConfig(t: IntegrationType): string {
    switch (t) {
      case 'HTTP':
        return '{\n  "/* webhook URL: */ ": "/api/noauth/integrations/http/<routingKey>"\n}';
      case 'MQTT':
      case 'AWS_IOT':
      case 'AZURE_IOT':
      case 'IBM_WATSON':
      case 'CHIRPSTACK':
      case 'THE_THINGS_NETWORK':
      case 'LORIOT':
        return JSON.stringify({
          host: 'broker.example.com', port: 1883, ssl: false,
          clientId: 'tb-integration-' + Math.random().toString(36).slice(2, 10),
          username: '', password: '', topics: ['#']
        }, null, 2);
      case 'KAFKA':
        return JSON.stringify({
          bootstrapServers: 'kafka:9092', groupId: 'tb-integration',
          topics: ['device-uplink'], autoOffsetReset: 'earliest'
        }, null, 2);
      case 'OPC_UA':
        return JSON.stringify({
          endpointUrl: 'opc.tcp://server:4840', securityPolicy: 'None', namespaceUri: '',
          nodeIds: []
        }, null, 2);
      default:
        return '{}';
    }
  }
}
