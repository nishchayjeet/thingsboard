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
import org.thingsboard.server.common.data.id.ConverterId;
import org.thingsboard.server.common.data.id.IntegrationId;
import org.thingsboard.server.common.data.integration.Converter;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.integration.IntegrationService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.integration.IntegrationManager;

import java.util.UUID;

@RestController
@TbCoreComponent
@Slf4j
@RequestMapping("/api")
@RequiredArgsConstructor
public class IntegrationController extends BaseController {

    private final IntegrationService integrationService;
    private final IntegrationManager integrationManager;

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @PostMapping("/integration")
    public Integration saveIntegration(@RequestBody Integration integration) throws ThingsboardException {
        integration.setTenantId(getTenantId());
        if (integration.getRoutingKey() == null || integration.getRoutingKey().isBlank()) {
            integration.setRoutingKey(UUID.randomUUID().toString());
        }
        Integration saved = integrationService.saveIntegration(integration);
        integrationManager.onSaved(saved);
        return saved;
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @GetMapping("/integration/{id}")
    public Integration getIntegrationById(@PathVariable("id") String id) throws ThingsboardException {
        return checkNotNull(integrationService.findById(getTenantId(), new IntegrationId(UUID.fromString(id))));
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @GetMapping("/integrations")
    public PageData<Integration> getIntegrations(@RequestParam int pageSize,
                                                 @RequestParam int page,
                                                 @RequestParam(required = false) String textSearch,
                                                 @RequestParam(required = false) String sortProperty,
                                                 @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return integrationService.findByTenantId(getTenantId(), pageLink);
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @DeleteMapping("/integration/{id}")
    public void deleteIntegration(@PathVariable("id") String id) throws ThingsboardException {
        IntegrationId iid = new IntegrationId(UUID.fromString(id));
        integrationManager.stop(iid);
        integrationService.deleteById(getTenantId(), iid);
    }

    // --- Converters ---

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @PostMapping("/converter")
    public Converter saveConverter(@RequestBody Converter converter) throws ThingsboardException {
        converter.setTenantId(getTenantId());
        return integrationService.saveConverter(converter);
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @GetMapping("/converter/{id}")
    public Converter getConverterById(@PathVariable("id") String id) throws ThingsboardException {
        return checkNotNull(integrationService.findConverterById(getTenantId(), new ConverterId(UUID.fromString(id))));
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @GetMapping("/converters")
    public PageData<Converter> getConverters(@RequestParam int pageSize,
                                             @RequestParam int page,
                                             @RequestParam(required = false) String textSearch,
                                             @RequestParam(required = false) String sortProperty,
                                             @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return integrationService.findConvertersByTenantId(getTenantId(), pageLink);
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @DeleteMapping("/converter/{id}")
    public void deleteConverter(@PathVariable("id") String id) throws ThingsboardException {
        integrationService.deleteConverterById(getTenantId(), new ConverterId(UUID.fromString(id)));
    }
}
