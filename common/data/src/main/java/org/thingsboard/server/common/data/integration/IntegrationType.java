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
package org.thingsboard.server.common.data.integration;

import lombok.Getter;

public enum IntegrationType {
    HTTP(false, "Generic HTTP webhook (push)"),
    MQTT(true, "Generic MQTT broker subscriber"),
    AWS_IOT(true, "AWS IoT Core via MQTT"),
    AZURE_IOT(true, "Azure IoT Hub via MQTT/AMQP"),
    IBM_WATSON(true, "IBM Watson IoT Platform"),
    KAFKA(true, "Apache Kafka consumer"),
    CHIRPSTACK(true, "ChirpStack LoRaWAN network server"),
    THE_THINGS_NETWORK(true, "The Things Network (TTN/TTI)"),
    LORIOT(true, "Loriot LoRaWAN network server"),
    OPC_UA(true, "OPC-UA client"),
    UDP(true, "UDP socket listener"),
    TCP(true, "TCP socket listener"),
    CUSTOM(true, "Custom integration");

    @Getter
    private final boolean longRunning;
    @Getter
    private final String description;

    IntegrationType(boolean longRunning, String description) {
        this.longRunning = longRunning;
        this.description = description;
    }
}
