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
import { BehaviorSubject, Observable, of } from 'rxjs';
import { catchError, tap } from 'rxjs/operators';
import { defaultHttpOptionsFromConfig, RequestConfig } from './http-utils';
import {
  DEFAULT_APP_TITLE,
  DEFAULT_FAVICON_URL,
  DEFAULT_LOGO_URL,
  LoginWhiteLabelingParams,
  WhiteLabelingParams
} from '@shared/models/white-labeling.models';

@Injectable({
  providedIn: 'root'
})
export class WhiteLabelingService {

  private currentParams$ = new BehaviorSubject<WhiteLabelingParams>({});

  constructor(private http: HttpClient) {}

  public get current(): WhiteLabelingParams {
    return this.currentParams$.value;
  }

  public get current$(): Observable<WhiteLabelingParams> {
    return this.currentParams$.asObservable();
  }

  public get logoUrl(): string {
    return this.currentParams$.value?.logoImageUrl || DEFAULT_LOGO_URL;
  }

  public loadLoginWhiteLabel(): Observable<LoginWhiteLabelingParams> {
    return this.http.get<LoginWhiteLabelingParams>('/api/noauth/whiteLabel/loginWhiteLabelParams').pipe(
      tap(params => this.apply(params)),
      catchError(() => of({} as LoginWhiteLabelingParams))
    );
  }

  public loadCurrentWhiteLabel(): Observable<WhiteLabelingParams> {
    return this.http.get<WhiteLabelingParams>('/api/noauth/whiteLabel/whiteLabelParams').pipe(
      tap(params => this.apply(params)),
      catchError(() => of({} as WhiteLabelingParams))
    );
  }

  public getSystemWhiteLabelParams(config?: RequestConfig): Observable<WhiteLabelingParams> {
    return this.http.get<WhiteLabelingParams>('/api/whiteLabel/systemWhiteLabelParams', defaultHttpOptionsFromConfig(config));
  }

  public saveSystemWhiteLabelParams(params: WhiteLabelingParams, config?: RequestConfig): Observable<WhiteLabelingParams> {
    return this.http.post<WhiteLabelingParams>('/api/whiteLabel/systemWhiteLabelParams', params, defaultHttpOptionsFromConfig(config))
      .pipe(tap(saved => this.apply(saved)));
  }

  public getTenantWhiteLabelParams(config?: RequestConfig): Observable<WhiteLabelingParams> {
    return this.http.get<WhiteLabelingParams>('/api/whiteLabel/tenantWhiteLabelParams', defaultHttpOptionsFromConfig(config));
  }

  public saveTenantWhiteLabelParams(params: WhiteLabelingParams, config?: RequestConfig): Observable<WhiteLabelingParams> {
    return this.http.post<WhiteLabelingParams>('/api/whiteLabel/tenantWhiteLabelParams', params, defaultHttpOptionsFromConfig(config))
      .pipe(tap(saved => this.apply(saved)));
  }

  public getSystemLoginWhiteLabelParams(config?: RequestConfig): Observable<LoginWhiteLabelingParams> {
    return this.http.get<LoginWhiteLabelingParams>('/api/whiteLabel/systemLoginWhiteLabelParams', defaultHttpOptionsFromConfig(config));
  }

  public saveSystemLoginWhiteLabelParams(params: LoginWhiteLabelingParams, config?: RequestConfig): Observable<LoginWhiteLabelingParams> {
    return this.http.post<LoginWhiteLabelingParams>('/api/whiteLabel/systemLoginWhiteLabelParams', params,
      defaultHttpOptionsFromConfig(config)).pipe(tap(saved => this.apply(saved)));
  }

  private apply(params: WhiteLabelingParams) {
    this.currentParams$.next(params || {});
    this.applyAppTitle(params?.appTitle);
    this.applyFavicon(params?.favicon?.url, params?.favicon?.type);
    this.applyPalette(params?.paletteSettings);
    this.applyCustomCss(params?.customCss);
  }

  private applyAppTitle(title?: string) {
    document.title = title && title.trim() ? title : DEFAULT_APP_TITLE;
  }

  private applyFavicon(url?: string, type?: string) {
    const href = url && url.trim() ? url : DEFAULT_FAVICON_URL;
    let link = document.querySelector<HTMLLinkElement>('link[rel~="icon"]');
    if (!link) {
      link = document.createElement('link');
      link.rel = 'icon';
      document.head.appendChild(link);
    }
    if (type) {
      link.type = type;
    }
    link.href = href;
  }

  private applyPalette(palette?: WhiteLabelingParams['paletteSettings']) {
    const root = document.documentElement;
    const primary = palette?.primaryPalette?.colorPrimary;
    const accent = palette?.accentPalette?.colorAccent ?? palette?.accentPalette?.colorPrimary;
    if (primary) {
      root.style.setProperty('--tb-primary-color', primary);
    } else {
      root.style.removeProperty('--tb-primary-color');
    }
    if (accent) {
      root.style.setProperty('--tb-accent-color', accent);
    } else {
      root.style.removeProperty('--tb-accent-color');
    }
  }

  private applyCustomCss(css?: string) {
    const STYLE_ID = 'tb-white-label-custom-css';
    let style = document.getElementById(STYLE_ID) as HTMLStyleElement | null;
    if (css && css.trim()) {
      if (!style) {
        style = document.createElement('style');
        style.id = STYLE_ID;
        document.head.appendChild(style);
      }
      style.textContent = css;
    } else if (style) {
      style.remove();
    }
  }
}
