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

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.integration.IntegrationType;
import org.thingsboard.server.dao.integration.IntegrationService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.integration.IntegrationContext;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Public, unauthenticated webhook endpoint for HTTP-typed integrations. Devices/cloud services POST
 * a JSON payload to {@code /api/noauth/integrations/http/{routingKey}}; the integration's converter
 * decodes it and forwards to the rule engine. Security is provided by the routing key + optional
 * secret header check, not by user authentication.
 */
@RestController
@TbCoreComponent
@RequestMapping("/api/noauth/integrations")
@RequiredArgsConstructor
@Slf4j
public class IntegrationHttpController {

    public static final String SECRET_HEADER = "X-Integration-Secret";

    private final IntegrationService integrationService;
    private final TbClusterService tbClusterService;

    @PostMapping(value = "/http/{routingKey}")
    public ResponseEntity<?> httpUplink(@PathVariable String routingKey,
                                        @RequestHeader(value = SECRET_HEADER, required = false) String providedSecret,
                                        @RequestBody(required = false) byte[] body,
                                        HttpServletRequest request) {
        Integration integration = integrationService.findByRoutingKey(routingKey);
        if (integration == null || !integration.isEnabled()) {
            return ResponseEntity.notFound().build();
        }
        if (integration.getType() != IntegrationType.HTTP) {
            return ResponseEntity.badRequest().body(Map.of("error",
                    "Integration with this routing key is not an HTTP integration"));
        }
        if (integration.getSecret() != null && !integration.getSecret().isEmpty()
                && !integration.getSecret().equals(providedSecret)) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid integration secret"));
        }

        Map<String, String> meta = new HashMap<>();
        meta.put("httpMethod", request.getMethod());
        meta.put("httpUri", request.getRequestURI());
        meta.put("remoteAddress", request.getRemoteAddr());
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String h = headerNames.nextElement();
            if (!SECRET_HEADER.equalsIgnoreCase(h)) {
                meta.put("h:" + h, request.getHeader(h));
            }
        }

        new IntegrationContext(integration, tbClusterService, integrationService).processUplink(body, meta);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
