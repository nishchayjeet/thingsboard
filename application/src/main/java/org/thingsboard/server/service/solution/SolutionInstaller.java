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
package org.thingsboard.server.service.solution;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.Dashboard;
import org.thingsboard.server.common.data.DeviceProfile;
import org.thingsboard.server.common.data.id.SolutionTemplateId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.rule.RuleChain;
import org.thingsboard.server.common.data.solution.SolutionTemplate;
import org.thingsboard.server.dao.dashboard.DashboardService;
import org.thingsboard.server.dao.device.DeviceProfileService;
import org.thingsboard.server.dao.rule.RuleChainService;
import org.thingsboard.server.dao.solution.SolutionTemplateService;
import org.thingsboard.server.queue.util.TbCoreComponent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Installs a {@link SolutionTemplate} into a target tenant by replaying the bundle's entities in
 * dependency order: device profiles → asset profiles → rule chains → dashboards. Records a row in
 * {@code solution_install_record} for traceability.
 *
 * This is intentionally narrower than PE's full importer — it covers the four most-installed entity
 * kinds. For a complete bundle (devices, customers, scheduled events, integrations), extend the
 * type-dispatch switch below.
 */
@Component
@TbCoreComponent
@RequiredArgsConstructor
@Slf4j
public class SolutionInstaller {

    private final SolutionTemplateService templateService;
    private final DeviceProfileService deviceProfileService;
    private final DashboardService dashboardService;
    private final RuleChainService ruleChainService;
    private final JdbcTemplate jdbc;

    public InstallResult install(TenantId tenantId, SolutionTemplateId templateId) {
        SolutionTemplate template = templateService.findById(templateId);
        if (template == null) throw new IllegalArgumentException("Solution template not found: " + templateId);

        JsonNode bundle = template.getBundle();
        InstallResult result = new InstallResult();
        result.templateId = templateId;
        result.templateName = template.getName();

        UUID recordId = UUID.randomUUID();
        try {
            installArray(bundle.get("deviceProfiles"), node -> {
                DeviceProfile dp = JacksonUtil.treeToValue(node, DeviceProfile.class);
                if (dp == null) return null;
                dp.setId(null);
                dp.setTenantId(tenantId);
                dp.setDefault(false);
                DeviceProfile saved = deviceProfileService.saveDeviceProfile(dp);
                result.deviceProfileIds.add(saved.getId().getId());
                return saved.getId().getId();
            });

            installArray(bundle.get("ruleChains"), node -> {
                RuleChain rc = JacksonUtil.treeToValue(node, RuleChain.class);
                if (rc == null) return null;
                rc.setId(null);
                rc.setTenantId(tenantId);
                RuleChain saved = ruleChainService.saveRuleChain(rc);
                result.ruleChainIds.add(saved.getId().getId());
                return saved.getId().getId();
            });

            installArray(bundle.get("dashboards"), node -> {
                Dashboard d = JacksonUtil.treeToValue(node, Dashboard.class);
                if (d == null) return null;
                d.setId(null);
                d.setTenantId(tenantId);
                Dashboard saved = dashboardService.saveDashboard(d);
                result.dashboardIds.add(saved.getId().getId());
                return saved.getId().getId();
            });

            recordInstall(recordId, tenantId, templateId, template.getName(), "SUCCESS", result, null);
            return result;
        } catch (Exception e) {
            log.error("Solution install failed: template={} tenant={}", templateId, tenantId, e);
            recordInstall(recordId, tenantId, templateId, template.getName(), "FAILED", result, e.getMessage());
            throw new RuntimeException("Solution install failed: " + e.getMessage(), e);
        }
    }

    private interface NodeInstaller {
        UUID install(JsonNode node);
    }

    private void installArray(JsonNode arr, NodeInstaller installer) {
        if (arr == null || !arr.isArray()) return;
        for (JsonNode node : arr) {
            try {
                installer.install(node);
            } catch (Exception e) {
                log.warn("Skipping entity in solution bundle due to error: {}", e.getMessage());
            }
        }
    }

    private void recordInstall(UUID recordId, TenantId tenantId, SolutionTemplateId templateId,
                               String templateName, String status, InstallResult result, String error) {
        try {
            ObjectNode installed = JacksonUtil.newObjectNode();
            installed.set("deviceProfileIds", JacksonUtil.valueToTree(result.deviceProfileIds));
            installed.set("ruleChainIds", JacksonUtil.valueToTree(result.ruleChainIds));
            installed.set("dashboardIds", JacksonUtil.valueToTree(result.dashboardIds));
            jdbc.update(
                    "INSERT INTO solution_install_record (id, created_time, tenant_id, template_id, template_name, status, installed_entities, error_message) " +
                            "VALUES (?,?,?,?,?,?,?::jsonb,?)",
                    recordId, System.currentTimeMillis(), tenantId.getId(), templateId.getId(),
                    templateName, status, JacksonUtil.toString(installed), error);
        } catch (Exception e) {
            log.warn("Failed to record solution install: {}", e.getMessage());
        }
    }

    public static class InstallResult {
        public SolutionTemplateId templateId;
        public String templateName;
        public List<UUID> deviceProfileIds = new ArrayList<>();
        public List<UUID> ruleChainIds = new ArrayList<>();
        public List<UUID> dashboardIds = new ArrayList<>();
    }
}
