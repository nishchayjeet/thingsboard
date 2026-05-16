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
package org.thingsboard.server.dao.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Resolves the next fire time for a ScheduledEvent schedule JSON. Schedule JSON shape:
 *   { "startTime": <epochMs>, "endsOn": <epochMs|null>, "timezone": "UTC",
 *     "repeat": "NONE|MINUTELY|HOURLY|DAILY|WEEKLY|MONTHLY|YEARLY|TIMER|CRON",
 *     "repeatIntervalMs": <long>, "cron": "0 0 * * * *" }
 */
@Slf4j
public final class ScheduleEvaluator {

    private ScheduleEvaluator() {}

    public static Long computeNextFireTime(JsonNode schedule, long now) {
        if (schedule == null || schedule.isNull()) return null;
        long start = schedule.path("startTime").asLong(now);
        Long endsOn = schedule.hasNonNull("endsOn") ? schedule.get("endsOn").asLong() : null;
        String tz = schedule.path("timezone").asText("UTC");
        String repeat = schedule.path("repeat").asText("NONE").toUpperCase();

        long next;
        switch (repeat) {
            case "NONE":
                next = start;
                break;
            case "CRON": {
                String cron = schedule.path("cron").asText(null);
                if (cron == null || cron.isBlank()) return null;
                try {
                    CronExpression expr = CronExpression.parse(cron);
                    ZonedDateTime base = Instant.ofEpochMilli(Math.max(start, now)).atZone(ZoneId.of(tz));
                    ZonedDateTime nextZdt = expr.next(base);
                    if (nextZdt == null) return null;
                    next = nextZdt.toInstant().toEpochMilli();
                } catch (Exception e) {
                    log.warn("Invalid cron expression: {}", cron, e);
                    return null;
                }
                break;
            }
            case "TIMER": {
                long interval = schedule.path("repeatIntervalMs").asLong(0);
                if (interval <= 0) return start > now ? start : null;
                if (start >= now) next = start;
                else {
                    long elapsed = now - start;
                    long ticks = (elapsed / interval) + 1;
                    next = start + ticks * interval;
                }
                break;
            }
            case "MINUTELY":
                next = advance(start, now, Duration.ofMinutes(1).toMillis(), tz, repeat);
                break;
            case "HOURLY":
                next = advance(start, now, Duration.ofHours(1).toMillis(), tz, repeat);
                break;
            case "DAILY":
                next = advance(start, now, Duration.ofDays(1).toMillis(), tz, repeat);
                break;
            case "WEEKLY":
                next = advance(start, now, Duration.ofDays(7).toMillis(), tz, repeat);
                break;
            case "MONTHLY":
            case "YEARLY":
                next = advanceCalendar(start, now, repeat, tz);
                break;
            default:
                return null;
        }

        if (endsOn != null && next > endsOn) return null;
        return next;
    }

    private static long advance(long start, long now, long step, String tz, String repeat) {
        if (start >= now) return start;
        long elapsed = now - start;
        long ticks = (elapsed / step) + 1;
        return start + ticks * step;
    }

    private static long advanceCalendar(long start, long now, String repeat, String tz) {
        ZonedDateTime base = Instant.ofEpochMilli(start).atZone(ZoneId.of(tz));
        ZonedDateTime cursor = base;
        ZonedDateTime nowZdt = Instant.ofEpochMilli(now).atZone(ZoneId.of(tz));
        while (!cursor.isAfter(nowZdt)) {
            cursor = repeat.equals("MONTHLY") ? cursor.plusMonths(1) : cursor.plusYears(1);
        }
        return cursor.toInstant().toEpochMilli();
    }
}
