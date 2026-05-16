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
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { PageComponent } from '@shared/components/page.component';
import { PayloadCodec, PayloadCodecService } from '@core/http/payload-codec.service';
import { PageLink } from '@shared/models/page/page-link';
import { forkJoin } from 'rxjs';

/**
 * PE-compatible Device Library page. Browse by vendor/category, click a codec
 * to see its decoder + sample input/output. "Use in device profile" is a hint
 * action — the actual attachment is done on the device profile page (clone the
 * decoder body into the profile's transformer config).
 */
@Component({
  selector: 'tb-codec-library',
  templateUrl: './codec-library.component.html',
  styleUrls: ['./pe-pages.scss'],
  standalone: false
})
export class CodecLibraryComponent extends PageComponent implements OnInit {

  codecs: PayloadCodec[] = [];
  vendors: string[] = [];
  categories: string[] = [];
  total = 0;

  vendorFilter = '';
  categoryFilter = '';
  textFilter = '';

  selected: PayloadCodec | null = null;

  constructor(protected store: Store<AppState>,
              private service: PayloadCodecService) {
    super(store);
  }

  ngOnInit() {
    forkJoin({
      vendors: this.service.vendors(),
      categories: this.service.categories()
    }).subscribe(({ vendors, categories }) => {
      this.vendors = vendors; this.categories = categories;
    });
    this.load();
  }

  load() {
    this.service.list(new PageLink(200),
      this.categoryFilter || undefined,
      this.vendorFilter || undefined).subscribe(p => {
      this.codecs = (p.data || []).filter(c => !this.textFilter ||
        c.name.toLowerCase().includes(this.textFilter.toLowerCase()) ||
        (c.vendor || '').toLowerCase().includes(this.textFilter.toLowerCase()));
      this.total = p.totalElements;
    });
  }

  select(c: PayloadCodec) {
    this.selected = c;
  }

  copyDecoder(c: PayloadCodec) {
    navigator.clipboard.writeText(c.decoderFunction || '').then(() => alert('Decoder copied. Paste it into the device profile script transformer.'));
  }
}
