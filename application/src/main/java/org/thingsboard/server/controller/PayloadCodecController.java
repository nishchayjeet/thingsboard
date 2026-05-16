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
import org.thingsboard.server.common.data.codec.PayloadCodec;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.PayloadCodecId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.dao.codec.PayloadCodecService;
import org.thingsboard.server.queue.util.TbCoreComponent;

import java.util.List;
import java.util.UUID;

@RestController
@TbCoreComponent
@Slf4j
@RequestMapping("/api/codec")
@RequiredArgsConstructor
public class PayloadCodecController extends BaseController {

    private final PayloadCodecService codecService;

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @GetMapping("/library")
    public PageData<PayloadCodec> list(@RequestParam int pageSize,
                                       @RequestParam int page,
                                       @RequestParam(required = false) String category,
                                       @RequestParam(required = false) String vendor,
                                       @RequestParam(required = false) String textSearch,
                                       @RequestParam(required = false) String sortProperty,
                                       @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return codecService.findAll(getTenantId(), category, vendor, pageLink);
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @GetMapping("/library/{id}")
    public PayloadCodec getById(@PathVariable("id") String id) throws ThingsboardException {
        return checkNotNull(codecService.findById(new PayloadCodecId(UUID.fromString(id))));
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @PostMapping("/library")
    public PayloadCodec save(@RequestBody PayloadCodec codec) throws ThingsboardException {
        codec.setSystem(false);
        codec.setTenantId(getTenantId());
        return codecService.save(codec);
    }

    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @DeleteMapping("/library/{id}")
    public void delete(@PathVariable("id") String id) throws ThingsboardException {
        codecService.delete(new PayloadCodecId(UUID.fromString(id)));
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @GetMapping("/library/vendors")
    public List<String> vendors() {
        return codecService.listVendors();
    }

    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN', 'CUSTOMER_USER')")
    @GetMapping("/library/categories")
    public List<String> categories() {
        return codecService.listCategories();
    }
}
