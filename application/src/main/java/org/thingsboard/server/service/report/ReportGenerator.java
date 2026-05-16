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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.report.ReportConfig;
import org.thingsboard.server.queue.util.TbCoreComponent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Snapshots a tenant dashboard to a PDF or PNG. Two backends are supported:
 *
 *   1. Out-of-process microservice (preferred, mirrors PE):
 *      Enabled when {@code reports.server.endpoint-url} is set. Renders via a
 *      separate tb-web-report container running puppeteer + Chrome.
 *
 *   2. In-process Chromium fork (fallback):
 *      Forks the {@code chromium} binary directly with {@code --print-to-pdf} / {@code --screenshot}.
 *      Useful for development hosts that already have Chromium installed; not recommended for
 *      production because it blocks a Java worker thread per report.
 */
@Component
@TbCoreComponent
@RequiredArgsConstructor
@Slf4j
public class ReportGenerator {

    @Value("${report.chrome.binary:chromium}")
    private String chromeBinary;

    @Value("${report.chrome.timeout-seconds:60}")
    private int timeoutSeconds;

    @Value("${report.chrome.window-size:1600,1200}")
    private String windowSize;

    private final WebReportClient webReportClient;

    public Path generate(ReportConfig config, String dashboardUrl, String jwt) throws IOException, InterruptedException {
        if (webReportClient.isEnabled()) {
            log.debug("Delegating report {} to tb-web-report microservice", config.getName());
            return webReportClient.generate(config, dashboardUrl, jwt);
        }
        return forkChromium(config, dashboardUrl);
    }

    private Path forkChromium(ReportConfig config, String dashboardUrl) throws IOException, InterruptedException {
        Path output = Files.createTempFile("tb-report-", suffix(config.getFormat()));
        List<String> cmd = new ArrayList<>();
        cmd.add(chromeBinary);
        cmd.add("--headless=new");
        cmd.add("--no-sandbox");
        cmd.add("--disable-gpu");
        cmd.add("--hide-scrollbars");
        cmd.add("--window-size=" + windowSize);
        cmd.add("--virtual-time-budget=10000");
        if (config.getFormat() == ReportConfig.Format.PDF) {
            cmd.add("--print-to-pdf=" + output);
            cmd.add("--no-pdf-header-footer");
        } else {
            cmd.add("--screenshot=" + output);
        }
        cmd.add(dashboardUrl);

        log.debug("Launching headless render: {}", cmd);
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process process = pb.start();
        boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IOException("Headless render timed out after " + timeoutSeconds + "s");
        }
        if (process.exitValue() != 0) {
            String err = new String(process.getInputStream().readAllBytes());
            throw new IOException("Headless render failed (exit " + process.exitValue() + "): " + err);
        }
        if (!Files.exists(output) || Files.size(output) == 0) {
            throw new IOException("Headless render produced no output at " + output);
        }
        return output;
    }

    private String suffix(ReportConfig.Format format) {
        return format == ReportConfig.Format.PDF ? ".pdf" : ".png";
    }
}
