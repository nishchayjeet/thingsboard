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

import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { PageComponent } from '@shared/components/page.component';
import { Converter, IntegrationService } from '@core/http/integration.service';
import { PageLink } from '@shared/models/page/page-link';

/**
 * PE-compatible Converters page. Uplink converters decode incoming integration
 * payloads into rule-engine messages; downlink converters encode outbound RPCs
 * back to the device's wire format. Scripts run in the rule engine using CE's
 * existing JsInvokeService.
 */
@Component({
  selector: 'tb-converters',
  templateUrl: './converters.component.html',
  styleUrls: ['./pe-pages.scss'],
  standalone: false
})
export class ConvertersComponent extends PageComponent implements OnInit {

  converters: Converter[] = [];
  selected: Converter | null = null;
  total = 0;
  form: FormGroup;

  readonly UPLINK_TEMPLATE = `// Uplink: decode payload bytes/JSON to ThingsBoard rule-engine messages.
function decodeUplink(payloadJson, metadata) {
  var p = JSON.parse(payloadJson);
  // …transform…
  return JSON.stringify({ temperature: p.t, humidity: p.h });
}`;

  readonly DOWNLINK_TEMPLATE = `// Downlink: encode an outbound rule-engine message to the device wire format.
function encodeDownlink(msgJson, metadata) {
  var m = JSON.parse(msgJson);
  return JSON.stringify({ payload: m, fPort: 1 });
}`;

  constructor(protected store: Store<AppState>,
              private fb: FormBuilder,
              private service: IntegrationService) {
    super(store);
    this.form = this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(255)]],
      type: ['UPLINK', Validators.required],
      scriptLang: ['JS'],
      decoder: [this.UPLINK_TEMPLATE, Validators.required],
      debugMode: [false],
      testInput: ['{"t": 21.3, "h": 48}'],
      testOutput: ['']
    });
  }

  ngOnInit() { this.load(); }

  load() {
    this.service.getConverters(new PageLink(100)).subscribe(p => {
      this.converters = p.data || []; this.total = p.totalElements;
    });
  }

  newConverter() {
    this.selected = null;
    this.form.reset({
      name: '', type: 'UPLINK', scriptLang: 'JS',
      decoder: this.UPLINK_TEMPLATE,
      debugMode: false, testInput: '{"t": 21.3, "h": 48}', testOutput: ''
    });
  }

  select(c: Converter) {
    this.selected = c;
    this.form.reset({
      name: c.name,
      type: c.type,
      scriptLang: c.configuration?.scriptLang || 'JS',
      decoder: c.type === 'UPLINK' ? (c.configuration?.decoder || this.UPLINK_TEMPLATE)
                                   : (c.configuration?.encoder || this.DOWNLINK_TEMPLATE),
      debugMode: c.debugMode || false,
      testInput: '', testOutput: ''
    });
  }

  onTypeChange(t: 'UPLINK' | 'DOWNLINK') {
    if (this.selected) return;
    this.form.patchValue({
      decoder: t === 'UPLINK' ? this.UPLINK_TEMPLATE : this.DOWNLINK_TEMPLATE
    });
  }

  save() {
    if (this.form.invalid) return;
    const v = this.form.value;
    const config: any = { scriptLang: v.scriptLang };
    if (v.type === 'UPLINK') config.decoder = v.decoder; else config.encoder = v.decoder;

    const payload: Converter = {
      ...(this.selected || {}),
      name: v.name, type: v.type, debugMode: v.debugMode,
      configuration: config
    } as Converter;

    this.service.saveConverter(payload).subscribe(() => {
      this.selected = null; this.load();
    });
  }

  remove(c: Converter, e: MouseEvent) {
    e.stopPropagation();
    if (!c.id || !confirm(`Delete converter "${c.name}"?`)) return;
    this.service.deleteConverter(c.id.id).subscribe(() => {
      if (this.selected?.id?.id === c.id?.id) this.selected = null;
      this.load();
    });
  }

  /** Test the script locally in the browser (best-effort). */
  testScript() {
    const v = this.form.value;
    try {
      // eslint-disable-next-line no-new-func
      const fn = new Function('payloadJson', 'metadata',
        v.decoder + (v.type === 'UPLINK'
          ? '\nreturn decodeUplink(payloadJson, metadata);'
          : '\nreturn encodeDownlink(payloadJson, metadata);'));
      const result = fn(v.testInput || '{}', {});
      this.form.patchValue({ testOutput: typeof result === 'string' ? result : JSON.stringify(result, null, 2) });
    } catch (e: any) {
      this.form.patchValue({ testOutput: 'ERROR: ' + e.message });
    }
  }
}
