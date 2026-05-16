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
package org.thingsboard.server.dao.codec;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.codec.PayloadCodec;
import org.thingsboard.server.common.data.id.PayloadCodecId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;

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
public class PayloadCodecServiceImpl implements PayloadCodecService {

    private final JdbcTemplate jdbc;

    private final RowMapper<PayloadCodec> mapper = (rs, i) -> map(rs);

    @Override
    public PayloadCodec save(PayloadCodec codec) {
        if (codec.getId() == null) {
            codec.setId(new PayloadCodecId(UUID.randomUUID()));
            codec.setCreatedTime(System.currentTimeMillis());
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO payload_codec_library (id, created_time, name, vendor, model, category, description, " +
                                "decoder_type, decoder_function, sample_input, sample_output, documentation_url, is_system, " +
                                "tenant_id, additional_info) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb)");
                ps.setObject(1, codec.getId().getId());
                ps.setLong(2, codec.getCreatedTime());
                ps.setString(3, codec.getName());
                ps.setString(4, codec.getVendor());
                ps.setString(5, codec.getModel());
                ps.setString(6, codec.getCategory());
                ps.setString(7, codec.getDescription());
                ps.setString(8, codec.getDecoderType().name());
                ps.setString(9, codec.getDecoderFunction());
                ps.setString(10, codec.getSampleInput());
                ps.setString(11, codec.getSampleOutput());
                ps.setString(12, codec.getDocumentationUrl());
                ps.setBoolean(13, codec.isSystem());
                ps.setObject(14, codec.getTenantId() != null ? codec.getTenantId().getId() : null, Types.OTHER);
                ps.setString(15, codec.getAdditionalInfo() != null ? JacksonUtil.toString(codec.getAdditionalInfo()) : null);
                return ps;
            });
        } else {
            jdbc.update("UPDATE payload_codec_library SET name=?, vendor=?, model=?, category=?, description=?, " +
                            "decoder_type=?, decoder_function=?, sample_input=?, sample_output=?, documentation_url=?, additional_info=?::jsonb " +
                            "WHERE id=?",
                    codec.getName(), codec.getVendor(), codec.getModel(), codec.getCategory(), codec.getDescription(),
                    codec.getDecoderType().name(), codec.getDecoderFunction(), codec.getSampleInput(), codec.getSampleOutput(),
                    codec.getDocumentationUrl(),
                    codec.getAdditionalInfo() != null ? JacksonUtil.toString(codec.getAdditionalInfo()) : null,
                    codec.getId().getId());
        }
        return codec;
    }

    @Override
    public PayloadCodec findById(PayloadCodecId id) {
        List<PayloadCodec> rows = jdbc.query("SELECT * FROM payload_codec_library WHERE id=?", mapper, id.getId());
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public PageData<PayloadCodec> findAll(TenantId tenantId, String category, String vendor, PageLink pageLink) {
        StringBuilder where = new StringBuilder("WHERE (is_system = TRUE OR tenant_id = ?)");
        List<Object> args = new ArrayList<>();
        args.add(tenantId != null ? tenantId.getId() : null);
        if (category != null && !category.isBlank()) { where.append(" AND category = ?"); args.add(category); }
        if (vendor != null && !vendor.isBlank()) { where.append(" AND vendor = ?"); args.add(vendor); }
        int limit = pageLink.getPageSize();
        int offset = pageLink.getPage() * pageLink.getPageSize();
        args.add(limit); args.add(offset);
        List<PayloadCodec> rows = jdbc.query("SELECT * FROM payload_codec_library " + where + " ORDER BY vendor, name LIMIT ? OFFSET ?",
                mapper, args.toArray());
        args.remove(args.size() - 1); args.remove(args.size() - 1);
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM payload_codec_library " + where, Integer.class, args.toArray());
        long t = total == null ? 0 : total;
        return new PageData<>(rows, (int) Math.ceil((double) t / limit), t, offset + rows.size() < t);
    }

    @Override
    public List<String> listVendors() {
        return jdbc.queryForList("SELECT DISTINCT vendor FROM payload_codec_library WHERE vendor IS NOT NULL ORDER BY vendor", String.class);
    }

    @Override
    public List<String> listCategories() {
        return jdbc.queryForList("SELECT DISTINCT category FROM payload_codec_library WHERE category IS NOT NULL ORDER BY category", String.class);
    }

    @Override
    public void delete(PayloadCodecId id) {
        jdbc.update("DELETE FROM payload_codec_library WHERE id=? AND is_system = FALSE", id.getId());
    }

    private PayloadCodec map(ResultSet rs) throws SQLException {
        PayloadCodec c = new PayloadCodec(new PayloadCodecId((UUID) rs.getObject("id")));
        c.setCreatedTime(rs.getLong("created_time"));
        c.setName(rs.getString("name"));
        c.setVendor(rs.getString("vendor"));
        c.setModel(rs.getString("model"));
        c.setCategory(rs.getString("category"));
        c.setDescription(rs.getString("description"));
        c.setDecoderType(PayloadCodec.DecoderType.valueOf(rs.getString("decoder_type")));
        c.setDecoderFunction(rs.getString("decoder_function"));
        c.setSampleInput(rs.getString("sample_input"));
        c.setSampleOutput(rs.getString("sample_output"));
        c.setDocumentationUrl(rs.getString("documentation_url"));
        c.setSystem(rs.getBoolean("is_system"));
        UUID tenant = (UUID) rs.getObject("tenant_id");
        if (tenant != null) c.setTenantId(TenantId.fromUUID(tenant));
        String addl = rs.getString("additional_info");
        if (addl != null) c.setAdditionalInfo(JacksonUtil.toJsonNode(addl));
        return c;
    }
}
