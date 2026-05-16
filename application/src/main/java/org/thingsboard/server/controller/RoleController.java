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
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.RoleId;
import org.thingsboard.server.common.data.id.UserId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.role.Role;
import org.thingsboard.server.config.annotations.ApiOperation;
import org.thingsboard.server.dao.role.RoleService;
import org.thingsboard.server.queue.util.TbCoreComponent;

import java.util.List;
import java.util.UUID;

@RestController
@TbCoreComponent
@Slf4j
@RequestMapping("/api")
@RequiredArgsConstructor
public class RoleController extends BaseController {

    private final RoleService roleService;

    @ApiOperation(value = "Create or update a role")
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @PostMapping("/role")
    public Role saveRole(@RequestBody Role role) throws ThingsboardException {
        role.setTenantId(getTenantId());
        return roleService.saveRole(role);
    }

    @ApiOperation(value = "Get role by id")
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @GetMapping("/role/{id}")
    public Role getRoleById(@PathVariable("id") String id) throws ThingsboardException {
        return checkNotNull(roleService.findById(getTenantId(), new RoleId(UUID.fromString(id))));
    }

    @ApiOperation(value = "List roles for the current tenant")
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @GetMapping("/roles")
    public PageData<Role> getRoles(@RequestParam int pageSize,
                                   @RequestParam int page,
                                   @RequestParam(required = false) String textSearch,
                                   @RequestParam(required = false) String sortProperty,
                                   @RequestParam(required = false) String sortOrder) throws ThingsboardException {
        PageLink pageLink = createPageLink(pageSize, page, textSearch, sortProperty, sortOrder);
        return roleService.findByTenantId(getTenantId(), pageLink);
    }

    @ApiOperation(value = "Delete role")
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @DeleteMapping("/role/{id}")
    public void deleteRole(@PathVariable("id") String id) throws ThingsboardException {
        roleService.deleteById(getTenantId(), new RoleId(UUID.fromString(id)));
    }

    @ApiOperation(value = "Assign role to a user")
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @PostMapping("/user/{userId}/role/{roleId}")
    public void assignRole(@PathVariable("userId") String userId,
                           @PathVariable("roleId") String roleId,
                           @Parameter(description = "Customer id (optional, scopes the assignment).")
                           @RequestParam(required = false) String customerId) throws ThingsboardException {
        CustomerId customer = customerId != null ? new CustomerId(UUID.fromString(customerId)) : null;
        roleService.assignRole(getTenantId(), new UserId(UUID.fromString(userId)),
                new RoleId(UUID.fromString(roleId)), customer);
    }

    @ApiOperation(value = "Revoke role from a user")
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @DeleteMapping("/user/{userId}/role/{roleId}")
    public void revokeRole(@PathVariable("userId") String userId,
                           @PathVariable("roleId") String roleId) throws ThingsboardException {
        roleService.revokeRole(getTenantId(), new UserId(UUID.fromString(userId)),
                new RoleId(UUID.fromString(roleId)));
    }

    @ApiOperation(value = "List roles assigned to a user")
    @PreAuthorize("hasAuthority('TENANT_ADMIN')")
    @GetMapping("/user/{userId}/roles")
    public List<Role> getUserRoles(@PathVariable("userId") String userId) throws ThingsboardException {
        return roleService.findRolesForUser(getTenantId(), new UserId(UUID.fromString(userId)));
    }
}
