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

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.integration.Converter;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.dao.integration.IntegrationService;

import java.util.Map;

/**
 * Hands an Integration adapter the services it needs without exposing the full Spring context.
 * Each adapter calls {@link #processUplink} for inbound device data; this method runs the configured
 * uplink converter (if any) and pushes the resulting message into the tenant's rule engine.
 */
@RequiredArgsConstructor
@Slf4j
public class IntegrationContext {

    private final Integration integration;
    private final TbClusterService tbClusterService;
    private final IntegrationService integrationService;

    public void processUplink(byte[] payload, Map<String, String> metadata) {
        try {
            String payloadStr = payload != null ? new String(payload) : "";
            JsonNode parsed = JacksonUtil.toJsonNode(payloadStr);
            JsonNode converted = parsed;

            if (integration.getDefaultConverterId() != null) {
                Converter converter = integrationService.findConverterById(integration.getTenantId(),
                        integration.getDefaultConverterId());
                if (converter != null) {
                    converted = ScriptConverter.runUplink(converter, parsed != null ? parsed : JacksonUtil.toJsonNode("{\"raw\":\"" +
                            payloadStr.replace("\"", "\\\"") + "\"}"), metadata);
                }
            }

            TbMsgMetaData md = new TbMsgMetaData();
            md.putValue("integrationId", integration.getId().getId().toString());
            md.putValue("integrationName", integration.getName());
            md.putValue("integrationType", integration.getType().name());
            if (metadata != null) metadata.forEach(md::putValue);

            TbMsg msg = TbMsg.newMsg()
                    .type("POST_TELEMETRY_REQUEST")
                    .originator(integration.getTenantId())
                    .copyMetaData(md)
                    .data(converted != null ? JacksonUtil.toString(converted) : payloadStr)
                    .build();

            tbClusterService.pushMsgToRuleEngine(integration.getTenantId(), integration.getTenantId(), msg, null);

            if (integration.isDebugMode()) {
                log.info("[INTEGRATION {}] uplink processed: {}", integration.getName(), msg.getData());
            }
        } catch (Exception e) {
            log.error("Integration {} uplink failed", integration.getName(), e);
        }
    }

    public Integration getIntegration() {
        return integration;
    }

    public TenantId getTenantId() {
        return integration.getTenantId();
    }
}
