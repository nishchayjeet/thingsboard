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
package org.thingsboard.server.controller;

import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.wl.LoginWhiteLabelingParams;
import org.thingsboard.server.common.data.wl.WhiteLabelingParams;
import org.thingsboard.server.config.annotations.ApiOperation;
import org.thingsboard.server.dao.wl.WhiteLabelingService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.permission.Operation;
import org.thingsboard.server.service.security.permission.Resource;

@RestController
@TbCoreComponent
@Slf4j
@RequestMapping("/api")
@RequiredArgsConstructor
public class WhiteLabelingController extends BaseController {

    private final WhiteLabelingService whiteLabelingService;

    // ----- Public endpoints used by the login page and pre-auth UI bootstrap -----

    @ApiOperation(value = "Get login page white-labeling parameters (public)",
            notes = "Returns logo, favicon, page title, and palette to render the login page. " +
                    "This endpoint is intentionally unauthenticated so the login page can paint before the user signs in.")
    @GetMapping("/noauth/whiteLabel/loginWhiteLabelParams")
    public LoginWhiteLabelingParams getLoginWhiteLabelParams(
            @Parameter(description = "Optional logo image checksum to skip transfer on cache hit.")
            @RequestParam(required = false) String logoImageChecksum,
            @Parameter(description = "Optional favicon checksum to skip transfer on cache hit.")
            @RequestParam(required = false) String faviconChecksum,
            @Parameter(description = "Host header forwarded from the browser; used for per-domain lookup.")
            @RequestParam(required = false) String domainName) {
        return whiteLabelingService.getMergedLoginWhiteLabelingParams(domainName);
    }

    @ApiOperation(value = "Get current white-labeling parameters for the active user (public)",
            notes = "Returns the merged system + tenant white-labeling parameters for use by the shell after login. " +
                    "Anonymous callers receive system-level parameters only.")
    @GetMapping("/noauth/whiteLabel/whiteLabelParams")
    public WhiteLabelingParams getCurrentWhiteLabelParams() {
        try {
            return whiteLabelingService.getMergedTenantWhiteLabelingParams(getTenantId());
        } catch (Exception ignored) {
            return whiteLabelingService.getMergedSystemWhiteLabelingParams();
        }
    }

    // ----- Authenticated endpoints (system + tenant scope) -----

    @ApiOperation(value = "Get system-level white-labeling parameters")
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/whiteLabel/systemWhiteLabelParams")
    public WhiteLabelingParams getSystemWhiteLabelParams() throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), Resource.ADMIN_SETTINGS, Operation.READ);
        return whiteLabelingService.getSystemWhiteLabelingParams();
    }

    @ApiOperation(value = "Save system-level white-labeling parameters")
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping("/whiteLabel/systemWhiteLabelParams")
    public WhiteLabelingParams saveSystemWhiteLabelParams(@RequestBody WhiteLabelingParams params) throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), Resource.ADMIN_SETTINGS, Operation.WRITE);
        return whiteLabelingService.saveSystemWhiteLabelingParams(params);
    }

    @ApiOperation(value = "Get tenant-level white-labeling parameters")
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @GetMapping("/whiteLabel/tenantWhiteLabelParams")
    public WhiteLabelingParams getTenantWhiteLabelParams() throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), Resource.ADMIN_SETTINGS, Operation.READ);
        return whiteLabelingService.getTenantWhiteLabelingParams(getTenantId());
    }

    @ApiOperation(value = "Save tenant-level white-labeling parameters")
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @PostMapping("/whiteLabel/tenantWhiteLabelParams")
    public WhiteLabelingParams saveTenantWhiteLabelParams(@RequestBody WhiteLabelingParams params) throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), Resource.ADMIN_SETTINGS, Operation.WRITE);
        return whiteLabelingService.saveTenantWhiteLabelingParams(getTenantId(), params);
    }

    @ApiOperation(value = "Get system-level login white-labeling parameters")
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @GetMapping("/whiteLabel/systemLoginWhiteLabelParams")
    public LoginWhiteLabelingParams getSystemLoginWhiteLabelParams() throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), Resource.ADMIN_SETTINGS, Operation.READ);
        return whiteLabelingService.getSystemLoginWhiteLabelingParams();
    }

    @ApiOperation(value = "Save system-level login white-labeling parameters")
    @PreAuthorize("hasAuthority('SYS_ADMIN')")
    @PostMapping("/whiteLabel/systemLoginWhiteLabelParams")
    public LoginWhiteLabelingParams saveSystemLoginWhiteLabelParams(@RequestBody LoginWhiteLabelingParams params) throws ThingsboardException {
        accessControlService.checkPermission(getCurrentUser(), Resource.ADMIN_SETTINGS, Operation.WRITE);
        return whiteLabelingService.saveSystemLoginWhiteLabelingParams(params);
    }
}
