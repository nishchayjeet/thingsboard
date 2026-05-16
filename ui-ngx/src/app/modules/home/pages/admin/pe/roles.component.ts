///
/// Copyright © 2016-2026 The Thingsboard Authors
///
/// Licensed under the Apache License, Version 2.0 (the "License");
/// you may not use this file except in compliance with the License.
/// You may obtain a copy of the License at
///
///     http://www.apache.org/licenses/LICENSE-2.0
///
/// Unless required by applicable law or agreed to in writing, software
/// distributed under the License is distributed on an "AS IS" BASIS,
/// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
/// See the License for the specific language governing permissions and
/// limitations under the License.
///

import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Store } from '@ngrx/store';
import { AppState } from '@core/core.state';
import { PageComponent } from '@shared/components/page.component';
import { Role, RoleService } from '@core/http/role.service';
import { PageLink } from '@shared/models/page/page-link';
import { Subject } from 'rxjs';

/**
 * PE-compatible Roles page. GENERIC roles carry a resource→operations map;
 * GROUP roles carry an "operations" list applied to entities granted via
 * entity_group_permission rows.
 */
@Component({
  selector: 'tb-roles',
  templateUrl: './roles.component.html',
  styleUrls: ['./pe-pages.scss'],
  standalone: false
})
export class RolesComponent extends PageComponent implements OnInit, OnDestroy {

  // Match the Resource enum in
  // application/src/main/java/org/thingsboard/server/service/security/permission/Resource.java
  readonly RESOURCES = [
    'DEVICE', 'ASSET', 'DASHBOARD', 'CUSTOMER', 'USER',
    'DEVICE_PROFILE', 'ASSET_PROFILE', 'RULE_CHAIN', 'WIDGETS_BUNDLE',
    'WIDGET_TYPE', 'ENTITY_VIEW', 'EDGE', 'TENANT', 'ALARM', 'TB_RESOURCE',
    'OTA_PACKAGE', 'QUEUE', 'NOTIFICATION', 'ADMIN_SETTINGS',
    'ROLE', 'SCHEDULED_EVENT', 'REPORT_CONFIG', 'INTEGRATION',
    'CONVERTER', 'PAYLOAD_CODEC', 'SOLUTION_TEMPLATE'
  ];

  // Match Operation enum.
  readonly OPERATIONS = [
    'ALL', 'CREATE', 'READ', 'WRITE', 'DELETE',
    'ASSIGN_TO_CUSTOMER', 'UNASSIGN_FROM_CUSTOMER', 'RPC_CALL',
    'READ_CREDENTIALS', 'WRITE_CREDENTIALS', 'READ_ATTRIBUTES',
    'WRITE_ATTRIBUTES', 'READ_TELEMETRY', 'WRITE_TELEMETRY', 'CLAIM_DEVICES'
  ];

  roles: Role[] = [];
  selected: Role | null = null;
  totalRoles = 0;
  form: FormGroup;

  /** UI-only matrix: { resource: { op: boolean } } */
  matrix: Record<string, Record<string, boolean>> = {};
  /** GROUP role: flat operation toggles */
  groupOps: Record<string, boolean> = {};

  private destroy$ = new Subject<void>();

  constructor(protected store: Store<AppState>,
              private fb: FormBuilder,
              private service: RoleService) {
    super(store);
    this.form = this.fb.group({
      name: ['', [Validators.required, Validators.maxLength(255)]],
      type: ['GENERIC', Validators.required],
      additionalInfo: ['']
    });
  }

  ngOnInit() { this.load(); this.resetMatrix(); }

  ngOnDestroy() {
    super.ngOnDestroy();
    this.destroy$.next(); this.destroy$.complete();
  }

  load() {
    this.service.getRoles(new PageLink(100)).subscribe(page => {
      this.roles = page.data || [];
      this.totalRoles = page.totalElements;
    });
  }

  newRole() {
    this.selected = null;
    this.form.reset({ name: '', type: 'GENERIC', additionalInfo: '' });
    this.resetMatrix();
  }

  select(role: Role) {
    this.selected = role;
    this.form.reset({
      name: role.name,
      type: role.type,
      additionalInfo: role.additionalInfo || ''
    });
    this.resetMatrix();
    if (role.type === 'GROUP') {
      const ops = (role.permissions as { operations: string[] })?.operations || [];
      ops.forEach(o => this.groupOps[o] = true);
    } else {
      const perms = (role.permissions as Record<string, string[]>) || {};
      Object.entries(perms).forEach(([resource, ops]) => {
        ops.forEach(op => {
          if (!this.matrix[resource]) this.matrix[resource] = {};
          this.matrix[resource][op] = true;
        });
      });
    }
  }

  save() {
    if (this.form.invalid) return;
    const type = this.form.value.type as 'GENERIC' | 'GROUP';
    let permissions: any;
    if (type === 'GROUP') {
      permissions = { operations: Object.entries(this.groupOps).filter(([, v]) => v).map(([k]) => k) };
    } else {
      permissions = {};
      for (const resource of this.RESOURCES) {
        const granted = Object.entries(this.matrix[resource] || {})
          .filter(([, v]) => v).map(([op]) => op);
        if (granted.length) permissions[resource] = granted;
      }
    }

    const payload: Role = {
      ...(this.selected || {}),
      name: this.form.value.name,
      type,
      permissions,
      additionalInfo: this.form.value.additionalInfo
    } as Role;

    this.service.saveRole(payload).subscribe(() => {
      this.selected = null;
      this.load();
    });
  }

  remove(role: Role) {
    if (!role.id || !confirm(`Delete role "${role.name}"?`)) return;
    this.service.deleteRole(role.id.id).subscribe(() => {
      if (this.selected?.id?.id === role.id?.id) this.selected = null;
      this.load();
    });
  }

  toggleResourceAll(resource: string) {
    const allOn = this.OPERATIONS.every(op => this.matrix[resource]?.[op]);
    if (!this.matrix[resource]) this.matrix[resource] = {};
    this.OPERATIONS.forEach(op => this.matrix[resource][op] = !allOn);
  }

  resourceGrantedCount(resource: string): number {
    return Object.values(this.matrix[resource] || {}).filter(Boolean).length;
  }

  private resetMatrix() {
    this.matrix = {};
    this.groupOps = {};
    this.RESOURCES.forEach(r => this.matrix[r] = {});
  }
}
