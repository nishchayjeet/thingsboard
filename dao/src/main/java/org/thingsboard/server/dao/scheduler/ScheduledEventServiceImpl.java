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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.EntityIdFactory;
import org.thingsboard.server.common.data.id.ScheduledEventId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.scheduler.ScheduledEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledEventServiceImpl implements ScheduledEventService {

    private final JdbcTemplate jdbc;

    private final RowMapper<ScheduledEvent> rowMapper = (rs, i) -> mapRow(rs);

    @Override
    public ScheduledEvent saveScheduledEvent(ScheduledEvent event) {
        if (event.getId() == null) {
            event.setId(new ScheduledEventId(UUID.randomUUID()));
            event.setCreatedTime(System.currentTimeMillis());
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO scheduled_event (id, created_time, tenant_id, customer_id, name, type, schedule, configuration, " +
                                "originator_id, originator_type, enabled, next_fire_time, last_fire_time, additional_info, version) " +
                                "VALUES (?,?,?,?,?,?,?::jsonb,?::jsonb,?,?,?,?,?,?,?)");
                ps.setObject(1, event.getId().getId());
                ps.setLong(2, event.getCreatedTime());
                ps.setObject(3, event.getTenantId().getId());
                ps.setObject(4, event.getCustomerId() != null ? event.getCustomerId().getId() : null, Types.OTHER);
                ps.setString(5, event.getName());
                ps.setString(6, event.getType());
                ps.setString(7, JacksonUtil.toString(event.getSchedule()));
                ps.setString(8, JacksonUtil.toString(event.getConfiguration()));
                ps.setObject(9, event.getOriginatorId() != null ? event.getOriginatorId().getId() : null, Types.OTHER);
                ps.setString(10, event.getOriginatorId() != null ? event.getOriginatorId().getEntityType().name() : null);
                ps.setBoolean(11, event.isEnabled());
                if (event.getNextFireTime() != null) ps.setLong(12, event.getNextFireTime()); else ps.setNull(12, Types.BIGINT);
                if (event.getLastFireTime() != null) ps.setLong(13, event.getLastFireTime()); else ps.setNull(13, Types.BIGINT);
                ps.setString(14, event.getAdditionalInfo());
                ps.setLong(15, 1L);
                return ps;
            });
            event.setVersion(1L);
        } else {
            jdbc.update("UPDATE scheduled_event SET name=?, type=?, schedule=?::jsonb, configuration=?::jsonb, " +
                            "originator_id=?, originator_type=?, enabled=?, next_fire_time=?, additional_info=?, version=version+1 WHERE id=?",
                    event.getName(), event.getType(),
                    JacksonUtil.toString(event.getSchedule()), JacksonUtil.toString(event.getConfiguration()),
                    event.getOriginatorId() != null ? event.getOriginatorId().getId() : null,
                    event.getOriginatorId() != null ? event.getOriginatorId().getEntityType().name() : null,
                    event.isEnabled(), event.getNextFireTime(), event.getAdditionalInfo(),
                    event.getId().getId());
        }
        return event;
    }

    @Override
    public ScheduledEvent findById(TenantId tenantId, ScheduledEventId id) {
        List<ScheduledEvent> rows = jdbc.query(
                "SELECT * FROM scheduled_event WHERE id = ? AND tenant_id = ?",
                rowMapper, id.getId(), tenantId.getId());
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public PageData<ScheduledEvent> findByTenantId(TenantId tenantId, PageLink pageLink) {
        int limit = pageLink.getPageSize();
        int offset = pageLink.getPage() * pageLink.getPageSize();
        List<ScheduledEvent> events = jdbc.query(
                "SELECT * FROM scheduled_event WHERE tenant_id = ? ORDER BY created_time DESC LIMIT ? OFFSET ?",
                rowMapper, tenantId.getId(), limit, offset);
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM scheduled_event WHERE tenant_id = ?",
                Integer.class, tenantId.getId());
        long t = total == null ? 0 : total;
        return new PageData<>(events, (int) Math.ceil((double) t / limit), t, offset + events.size() < t);
    }

    @Override
    public void deleteById(TenantId tenantId, ScheduledEventId id) {
        jdbc.update("DELETE FROM scheduled_event WHERE id = ? AND tenant_id = ?", id.getId(), tenantId.getId());
    }

    @Override
    public List<ScheduledEvent> findDueEvents(long now, int limit) {
        return jdbc.query(
                "SELECT * FROM scheduled_event WHERE enabled = TRUE AND next_fire_time IS NOT NULL AND next_fire_time <= ? " +
                        "ORDER BY next_fire_time ASC LIMIT ?",
                rowMapper, now, limit);
    }

    @Override
    public void markFired(ScheduledEventId id, long firedAt, Long nextFireTime) {
        jdbc.update("UPDATE scheduled_event SET last_fire_time = ?, next_fire_time = ? WHERE id = ?",
                firedAt, nextFireTime, id.getId());
    }

    private ScheduledEvent mapRow(ResultSet rs) throws SQLException {
        ScheduledEvent e = new ScheduledEvent(new ScheduledEventId((UUID) rs.getObject("id")));
        e.setCreatedTime(rs.getLong("created_time"));
        e.setTenantId(TenantId.fromUUID((UUID) rs.getObject("tenant_id")));
        UUID customerUuid = (UUID) rs.getObject("customer_id");
        if (customerUuid != null) e.setCustomerId(new CustomerId(customerUuid));
        e.setName(rs.getString("name"));
        e.setType(rs.getString("type"));
        e.setSchedule(parseJson(rs.getString("schedule")));
        e.setConfiguration(parseJson(rs.getString("configuration")));
        UUID origId = (UUID) rs.getObject("originator_id");
        String origType = rs.getString("originator_type");
        if (origId != null && origType != null) {
            e.setOriginatorId(EntityIdFactory.getByTypeAndUuid(origType, origId));
        }
        e.setEnabled(rs.getBoolean("enabled"));
        long nft = rs.getLong("next_fire_time");
        if (!rs.wasNull()) e.setNextFireTime(nft);
        long lft = rs.getLong("last_fire_time");
        if (!rs.wasNull()) e.setLastFireTime(lft);
        e.setAdditionalInfo(rs.getString("additional_info"));
        e.setVersion(rs.getLong("version"));
        return e;
    }

    private static JsonNode parseJson(String s) {
        if (s == null) return null;
        return JacksonUtil.toJsonNode(s);
    }
}
