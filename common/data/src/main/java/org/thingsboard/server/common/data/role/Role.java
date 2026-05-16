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
package org.thingsboard.server.common.data.role;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.thingsboard.server.common.data.BaseData;
import org.thingsboard.server.common.data.HasName;
import org.thingsboard.server.common.data.HasTenantId;
import org.thingsboard.server.common.data.HasVersion;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.id.TenantId;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Role extends BaseData<RoleId> implements HasName, HasTenantId, HasVersion {

    public enum RoleType { GENERIC, GROUP }

    private TenantId tenantId;
    private CustomerId customerId;
    private String name;
    private RoleType type = RoleType.GENERIC;

    /**
     * Permissions JSON shape (GENERIC role):
     *   {
     *     "DEVICE":    ["READ", "WRITE"],
     *     "DASHBOARD": ["ALL"],
     *     "ALARM":     ["READ"]
     *   }
     * Resource names are the {@link org.thingsboard.server.service.security.permission.Resource}
     * enum values; operation names match {@link org.thingsboard.server.service.security.permission.Operation}.
     * Use "ALL" to grant every operation on that resource.
     *
     * Permissions JSON shape (GROUP role):
     *   {
     *     "operations": ["READ", "WRITE"]
     *   }
     * GROUP roles apply when the role is attached to an entity_group_permission row, granting the listed
     * operations on the referenced entities.
     */
    private JsonNode permissions;

    private String additionalInfo;
    private RoleId externalId;
    private Long version;

    public Role(RoleId id) {
        super(id);
    }
}
