/**
 * Copyright © 2016-2026 The Thingsboard Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.thingsboard.server.dao.wl;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.AdminSettings;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.wl.LoginWhiteLabelingParams;
import org.thingsboard.server.common.data.wl.WhiteLabelingParams;
import org.thingsboard.server.dao.settings.AdminSettingsService;

@Service
@RequiredArgsConstructor
@Slf4j
public class WhiteLabelingServiceImpl implements WhiteLabelingService {

    public static final String WHITE_LABEL_PARAMS_KEY = "whiteLabelParams";
    public static final String LOGIN_WHITE_LABEL_PARAMS_KEY = "loginWhiteLabelParams";

    private static final WhiteLabelingParams EMPTY_WL = new WhiteLabelingParams();
    private static final LoginWhiteLabelingParams EMPTY_LOGIN_WL = new LoginWhiteLabelingParams();

    private final AdminSettingsService adminSettingsService;

    @Override
    public WhiteLabelingParams getSystemWhiteLabelingParams() {
        return readParams(TenantId.SYS_TENANT_ID, WHITE_LABEL_PARAMS_KEY, WhiteLabelingParams.class, EMPTY_WL);
    }

    @Override
    public WhiteLabelingParams getTenantWhiteLabelingParams(TenantId tenantId) {
        return readParams(tenantId, WHITE_LABEL_PARAMS_KEY, WhiteLabelingParams.class, EMPTY_WL);
    }

    @Override
    public WhiteLabelingParams getMergedSystemWhiteLabelingParams() {
        return getSystemWhiteLabelingParams();
    }

    @Override
    public WhiteLabelingParams getMergedTenantWhiteLabelingParams(TenantId tenantId) {
        WhiteLabelingParams system = getSystemWhiteLabelingParams();
        WhiteLabelingParams tenant = getTenantWhiteLabelingParams(tenantId);
        return merge(system, tenant);
    }

    @Override
    public LoginWhiteLabelingParams getSystemLoginWhiteLabelingParams() {
        return readParams(TenantId.SYS_TENANT_ID, LOGIN_WHITE_LABEL_PARAMS_KEY, LoginWhiteLabelingParams.class, EMPTY_LOGIN_WL);
    }

    @Override
    public LoginWhiteLabelingParams getMergedLoginWhiteLabelingParams(String domainName) {
        // CE single-domain stub: returns system-level login WL. PE has per-domain mapping.
        return getSystemLoginWhiteLabelingParams();
    }

    @Override
    public WhiteLabelingParams saveSystemWhiteLabelingParams(WhiteLabelingParams params) {
        return saveParams(TenantId.SYS_TENANT_ID, WHITE_LABEL_PARAMS_KEY, params);
    }

    @Override
    public WhiteLabelingParams saveTenantWhiteLabelingParams(TenantId tenantId, WhiteLabelingParams params) {
        return saveParams(tenantId, WHITE_LABEL_PARAMS_KEY, params);
    }

    @Override
    public LoginWhiteLabelingParams saveSystemLoginWhiteLabelingParams(LoginWhiteLabelingParams params) {
        return saveParams(TenantId.SYS_TENANT_ID, LOGIN_WHITE_LABEL_PARAMS_KEY, params);
    }

    private <T> T readParams(TenantId tenantId, String key, Class<T> cls, T fallback) {
        AdminSettings settings = adminSettingsService.findAdminSettingsByTenantIdAndKey(tenantId, key);
        if (settings == null || settings.getJsonValue() == null) {
            return fallback;
        }
        try {
            return JacksonUtil.treeToValue(settings.getJsonValue(), cls);
        } catch (Exception e) {
            log.warn("Failed to parse {} for tenant {}: {}", key, tenantId, e.getMessage());
            return fallback;
        }
    }

    private <T> T saveParams(TenantId tenantId, String key, T params) {
        AdminSettings existing = adminSettingsService.findAdminSettingsByTenantIdAndKey(tenantId, key);
        if (existing == null) {
            existing = new AdminSettings();
            existing.setTenantId(tenantId);
            existing.setKey(key);
        }
        JsonNode json = JacksonUtil.valueToTree(params);
        existing.setJsonValue(json);
        adminSettingsService.saveAdminSettings(tenantId, existing);
        return params;
    }

    private WhiteLabelingParams merge(WhiteLabelingParams base, WhiteLabelingParams over) {
        WhiteLabelingParams merged = new WhiteLabelingParams();
        merged.setLogoImageUrl(firstNonNull(over.getLogoImageUrl(), base.getLogoImageUrl()));
        merged.setLogoImageHeight(firstNonNull(over.getLogoImageHeight(), base.getLogoImageHeight()));
        merged.setAppTitle(firstNonNull(over.getAppTitle(), base.getAppTitle()));
        merged.setFavicon(firstNonNull(over.getFavicon(), base.getFavicon()));
        merged.setPaletteSettings(firstNonNull(over.getPaletteSettings(), base.getPaletteSettings()));
        merged.setHelpLinkBaseUrl(firstNonNull(over.getHelpLinkBaseUrl(), base.getHelpLinkBaseUrl()));
        merged.setEnableHelpLinks(firstNonNull(over.getEnableHelpLinks(), base.getEnableHelpLinks()));
        merged.setShowNameVersion(firstNonNull(over.getShowNameVersion(), base.getShowNameVersion()));
        merged.setPlatformName(firstNonNull(over.getPlatformName(), base.getPlatformName()));
        merged.setPlatformVersion(firstNonNull(over.getPlatformVersion(), base.getPlatformVersion()));
        merged.setCustomCss(firstNonNull(over.getCustomCss(), base.getCustomCss()));
        return merged;
    }

    private static <T> T firstNonNull(T a, T b) {
        return a != null ? a : b;
    }
}
