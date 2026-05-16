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

import { Component, Input, OnDestroy, OnInit } from '@angular/core';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { PageComponent } from '@shared/components/page.component';
import { FormBuilder, FormGroup } from '@angular/forms';
import { HasConfirmForm } from '@core/guards/confirm-on-exit.guard';
import { WhiteLabelingService } from '@core/http/white-labeling.service';
import { WhiteLabelingParams } from '@shared/models/white-labeling.models';
import { Subject } from 'rxjs';
import { Authority } from '@shared/models/authority.enum';
import { getCurrentAuthState } from '@core/auth/auth.selectors';

@Component({
  selector: 'tb-white-labeling',
  templateUrl: './white-labeling.component.html',
  styleUrls: ['./settings-card.scss'],
  standalone: false
})
export class WhiteLabelingComponent extends PageComponent implements HasConfirmForm, OnInit, OnDestroy {

  @Input()
  scope: 'system' | 'tenant';

  whiteLabelingForm: FormGroup;
  private readonly destroy$ = new Subject<void>();

  constructor(protected store: Store<AppState>,
              private wlService: WhiteLabelingService,
              private fb: FormBuilder) {
    super(store);
    this.whiteLabelingForm = this.fb.group({
      appTitle: [''],
      logoImageUrl: [''],
      logoImageHeight: [null],
      faviconUrl: [''],
      primaryColor: [''],
      accentColor: [''],
      customCss: [''],
      helpLinkBaseUrl: [''],
      enableHelpLinks: [true]
    });
  }

  ngOnInit() {
    if (!this.scope) {
      const auth = getCurrentAuthState(this.store);
      this.scope = auth?.authUser?.authority === Authority.SYS_ADMIN ? 'system' : 'tenant';
    }
    const load$ = this.scope === 'system'
      ? this.wlService.getSystemWhiteLabelParams()
      : this.wlService.getTenantWhiteLabelParams();
    load$.subscribe(params => this.applyFormValue(params));
  }

  ngOnDestroy() {
    super.ngOnDestroy();
    this.destroy$.next();
    this.destroy$.complete();
  }

  save(): void {
    const params = this.collectFormValue();
    const save$ = this.scope === 'system'
      ? this.wlService.saveSystemWhiteLabelParams(params)
      : this.wlService.saveTenantWhiteLabelParams(params);
    save$.subscribe(saved => this.applyFormValue(saved));
  }

  discard(): void {
    const load$ = this.scope === 'system'
      ? this.wlService.getSystemWhiteLabelParams()
      : this.wlService.getTenantWhiteLabelParams();
    load$.subscribe(params => this.applyFormValue(params));
  }

  private collectFormValue(): WhiteLabelingParams {
    const v = this.whiteLabelingForm.value;
    return {
      appTitle: v.appTitle || null,
      logoImageUrl: v.logoImageUrl || null,
      logoImageHeight: v.logoImageHeight || null,
      favicon: v.faviconUrl ? { url: v.faviconUrl } : null,
      paletteSettings: (v.primaryColor || v.accentColor) ? {
        primaryPalette: v.primaryColor ? { type: 'custom', colorPrimary: v.primaryColor } : null,
        accentPalette: v.accentColor ? { type: 'custom', colorPrimary: v.accentColor, colorAccent: v.accentColor } : null
      } : null,
      customCss: v.customCss || null,
      helpLinkBaseUrl: v.helpLinkBaseUrl || null,
      enableHelpLinks: v.enableHelpLinks
    };
  }

  private applyFormValue(params: WhiteLabelingParams) {
    this.whiteLabelingForm.reset({
      appTitle: params?.appTitle ?? '',
      logoImageUrl: params?.logoImageUrl ?? '',
      logoImageHeight: params?.logoImageHeight ?? null,
      faviconUrl: params?.favicon?.url ?? '',
      primaryColor: params?.paletteSettings?.primaryPalette?.colorPrimary ?? '',
      accentColor: params?.paletteSettings?.accentPalette?.colorAccent
        ?? params?.paletteSettings?.accentPalette?.colorPrimary ?? '',
      customCss: params?.customCss ?? '',
      helpLinkBaseUrl: params?.helpLinkBaseUrl ?? '',
      enableHelpLinks: params?.enableHelpLinks ?? true
    });
  }

  confirmForm(): FormGroup {
    return this.whiteLabelingForm;
  }
}
