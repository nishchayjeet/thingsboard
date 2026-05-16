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

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.role.Role;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class RoleServiceImpl implements RoleService {

    private final JdbcTemplate jdbc;

    private final RowMapper<Role> roleMapper = (rs, i) -> mapRole(rs);

    @Override
    public Role saveRole(Role role) {
        if (role.getId() == null) {
            role.setId(new RoleId(UUID.randomUUID()));
            role.setCreatedTime(System.currentTimeMillis());
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO role (id, created_time, tenant_id, customer_id, name, type, permissions, additional_info, version) " +
                                "VALUES (?,?,?,?,?,?,?::jsonb,?,?)");
                ps.setObject(1, role.getId().getId());
                ps.setLong(2, role.getCreatedTime());
                ps.setObject(3, role.getTenantId().getId());
                ps.setObject(4, role.getCustomerId() != null ? role.getCustomerId().getId() : null, Types.OTHER);
                ps.setString(5, role.getName());
                ps.setString(6, role.getType() != null ? role.getType().name() : "GENERIC");
                ps.setString(7, role.getPermissions() != null ? JacksonUtil.toString(role.getPermissions()) : "{}");
                ps.setString(8, role.getAdditionalInfo());
                ps.setLong(9, 1L);
                return ps;
            });
            role.setVersion(1L);
        } else {
            jdbc.update("UPDATE role SET name=?, type=?, permissions=?::jsonb, additional_info=?, version=version+1 WHERE id=? AND tenant_id=?",
                    role.getName(),
                    role.getType() != null ? role.getType().name() : "GENERIC",
                    role.getPermissions() != null ? JacksonUtil.toString(role.getPermissions()) : "{}",
                    role.getAdditionalInfo(),
                    role.getId().getId(),
                    role.getTenantId().getId());
        }
        return role;
    }

    @Override
    public Role findById(TenantId tenantId, RoleId id) {
        List<Role> rows = jdbc.query("SELECT * FROM role WHERE id = ? AND tenant_id = ?",
                roleMapper, id.getId(), tenantId.getId());
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public PageData<Role> findByTenantId(TenantId tenantId, PageLink pageLink) {
        int limit = pageLink.getPageSize();
        int offset = pageLink.getPage() * pageLink.getPageSize();
        List<Role> roles = jdbc.query("SELECT * FROM role WHERE tenant_id = ? ORDER BY name ASC LIMIT ? OFFSET ?",
                roleMapper, tenantId.getId(), limit, offset);
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM role WHERE tenant_id = ?",
                Integer.class, tenantId.getId());
        long t = total == null ? 0 : total;
        return new PageData<>(roles, (int) Math.ceil((double) t / limit), t, offset + roles.size() < t);
    }

    @Override
    public void deleteById(TenantId tenantId, RoleId id) {
        jdbc.update("DELETE FROM role WHERE id = ? AND tenant_id = ?", id.getId(), tenantId.getId());
    }

    @Override
    public void assignRole(TenantId tenantId, UserId userId, RoleId roleId, CustomerId customerId) {
        jdbc.update("INSERT INTO user_role (id, created_time, tenant_id, user_id, role_id, customer_id) " +
                        "VALUES (?,?,?,?,?,?) ON CONFLICT (user_id, role_id, customer_id) DO NOTHING",
                UUID.randomUUID(), System.currentTimeMillis(),
                tenantId.getId(), userId.getId(), roleId.getId(),
                customerId != null ? customerId.getId() : null);
    }

    @Override
    public void revokeRole(TenantId tenantId, UserId userId, RoleId roleId) {
        jdbc.update("DELETE FROM user_role WHERE user_id = ? AND role_id = ? AND tenant_id = ?",
                userId.getId(), roleId.getId(), tenantId.getId());
    }

    @Override
    public List<Role> findRolesForUser(TenantId tenantId, UserId userId) {
        return jdbc.query("SELECT r.* FROM role r INNER JOIN user_role ur ON ur.role_id = r.id " +
                        "WHERE ur.user_id = ? AND r.tenant_id = ?",
                roleMapper, userId.getId(), tenantId.getId());
    }

    @Override
    public Set<String> findGrantedOperations(TenantId tenantId, UserId userId, String resourceName, EntityId entityId) {
        Set<String> granted = new HashSet<>();
        List<Role> roles = findRolesForUser(tenantId, userId);
        for (Role role : roles) {
            if (role.getType() == Role.RoleType.GENERIC && role.getPermissions() != null) {
                JsonNode ops = role.getPermissions().get(resourceName);
                if (ops != null && ops.isArray()) {
                    ops.forEach(op -> granted.add(op.asText()));
                }
            }
        }
        if (entityId != null) {
            List<String> groupOps = jdbc.queryForList(
                    "SELECT jsonb_array_elements_text(egp.operations) AS op FROM entity_group_permission egp " +
                            "INNER JOIN user_role ur ON ur.role_id = egp.role_id " +
                            "WHERE ur.user_id = ? AND egp.tenant_id = ? AND " +
                            "(egp.entity_id = ? OR egp.entity_id IS NULL)",
                    String.class, userId.getId(), tenantId.getId(), entityId.getId());
            granted.addAll(groupOps);
        }
        return granted;
    }

    private Role mapRole(ResultSet rs) throws SQLException {
        Role role = new Role(new RoleId((UUID) rs.getObject("id")));
        role.setCreatedTime(rs.getLong("created_time"));
        role.setTenantId(TenantId.fromUUID((UUID) rs.getObject("tenant_id")));
        UUID customerUuid = (UUID) rs.getObject("customer_id");
        if (customerUuid != null) role.setCustomerId(new CustomerId(customerUuid));
        role.setName(rs.getString("name"));
        String type = rs.getString("type");
        role.setType(type != null ? Role.RoleType.valueOf(type) : Role.RoleType.GENERIC);
        String permsJson = rs.getString("permissions");
        role.setPermissions(permsJson != null ? JacksonUtil.toJsonNode(permsJson) : null);
        role.setAdditionalInfo(rs.getString("additional_info"));
        role.setVersion(rs.getLong("version"));
        return role;
    }
}
