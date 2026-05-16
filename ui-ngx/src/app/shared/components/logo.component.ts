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
import { AuthService } from '@core/auth/auth.service';
import { AppState } from '@core/core.state';
import { Store } from '@ngrx/store';
import { UrlTree } from '@angular/router';
import { getCurrentAuthState } from '@core/auth/auth.selectors';
import { UrlHolder } from '@shared/pipe/image.pipe';
import { Subscription } from 'rxjs';
import { WhiteLabelingService } from '@core/http/white-labeling.service';
import { DEFAULT_LOGO_URL } from '@shared/models/white-labeling.models';

@Component({
    selector: 'tb-logo',
    templateUrl: './logo.component.html',
    styleUrls: ['./logo.component.scss'],
    standalone: false
})
export class LogoComponent implements OnInit, OnDestroy {

  @Input()
  src: string | UrlHolder;

  @Input()
  link: string | UrlTree;

  @Input()
  target: string = null;

  isExternal = false;

  private wlSub: Subscription;
  private srcExplicit = false;

  constructor(private authService: AuthService,
              private store: Store<AppState>,
              private whiteLabelingService: WhiteLabelingService) {
  }

  ngOnInit() {
    this.srcExplicit = this.src != null;
    if (!this.src) {
      this.src = this.whiteLabelingService.logoUrl || DEFAULT_LOGO_URL;
      this.wlSub = this.whiteLabelingService.current$.subscribe(params => {
        this.src = params?.logoImageUrl || DEFAULT_LOGO_URL;
      });
    }
    if (!this.link) {
      const authState = getCurrentAuthState(this.store);
      this.link = this.authService.defaultUrl(true, authState);
    }
    if (typeof this.link === 'string' && this.link.startsWith('http')) {
      this.isExternal = true;
    }
  }

  ngOnDestroy() {
    if (this.wlSub) {
      this.wlSub.unsubscribe();
    }
  }
}
