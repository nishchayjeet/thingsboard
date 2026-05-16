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
package org.thingsboard.server.service.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.cluster.TbClusterService;
import org.thingsboard.server.common.data.id.EntityId;
import org.thingsboard.server.common.data.id.ReportConfigId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.scheduler.ScheduledEvent;
import org.thingsboard.server.common.msg.TbMsg;
import org.thingsboard.server.common.msg.TbMsgMetaData;
import org.thingsboard.server.dao.scheduler.ScheduleEvaluator;
import org.thingsboard.server.dao.scheduler.ScheduledEventService;
import org.thingsboard.server.queue.util.TbCoreComponent;
import org.thingsboard.server.service.report.ReportRunner;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Polls the scheduled_event table at a fixed interval and pushes due events into the tenant's rule engine.
 * Single-server safe; on multi-server deployments add a row-level lock via SELECT ... FOR UPDATE SKIP LOCKED
 * before invoking firing logic.
 */
@Component
@TbCoreComponent
@RequiredArgsConstructor
@Slf4j
public class SchedulerPoller {

    public static final String SCHEDULED_EVENT_MSG_TYPE = "SCHEDULED_EVENT";
    private static final int BATCH_SIZE = 200;

    public static final String SCHEDULED_EVENT_TYPE_REPORT = "REPORT";

    private final ScheduledEventService scheduledEventService;
    private final TbClusterService tbClusterService;
    private final Optional<ReportRunner> reportRunner;

    @Scheduled(initialDelayString = "${scheduler.poll.initial-delay-ms:5000}",
               fixedDelayString = "${scheduler.poll.delay-ms:10000}")
    public void poll() {
        long now = System.currentTimeMillis();
        List<ScheduledEvent> due;
        try {
            due = scheduledEventService.findDueEvents(now, BATCH_SIZE);
        } catch (Exception e) {
            log.error("Scheduler poll: failed to read due events", e);
            return;
        }
        for (ScheduledEvent event : due) {
            fire(event, now);
        }
    }

    private void fire(ScheduledEvent event, long now) {
        try {
            TenantId tenantId = event.getTenantId();
            EntityId originator = event.getOriginatorId() != null ? event.getOriginatorId() : tenantId;
            TbMsgMetaData metaData = new TbMsgMetaData();
            metaData.putValue("scheduledEventId", event.getId().getId().toString());
            metaData.putValue("scheduledEventName", event.getName());
            metaData.putValue("scheduledEventType", event.getType() != null ? event.getType() : "");

            String data = event.getConfiguration() != null ? JacksonUtil.toString(event.getConfiguration()) : "{}";

            TbMsg msg = TbMsg.newMsg()
                    .type(SCHEDULED_EVENT_MSG_TYPE)
                    .originator(originator)
                    .copyMetaData(metaData)
                    .data(data)
                    .build();

            tbClusterService.pushMsgToRuleEngine(tenantId, originator, msg, null);

            // Side-effect handlers for built-in scheduled event types.
            if (SCHEDULED_EVENT_TYPE_REPORT.equalsIgnoreCase(event.getType()) && reportRunner.isPresent()) {
                String reportId = event.getConfiguration() != null && event.getConfiguration().hasNonNull("reportId")
                        ? event.getConfiguration().get("reportId").asText()
                        : null;
                if (reportId != null) {
                    try {
                        reportRunner.get().run(tenantId, new ReportConfigId(UUID.fromString(reportId)));
                    } catch (Exception runErr) {
                        log.error("Scheduled report run failed for event {}", event.getId(), runErr);
                    }
                }
            }

            Long nextFire = ScheduleEvaluator.computeNextFireTime(event.getSchedule(), now + 1);
            scheduledEventService.markFired(event.getId(), now, nextFire);
        } catch (Exception e) {
            log.error("Failed to fire scheduled event {}", event.getId(), e);
            scheduledEventService.markFired(event.getId(), now, null);
        }
    }
}
