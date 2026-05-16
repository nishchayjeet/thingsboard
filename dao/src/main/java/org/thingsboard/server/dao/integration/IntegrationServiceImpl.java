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
package org.thingsboard.server.dao.integration;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.id.ConverterId;
import org.thingsboard.server.common.data.id.IntegrationId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.integration.Converter;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.integration.IntegrationType;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class IntegrationServiceImpl implements IntegrationService {

    private final JdbcTemplate jdbc;

    private final RowMapper<Integration> integrationMapper = (rs, i) -> mapIntegration(rs);
    private final RowMapper<Converter> converterMapper = (rs, i) -> mapConverter(rs);

    @Override
    public Integration saveIntegration(Integration integration) {
        if (integration.getId() == null) {
            integration.setId(new IntegrationId(UUID.randomUUID()));
            integration.setCreatedTime(System.currentTimeMillis());
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO integration (id, created_time, tenant_id, name, routing_key, secret, type, enabled, " +
                                "is_remote, allow_create_devices_or_assets, default_converter_id, downlink_converter_id, " +
                                "debug_mode, configuration, additional_info, version) " +
                                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?)");
                ps.setObject(1, integration.getId().getId());
                ps.setLong(2, integration.getCreatedTime());
                ps.setObject(3, integration.getTenantId().getId());
                ps.setString(4, integration.getName());
                ps.setString(5, integration.getRoutingKey());
                ps.setString(6, integration.getSecret());
                ps.setString(7, integration.getType().name());
                ps.setBoolean(8, integration.isEnabled());
                ps.setBoolean(9, integration.isRemote());
                ps.setBoolean(10, integration.isAllowCreateDevicesOrAssets());
                ps.setObject(11, integration.getDefaultConverterId() != null ? integration.getDefaultConverterId().getId() : null, Types.OTHER);
                ps.setObject(12, integration.getDownlinkConverterId() != null ? integration.getDownlinkConverterId().getId() : null, Types.OTHER);
                ps.setBoolean(13, integration.isDebugMode());
                ps.setString(14, integration.getConfiguration() != null ? JacksonUtil.toString(integration.getConfiguration()) : "{}");
                ps.setString(15, integration.getAdditionalInfo());
                ps.setLong(16, 1L);
                return ps;
            });
            integration.setVersion(1L);
        } else {
            jdbc.update("UPDATE integration SET name=?, routing_key=?, secret=?, type=?, enabled=?, is_remote=?, " +
                            "allow_create_devices_or_assets=?, default_converter_id=?, downlink_converter_id=?, " +
                            "debug_mode=?, configuration=?::jsonb, additional_info=?, version=version+1 " +
                            "WHERE id=? AND tenant_id=?",
                    integration.getName(), integration.getRoutingKey(), integration.getSecret(),
                    integration.getType().name(), integration.isEnabled(), integration.isRemote(),
                    integration.isAllowCreateDevicesOrAssets(),
                    integration.getDefaultConverterId() != null ? integration.getDefaultConverterId().getId() : null,
                    integration.getDownlinkConverterId() != null ? integration.getDownlinkConverterId().getId() : null,
                    integration.isDebugMode(),
                    integration.getConfiguration() != null ? JacksonUtil.toString(integration.getConfiguration()) : "{}",
                    integration.getAdditionalInfo(),
                    integration.getId().getId(), integration.getTenantId().getId());
        }
        return integration;
    }

    @Override
    public Integration findById(TenantId tenantId, IntegrationId id) {
        List<Integration> rows = jdbc.query("SELECT * FROM integration WHERE id=? AND tenant_id=?",
                integrationMapper, id.getId(), tenantId.getId());
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public Integration findByRoutingKey(String routingKey) {
        List<Integration> rows = jdbc.query("SELECT * FROM integration WHERE routing_key = ?",
                integrationMapper, routingKey);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public PageData<Integration> findByTenantId(TenantId tenantId, PageLink pageLink) {
        int limit = pageLink.getPageSize();
        int offset = pageLink.getPage() * pageLink.getPageSize();
        List<Integration> rows = jdbc.query("SELECT * FROM integration WHERE tenant_id=? ORDER BY created_time DESC LIMIT ? OFFSET ?",
                integrationMapper, tenantId.getId(), limit, offset);
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM integration WHERE tenant_id=?",
                Integer.class, tenantId.getId());
        long t = total == null ? 0 : total;
        return new PageData<>(rows, (int) Math.ceil((double) t / limit), t, offset + rows.size() < t);
    }

    @Override
    public List<Integration> findEnabled() {
        return jdbc.query("SELECT * FROM integration WHERE enabled = TRUE", integrationMapper);
    }

    @Override
    public void deleteById(TenantId tenantId, IntegrationId id) {
        jdbc.update("DELETE FROM integration WHERE id=? AND tenant_id=?", id.getId(), tenantId.getId());
    }

    @Override
    public Converter saveConverter(Converter converter) {
        if (converter.getId() == null) {
            converter.setId(new ConverterId(UUID.randomUUID()));
            converter.setCreatedTime(System.currentTimeMillis());
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO converter (id, created_time, tenant_id, name, type, debug_mode, configuration, additional_info, version) " +
                                "VALUES (?,?,?,?,?,?,?::jsonb,?,?)");
                ps.setObject(1, converter.getId().getId());
                ps.setLong(2, converter.getCreatedTime());
                ps.setObject(3, converter.getTenantId().getId());
                ps.setString(4, converter.getName());
                ps.setString(5, converter.getType().name());
                ps.setBoolean(6, converter.isDebugMode());
                ps.setString(7, converter.getConfiguration() != null ? JacksonUtil.toString(converter.getConfiguration()) : "{}");
                ps.setString(8, converter.getAdditionalInfo());
                ps.setLong(9, 1L);
                return ps;
            });
            converter.setVersion(1L);
        } else {
            jdbc.update("UPDATE converter SET name=?, type=?, debug_mode=?, configuration=?::jsonb, additional_info=?, version=version+1 " +
                            "WHERE id=? AND tenant_id=?",
                    converter.getName(), converter.getType().name(), converter.isDebugMode(),
                    converter.getConfiguration() != null ? JacksonUtil.toString(converter.getConfiguration()) : "{}",
                    converter.getAdditionalInfo(),
                    converter.getId().getId(), converter.getTenantId().getId());
        }
        return converter;
    }

    @Override
    public Converter findConverterById(TenantId tenantId, ConverterId id) {
        List<Converter> rows = jdbc.query("SELECT * FROM converter WHERE id=? AND tenant_id=?",
                converterMapper, id.getId(), tenantId.getId());
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public PageData<Converter> findConvertersByTenantId(TenantId tenantId, PageLink pageLink) {
        int limit = pageLink.getPageSize();
        int offset = pageLink.getPage() * pageLink.getPageSize();
        List<Converter> rows = jdbc.query("SELECT * FROM converter WHERE tenant_id=? ORDER BY name ASC LIMIT ? OFFSET ?",
                converterMapper, tenantId.getId(), limit, offset);
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM converter WHERE tenant_id=?",
                Integer.class, tenantId.getId());
        long t = total == null ? 0 : total;
        return new PageData<>(rows, (int) Math.ceil((double) t / limit), t, offset + rows.size() < t);
    }

    @Override
    public void deleteConverterById(TenantId tenantId, ConverterId id) {
        jdbc.update("DELETE FROM converter WHERE id=? AND tenant_id=?", id.getId(), tenantId.getId());
    }

    private Integration mapIntegration(ResultSet rs) throws SQLException {
        Integration integ = new Integration(new IntegrationId((UUID) rs.getObject("id")));
        integ.setCreatedTime(rs.getLong("created_time"));
        integ.setTenantId(TenantId.fromUUID((UUID) rs.getObject("tenant_id")));
        integ.setName(rs.getString("name"));
        integ.setRoutingKey(rs.getString("routing_key"));
        integ.setSecret(rs.getString("secret"));
        integ.setType(IntegrationType.valueOf(rs.getString("type")));
        integ.setEnabled(rs.getBoolean("enabled"));
        integ.setRemote(rs.getBoolean("is_remote"));
        integ.setAllowCreateDevicesOrAssets(rs.getBoolean("allow_create_devices_or_assets"));
        UUID dc = (UUID) rs.getObject("default_converter_id");
        if (dc != null) integ.setDefaultConverterId(new ConverterId(dc));
        UUID dlc = (UUID) rs.getObject("downlink_converter_id");
        if (dlc != null) integ.setDownlinkConverterId(new ConverterId(dlc));
        integ.setDebugMode(rs.getBoolean("debug_mode"));
        String cfg = rs.getString("configuration");
        if (cfg != null) integ.setConfiguration(JacksonUtil.toJsonNode(cfg));
        integ.setAdditionalInfo(rs.getString("additional_info"));
        integ.setVersion(rs.getLong("version"));
        return integ;
    }

    private Converter mapConverter(ResultSet rs) throws SQLException {
        Converter c = new Converter(new ConverterId((UUID) rs.getObject("id")));
        c.setCreatedTime(rs.getLong("created_time"));
        c.setTenantId(TenantId.fromUUID((UUID) rs.getObject("tenant_id")));
        c.setName(rs.getString("name"));
        c.setType(Converter.ConverterType.valueOf(rs.getString("type")));
        c.setDebugMode(rs.getBoolean("debug_mode"));
        String cfg = rs.getString("configuration");
        if (cfg != null) c.setConfiguration(JacksonUtil.toJsonNode(cfg));
        c.setAdditionalInfo(rs.getString("additional_info"));
        c.setVersion(rs.getLong("version"));
        return c;
    }
}
