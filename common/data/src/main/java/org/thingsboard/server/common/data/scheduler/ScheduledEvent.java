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
package org.thingsboard.server.common.data.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.thingsboard.server.common.data.BaseData;
import org.thingsboard.server.common.data.HasCustomerId;
import org.thingsboard.server.common.data.HasName;
import org.thingsboard.server.common.data.HasTenantId;
import org.thingsboard.server.common.data.HasVersion;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.ScheduledEventId;
import org.thingsboard.server.common.data.id.TenantId;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ScheduledEvent extends BaseData<ScheduledEventId> implements HasName, HasTenantId, HasCustomerId, HasVersion {

    private TenantId tenantId;
    private CustomerId customerId;
    private String name;
    private String type;
    private JsonNode schedule;
    private JsonNode configuration;
    private EntityId originatorId;
    private boolean enabled = true;
    private Long nextFireTime;
    private Long lastFireTime;
    private String additionalInfo;
    private ScheduledEventId externalId;
    private Long version;

    public ScheduledEvent(ScheduledEventId id) {
        super(id);
    }

    public ScheduledEvent(ScheduledEvent other) {
        super(other);
        this.tenantId = other.tenantId;
        this.customerId = other.customerId;
        this.name = other.name;
        this.type = other.type;
        this.schedule = other.schedule;
        this.configuration = other.configuration;
        this.originatorId = other.originatorId;
        this.enabled = other.enabled;
        this.nextFireTime = other.nextFireTime;
        this.lastFireTime = other.lastFireTime;
        this.additionalInfo = other.additionalInfo;
        this.externalId = other.externalId;
        this.version = other.version;
    }
}
