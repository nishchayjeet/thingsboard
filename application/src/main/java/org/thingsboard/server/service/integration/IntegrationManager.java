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
package org.thingsboard.server.service.integration;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.id.IntegrationId;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.integration.IntegrationType;
import org.thingsboard.server.dao.integration.IntegrationService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.integration.adapters.HttpIntegrationAdapter;
import org.thingsboard.server.service.integration.adapters.MqttIntegrationAdapter;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the lifecycle of all enabled integrations on this node. On startup, loads enabled integrations from
 * the database and starts a matching adapter for each. Provides start/stop/restart for runtime mutations.
 *
 * HTTP-typed integrations don't need a long-running adapter — they're served by IntegrationHttpController
 * via the routing key. MQTT/Kafka/AWS-IoT etc. start an outbound client and hold a connection.
 */
@Component
@TbCoreComponent
@RequiredArgsConstructor
@Slf4j
public class IntegrationManager {

    private final IntegrationService integrationService;
    private final TbClusterService tbClusterService;
    private final ObjectProvider<MqttIntegrationAdapter> mqttAdapterProvider;
    private final ObjectProvider<HttpIntegrationAdapter> httpAdapterProvider;

    private final Map<IntegrationId, IntegrationAdapter> active = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        try {
            List<Integration> enabled = integrationService.findEnabled();
            log.info("Starting {} enabled integrations", enabled.size());
            for (Integration integ : enabled) {
                startInternal(integ);
            }
        } catch (Exception e) {
            log.error("Failed to initialize integrations", e);
        }
    }

    @PreDestroy
    public void shutdown() {
        active.values().forEach(adapter -> {
            try { adapter.stop(); } catch (Exception ignored) {}
        });
        active.clear();
    }

    public void onSaved(Integration integration) {
        stop(integration.getId());
        if (integration.isEnabled()) {
            startInternal(integration);
        }
    }

    public void stop(IntegrationId id) {
        IntegrationAdapter adapter = active.remove(id);
        if (adapter != null) {
            try { adapter.stop(); } catch (Exception e) { log.warn("Stopping adapter {} failed: {}", id, e.getMessage()); }
        }
    }

    private void startInternal(Integration integration) {
        IntegrationAdapter adapter = createAdapter(integration.getType());
        if (adapter == null) {
            log.info("No adapter registered for integration type {} (integration {}); skipping startup.",
                    integration.getType(), integration.getName());
            return;
        }
        try {
            IntegrationContext ctx = new IntegrationContext(integration, tbClusterService, integrationService);
            adapter.start(ctx);
            active.put(integration.getId(), adapter);
            log.info("Integration {} ({}) started", integration.getName(), integration.getType());
        } catch (Exception e) {
            log.error("Failed to start integration {}", integration.getName(), e);
        }
    }

    private IntegrationAdapter createAdapter(IntegrationType type) {
        return switch (type) {
            case MQTT, AWS_IOT, AZURE_IOT, IBM_WATSON, CHIRPSTACK, THE_THINGS_NETWORK, LORIOT -> mqttAdapterProvider.getIfAvailable();
            case HTTP -> null; // HTTP integrations are reactive — served by IntegrationHttpController
            default -> null;
        };
    }
}
