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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.common.data.exception.ThingsboardException;
import org.thingsboard.server.common.data.id.ScheduledEventId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.scheduler.ScheduledEvent;
import org.thingsboard.server.config.annotations.ApiOperation;
import org.thingsboard.server.dao.scheduler.ScheduledEventService;
import org.thingsboard.server.dao.scheduler.ScheduleEvaluator;
import org.thingsboard.server.queue.util.TbCoreComponent;

import java.util.UUID;

@RestController
@TbCoreComponent
@Slf4j
@RequestMapping("/api/scheduler")
@RequiredArgsConstructor
public class ScheduledEventController extends BaseController {

    private final ScheduledEventService scheduledEventService;

    @ApiOperation(value = "Create or update a scheduled event")
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @PostMapping("/event")
    public ScheduledEvent saveScheduledEvent(@RequestBody ScheduledEvent event) throws ThingsboardException {
        event.setTenantId(getTenantId());
        if (event.getSchedule() != null) {
            Long nextFire = ScheduleEvaluator.computeNextFireTime(event.getSchedule(), System.currentTimeMillis());
            event.setNextFireTime(nextFire);
        }
        return scheduledEventService.saveScheduledEvent(event);
    }

    @ApiOperation(value = "Get a scheduled event by id")
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @GetMapping("/event/{id}")
    public ScheduledEvent getScheduledEventById(@PathVariable("id") String id) throws ThingsboardException {
        ScheduledEvent event = scheduledEventService.findById(getTenantId(), new ScheduledEventId(UUID.fromString(id)));
        return checkNotNull(event);
    }

    @ApiOperation(value = "List scheduled events for the current tenant")
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @GetMapping("/events")
    public PageData<ScheduledEvent> getScheduledEvents(
            @Parameter(description = "Maximum number of items per page.") @RequestParam int pageSize,
            @Parameter(description = "Page index, starting from 0.") @RequestParam int page,
            @Parameter(description = "Free-text search.") @RequestParam(required = false) String textSearch,
            @Parameter(description = "Sort property.") @RequestParam(required = false) String sortProperty,
            @Parameter(description = "Sort order: ASC or DESC.") @RequestParam(required = false) String sortOrder)
            throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return scheduledEventService.findByTenantId(getTenantId(), pageLink);
    }

    @ApiOperation(value = "Delete a scheduled event")
    @PreAuthorize("hasAnyAuthority('TENANT_ADMIN')")
    @DeleteMapping("/event/{id}")
    public void deleteScheduledEvent(@PathVariable("id") String id) throws ThingsboardException {
        scheduledEventService.deleteById(getTenantId(), new ScheduledEventId(UUID.fromString(id)));
    }
}
