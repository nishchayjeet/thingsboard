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
package org.thingsboard.server.service.integration.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.integration.IntegrationType;
import org.thingsboard.server.service.integration.IntegrationAdapter;
import org.thingsboard.server.service.integration.IntegrationContext;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Subscribes to a remote MQTT broker (or LoRaWAN network server with MQTT bridge: ChirpStack, TTN, AWS IoT, etc.).
 * Configuration JSON:
 *   {
 *     "host":     "broker.hivemq.com",
 *     "port":     1883,
 *     "ssl":      false,
 *     "clientId": "tb-integration-<uuid>",
 *     "username": "...",
 *     "password": "...",
 *     "topics":   ["application/+/device/+/event/up"]
 *   }
 */
@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Slf4j
public class MqttIntegrationAdapter implements IntegrationAdapter {

    private MqttClient client;

    @Override
    public IntegrationType getType() {
        return IntegrationType.MQTT;
    }

    @Override
    public void start(IntegrationContext ctx) throws Exception {
        JsonNode cfg = ctx.getIntegration().getConfiguration();
        if (cfg == null || cfg.isNull()) {
            throw new IllegalArgumentException("MQTT integration configuration is missing");
        }
        String host = cfg.path("host").asText();
        int port = cfg.path("port").asInt(1883);
        boolean ssl = cfg.path("ssl").asBoolean(false);
        String clientId = cfg.path("clientId").asText("tb-integration-" + UUID.randomUUID());
        String username = cfg.hasNonNull("username") ? cfg.get("username").asText() : null;
        String password = cfg.hasNonNull("password") ? cfg.get("password").asText() : null;

        String broker = (ssl ? "ssl://" : "tcp://") + host + ":" + port;
        client = new MqttClient(broker, clientId, new MemoryPersistence());
        MqttConnectOptions opts = new MqttConnectOptions();
        opts.setAutomaticReconnect(true);
        opts.setCleanSession(true);
        opts.setConnectionTimeout(10);
        if (username != null) opts.setUserName(username);
        if (password != null) opts.setPassword(password.toCharArray());

        client.setCallback(new MqttCallback() {
            @Override public void connectionLost(Throwable cause) {
                log.warn("MQTT integration {} lost connection: {}", ctx.getIntegration().getName(), cause.getMessage());
            }
            @Override public void messageArrived(String topic, MqttMessage message) {
                Map<String, String> meta = new HashMap<>();
                meta.put("mqttTopic", topic);
                meta.put("mqttQos", String.valueOf(message.getQos()));
                ctx.processUplink(message.getPayload(), meta);
            }
            @Override public void deliveryComplete(IMqttDeliveryToken token) {}
        });

        client.connect(opts);
        JsonNode topics = cfg.get("topics");
        if (topics != null && topics.isArray()) {
            for (JsonNode t : topics) {
                client.subscribe(t.asText(), 1);
            }
        } else {
            client.subscribe("#", 1);
        }
        log.info("MQTT integration {} connected to {}", ctx.getIntegration().getName(), broker);
    }

    @Override
    public void stop() {
        if (client != null) {
            try {
                if (client.isConnected()) client.disconnect();
                client.close();
            } catch (Exception e) {
                log.warn("Error stopping MQTT client: {}", e.getMessage());
            } finally {
                client = null;
            }
        }
    }
}
