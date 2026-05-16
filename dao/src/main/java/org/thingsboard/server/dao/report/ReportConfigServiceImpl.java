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
package org.thingsboard.server.dao.report;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.ReportConfigId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.report.ReportConfig;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportConfigServiceImpl implements ReportConfigService {

    private final JdbcTemplate jdbc;

    private final RowMapper<ReportConfig> mapper = (rs, i) -> map(rs);

    @Override
    public ReportConfig save(ReportConfig report) {
        if (report.getId() == null) {
            report.setId(new ReportConfigId(UUID.randomUUID()));
            report.setCreatedTime(System.currentTimeMillis());
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO report_config (id, created_time, tenant_id, customer_id, name, dashboard_id, state_id, " +
                                "format, timezone, recipients, schedule, use_dashboard_timewindow, timewindow, " +
                                "additional_info, version) VALUES (?,?,?,?,?,?,?,?,?,?::jsonb,?::jsonb,?,?::jsonb,?,?)");
                ps.setObject(1, report.getId().getId());
                ps.setLong(2, report.getCreatedTime());
                ps.setObject(3, report.getTenantId().getId());
                ps.setObject(4, report.getCustomerId() != null ? report.getCustomerId().getId() : null, Types.OTHER);
                ps.setString(5, report.getName());
                ps.setObject(6, report.getDashboardId().getId());
                ps.setString(7, report.getStateId());
                ps.setString(8, report.getFormat().name());
                ps.setString(9, report.getTimezone());
                ps.setString(10, report.getRecipients() != null ? JacksonUtil.toString(report.getRecipients()) : "[]");
                ps.setString(11, report.getSchedule() != null ? JacksonUtil.toString(report.getSchedule()) : null);
                ps.setBoolean(12, report.isUseDashboardTimewindow());
                ps.setString(13, report.getTimewindow() != null ? JacksonUtil.toString(report.getTimewindow()) : null);
                ps.setString(14, report.getAdditionalInfo());
                ps.setLong(15, 1L);
                return ps;
            });
        } else {
            jdbc.update("UPDATE report_config SET name=?, dashboard_id=?, state_id=?, format=?, timezone=?, " +
                            "recipients=?::jsonb, schedule=?::jsonb, use_dashboard_timewindow=?, timewindow=?::jsonb, " +
                            "additional_info=?, version=version+1 WHERE id=? AND tenant_id=?",
                    report.getName(), report.getDashboardId().getId(), report.getStateId(),
                    report.getFormat().name(), report.getTimezone(),
                    report.getRecipients() != null ? JacksonUtil.toString(report.getRecipients()) : "[]",
                    report.getSchedule() != null ? JacksonUtil.toString(report.getSchedule()) : null,
                    report.isUseDashboardTimewindow(),
                    report.getTimewindow() != null ? JacksonUtil.toString(report.getTimewindow()) : null,
                    report.getAdditionalInfo(),
                    report.getId().getId(), report.getTenantId().getId());
        }
        return report;
    }

    @Override
    public ReportConfig findById(TenantId tenantId, ReportConfigId id) {
        List<ReportConfig> rows = jdbc.query("SELECT * FROM report_config WHERE id=? AND tenant_id=?",
                mapper, id.getId(), tenantId.getId());
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public PageData<ReportConfig> findByTenantId(TenantId tenantId, PageLink pageLink) {
        int limit = pageLink.getPageSize();
        int offset = pageLink.getPage() * pageLink.getPageSize();
        List<ReportConfig> rows = jdbc.query("SELECT * FROM report_config WHERE tenant_id=? ORDER BY name ASC LIMIT ? OFFSET ?",
                mapper, tenantId.getId(), limit, offset);
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM report_config WHERE tenant_id=?",
                Integer.class, tenantId.getId());
        long t = total == null ? 0 : total;
        return new PageData<>(rows, (int) Math.ceil((double) t / limit), t, offset + rows.size() < t);
    }

    @Override
    public List<ReportConfig> findAll() {
        return jdbc.query("SELECT * FROM report_config", mapper);
    }

    @Override
    public void delete(TenantId tenantId, ReportConfigId id) {
        jdbc.update("DELETE FROM report_config WHERE id=? AND tenant_id=?", id.getId(), tenantId.getId());
    }

    @Override
    public void recordRun(ReportConfigId id, long ts, String status, String error) {
        jdbc.update("UPDATE report_config SET last_run_time=?, last_run_status=?, last_run_error=? WHERE id=?",
                ts, status, error, id.getId());
    }

    private ReportConfig map(ResultSet rs) throws SQLException {
        ReportConfig r = new ReportConfig(new ReportConfigId((UUID) rs.getObject("id")));
        r.setCreatedTime(rs.getLong("created_time"));
        r.setTenantId(TenantId.fromUUID((UUID) rs.getObject("tenant_id")));
        UUID cust = (UUID) rs.getObject("customer_id");
        if (cust != null) r.setCustomerId(new CustomerId(cust));
        r.setName(rs.getString("name"));
        r.setDashboardId(new DashboardId((UUID) rs.getObject("dashboard_id")));
        r.setStateId(rs.getString("state_id"));
        r.setFormat(ReportConfig.Format.valueOf(rs.getString("format")));
        r.setTimezone(rs.getString("timezone"));
        String recipients = rs.getString("recipients");
        if (recipients != null) r.setRecipients(JacksonUtil.toJsonNode(recipients));
        String schedule = rs.getString("schedule");
        if (schedule != null) r.setSchedule(JacksonUtil.toJsonNode(schedule));
        r.setUseDashboardTimewindow(rs.getBoolean("use_dashboard_timewindow"));
        String timewindow = rs.getString("timewindow");
        if (timewindow != null) r.setTimewindow(JacksonUtil.toJsonNode(timewindow));
        r.setLastRunStatus(rs.getString("last_run_status"));
        long lr = rs.getLong("last_run_time");
        if (!rs.wasNull()) r.setLastRunTime(lr);
        r.setLastRunError(rs.getString("last_run_error"));
        r.setAdditionalInfo(rs.getString("additional_info"));
        return r;
    }
}
