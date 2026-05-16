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
package org.thingsboard.server.service.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.report.ReportConfig;
import org.thingsboard.server.queue.util.TbCoreComponent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

/**
 * Submits a render request to an out-of-process tb-web-report microservice and streams the PDF/PNG
 * response to a temp file. Mirrors the PE {@code REPORTS_SERVER_ENDPOINT_URL} contract.
 *
 * Enabled when {@code reports.server.endpoint-url} (env {@code REPORTS_SERVER_ENDPOINT_URL}) is set
 * to a non-empty value; otherwise the caller should fall back to the in-process renderer.
 */
@Component
@TbCoreComponent
@Slf4j
public class WebReportClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Value("${reports.server.endpoint-url:}")
    private String endpointUrl;

    @Value("${reports.server.request-timeout-seconds:180}")
    private int requestTimeoutSeconds;

    @Value("${reports.server.navigation-timeout-ms:120000}")
    private int navigationTimeoutMs;

    @Value("${reports.server.idle-wait-ms:3000}")
    private int idleWaitMs;

    private HttpClient http;

    public boolean isEnabled() {
        return endpointUrl != null && !endpointUrl.isBlank();
    }

    public Path generate(ReportConfig config, String dashboardUrl, String jwt) throws IOException, InterruptedException {
        if (!isEnabled()) {
            throw new IllegalStateException("WebReportClient is not configured (reports.server.endpoint-url is empty)");
        }
        ObjectNode body = MAPPER.createObjectNode();
        body.put("url", dashboardUrl);
        if (jwt != null && !jwt.isBlank()) {
            body.put("jwt", jwt);
        }
        body.put("format", config.getFormat() == ReportConfig.Format.PDF ? "pdf" : "png");
        body.put("navigationTimeoutMs", navigationTimeoutMs);
        body.put("idleWaitMs", idleWaitMs);
        ObjectNode viewport = body.putObject("viewport");
        viewport.put("width", 1600);
        viewport.put("height", 1200);

        URI uri = URI.create(stripTrailingSlash(endpointUrl) + "/api/generateReport");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(requestTimeoutSeconds))
                .header("Content-Type", "application/json")
                .header("Accept", "application/pdf, image/png")
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

        Path output = Files.createTempFile("tb-report-",
                config.getFormat() == ReportConfig.Format.PDF ? ".pdf" : ".png");
        try {
            HttpResponse<Path> response = client().send(request, HttpResponse.BodyHandlers.ofFile(output));
            if (response.statusCode() / 100 != 2) {
                String snippet = "";
                try {
                    byte[] bytes = Files.readAllBytes(output);
                    snippet = new String(bytes, 0, Math.min(bytes.length, 512));
                } catch (Exception ignore) {}
                throw new IOException("tb-web-report returned HTTP " + response.statusCode() + ": " + snippet);
            }
            if (Files.size(output) == 0) {
                throw new IOException("tb-web-report returned an empty body");
            }
            return output;
        } catch (IOException | InterruptedException e) {
            try { Files.deleteIfExists(output); } catch (Exception ignore) {}
            throw e;
        }
    }

    private synchronized HttpClient client() {
        if (http == null) {
            http = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
        }
        return http;
    }

    private static String stripTrailingSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
