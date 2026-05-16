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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.ReportConfigId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.report.ReportConfig;
import org.thingsboard.server.dao.report.ReportConfigService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.report.ReportRunner;

import java.util.UUID;

@RestController
@TbCoreComponent
@Slf4j
@RequestMapping("/api/report")
@RequiredArgsConstructor
public class ReportController extends BaseController {

    private final ReportConfigService reportConfigService;
    private final ReportRunner reportRunner;

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @PostMapping("/config")
    public ReportConfig saveReport(@RequestBody ReportConfig report) throws ThingsboardException {
        report.setTenantId(getTenantId());
        return reportConfigService.save(report);
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @GetMapping("/config/{id}")
    public ReportConfig getReportById(@PathVariable("id") String id) throws ThingsboardException {
        return checkNotNull(reportConfigService.findById(getTenantId(), new ReportConfigId(UUID.fromString(id))));
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @GetMapping("/configs")
    public PageData<ReportConfig> getReports(@RequestParam int pageSize,
                                             @RequestParam int page,
                                             @RequestParam(required = false) String textSearch,
                                             @RequestParam(required = false) String sortProperty,
                                             @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return reportConfigService.findByTenantId(getTenantId(), pageLink);
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @DeleteMapping("/config/{id}")
    public void deleteReport(@PathVariable("id") String id) throws ThingsboardException {
        reportConfigService.delete(getTenantId(), new ReportConfigId(UUID.fromString(id)));
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @PostMapping("/config/{id}/runNow")
    public void runNow(@PathVariable("id") String id) throws ThingsboardException {
        reportRunner.run(getTenantId(), new ReportConfigId(UUID.fromString(id)));
    }
}
