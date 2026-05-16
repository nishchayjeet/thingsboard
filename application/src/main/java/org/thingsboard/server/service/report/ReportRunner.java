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

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.User;
import org.thingsboard.server.common.data.id.ReportConfigId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.report.ReportConfig;
import org.thingsboard.server.dao.report.ReportConfigService;
import org.thingsboard.server.dao.user.UserService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.security.model.SecurityUser;
import org.thingsboard.server.service.security.model.UserPrincipal;
import org.thingsboard.server.service.security.model.token.JwtTokenFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Executes a single report: snapshots the dashboard via {@link ReportGenerator} and emails the result
 * to each recipient.  Stateless — safe to invoke from the scheduler poller or a manual REST trigger.
 */
@Component
@TbCoreComponent
@RequiredArgsConstructor
@Slf4j
public class ReportRunner {

    @Value("${report.base-url:http://localhost:8080}")
    private String baseUrl;

    @Value("${report.from-address:no-reply@thingsboard.org}")
    private String fromAddress;

    private final ReportGenerator generator;
    private final ReportConfigService reportConfigService;
    private final UserService userService;
    private final JwtTokenFactory jwtTokenFactory;
    private final Optional<JavaMailSender> javaMailSender;

    public void run(TenantId tenantId, ReportConfigId id) {
        ReportConfig report = reportConfigService.findById(tenantId, id);
        if (report == null) {
            log.warn("Report {} not found in tenant {}", id, tenantId);
            return;
        }
        long now = System.currentTimeMillis();
        try {
            String url = buildDashboardUrl(report);
            String jwt = issueRenderToken(tenantId);
            Path output = generator.generate(report, url, jwt);
            try {
                send(report, output);
                reportConfigService.recordRun(id, now, "SUCCESS", null);
            } finally {
                try { Files.deleteIfExists(output); } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            log.error("Report {} run failed", id, e);
            reportConfigService.recordRun(id, now, "FAILED", e.getMessage());
        }
    }

    private String buildDashboardUrl(ReportConfig report) {
        StringBuilder sb = new StringBuilder(baseUrl).append("/dashboard/").append(report.getDashboardId().getId());
        if (report.getStateId() != null) sb.append("?state=").append(report.getStateId());
        return sb.toString();
    }

    /**
     * Mint a short-ish-lived access JWT impersonating the tenant's first admin user, so the headless
     * browser (run by tb-web-report or the in-process chromium fork) can render private dashboards.
     * Returns {@code null} if no tenant admin exists (e.g. fresh tenant, or scheduler firing for an
     * orphaned config) — the renderer will then fall back to an unauthenticated page load.
     */
    private String issueRenderToken(TenantId tenantId) {
        try {
            PageData<User> admins = userService.findTenantAdmins(tenantId, new PageLink(1));
            if (admins.getData().isEmpty()) {
                log.warn("No tenant admin found for tenant {} — report will render unauthenticated", tenantId);
                return null;
            }
            User user = admins.getData().get(0);
            UserPrincipal principal = new UserPrincipal(UserPrincipal.Type.USER_NAME, user.getEmail());
            SecurityUser securityUser = new SecurityUser(user, true, principal);
            return jwtTokenFactory.createAccessJwtToken(securityUser).token();
        } catch (Exception e) {
            log.warn("Failed to mint render token for tenant {}: {}", tenantId, e.getMessage());
            return null;
        }
    }

    private void send(ReportConfig report, Path attachment) throws Exception {
        if (javaMailSender.isEmpty()) {
            log.warn("Report {} generated but no JavaMailSender bean is configured; skipping send",
                    report.getName());
            return;
        }
        List<String> recipients = parseRecipients(report.getRecipients());
        if (recipients.isEmpty()) {
            log.warn("Report {} has no recipients; nothing to send.", report.getName());
            return;
        }
        String dateStr = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        String subject = "[Report] " + report.getName() + " — " + dateStr;
        String body = "Attached: scheduled report \"" + report.getName() + "\"" +
                "\nGenerated: " + dateStr + "\n";

        JavaMailSender sender = javaMailSender.get();
        for (String to : recipients) {
            MimeMessage msg = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            String filename = report.getName().replaceAll("[^a-zA-Z0-9._-]", "_") + "_" + dateStr +
                    (report.getFormat() == ReportConfig.Format.PDF ? ".pdf" : ".png");
            helper.addAttachment(filename, new FileSystemResource(attachment.toFile()));
            sender.send(msg);
        }
    }

    private List<String> parseRecipients(JsonNode recipients) {
        List<String> out = new ArrayList<>();
        if (recipients == null || !recipients.isArray()) return out;
        recipients.forEach(n -> {
            if (n.isTextual()) out.add(n.asText());
            else if (n.has("email")) out.add(n.get("email").asText());
        });
        return out;
    }
}
