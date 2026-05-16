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
package org.thingsboard.server.dao.role;

import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.role.Role;

import java.util.List;
import java.util.Set;

public interface RoleService {

    Role saveRole(Role role);

    Role findById(TenantId tenantId, RoleId id);

    PageData<Role> findByTenantId(TenantId tenantId, PageLink pageLink);

    void deleteById(TenantId tenantId, RoleId id);

    // --- User <-> Role assignment ---

    void assignRole(TenantId tenantId, UserId userId, RoleId roleId, CustomerId customerId);

    void revokeRole(TenantId tenantId, UserId userId, RoleId roleId);

    List<Role> findRolesForUser(TenantId tenantId, UserId userId);

    // --- Permission lookup ---

    /**
     * Returns the set of granted operation names for the given user against the given resource. Combines
     * GENERIC-role permissions (resource → operations map) plus GROUP-role grants matching {@code entityId}.
     * Returns an empty set if the user has no role grants.
     */
    Set<String> findGrantedOperations(TenantId tenantId, UserId userId, String resourceName, EntityId entityId);
}
