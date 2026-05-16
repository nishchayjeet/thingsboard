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
package org.thingsboard.server.dao.solution;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.thingsboard.common.util.JacksonUtil;
import org.thingsboard.server.common.data.id.SolutionTemplateId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.solution.SolutionTemplate;

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
public class SolutionTemplateServiceImpl implements SolutionTemplateService {

    private final JdbcTemplate jdbc;

    private final RowMapper<SolutionTemplate> mapper = (rs, i) -> map(rs);

    @Override
    public SolutionTemplate save(SolutionTemplate template) {
        if (template.getId() == null) {
            template.setId(new SolutionTemplateId(UUID.randomUUID()));
            template.setCreatedTime(System.currentTimeMillis());
            jdbc.update(con -> {
                PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO solution_template (id, created_time, name, title, category, description, " +
                                "preview_image, bundle, is_system, tenant_id, additional_info) " +
                                "VALUES (?,?,?,?,?,?,?,?::jsonb,?,?,?::jsonb)");
                ps.setObject(1, template.getId().getId());
                ps.setLong(2, template.getCreatedTime());
                ps.setString(3, template.getName());
                ps.setString(4, template.getTitle());
                ps.setString(5, template.getCategory());
                ps.setString(6, template.getDescription());
                ps.setString(7, template.getPreviewImage());
                ps.setString(8, template.getBundle() != null ? JacksonUtil.toString(template.getBundle()) : "{}");
                ps.setBoolean(9, template.isSystem());
                ps.setObject(10, template.getTenantId() != null ? template.getTenantId().getId() : null, Types.OTHER);
                ps.setString(11, template.getAdditionalInfo() != null ? JacksonUtil.toString(template.getAdditionalInfo()) : null);
                return ps;
            });
        } else {
            jdbc.update("UPDATE solution_template SET name=?, title=?, category=?, description=?, preview_image=?, " +
                            "bundle=?::jsonb, additional_info=?::jsonb WHERE id=?",
                    template.getName(), template.getTitle(), template.getCategory(), template.getDescription(),
                    template.getPreviewImage(),
                    template.getBundle() != null ? JacksonUtil.toString(template.getBundle()) : "{}",
                    template.getAdditionalInfo() != null ? JacksonUtil.toString(template.getAdditionalInfo()) : null,
                    template.getId().getId());
        }
        return template;
    }

    @Override
    public SolutionTemplate findById(SolutionTemplateId id) {
        List<SolutionTemplate> rows = jdbc.query("SELECT * FROM solution_template WHERE id=?", mapper, id.getId());
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public PageData<SolutionTemplate> findAll(TenantId tenantId, String category, PageLink pageLink) {
        StringBuilder where = new StringBuilder("WHERE (is_system = TRUE OR tenant_id = ?)");
        List<Object> args = new ArrayList<>();
        args.add(tenantId != null ? tenantId.getId() : null);
        if (category != null && !category.isBlank()) { where.append(" AND category = ?"); args.add(category); }
        int limit = pageLink.getPageSize();
        int offset = pageLink.getPage() * pageLink.getPageSize();
        args.add(limit); args.add(offset);
        List<SolutionTemplate> rows = jdbc.query("SELECT * FROM solution_template " + where + " ORDER BY category, name LIMIT ? OFFSET ?",
                mapper, args.toArray());
        args.remove(args.size() - 1); args.remove(args.size() - 1);
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM solution_template " + where, Integer.class, args.toArray());
        long t = total == null ? 0 : total;
        return new PageData<>(rows, (int) Math.ceil((double) t / limit), t, offset + rows.size() < t);
    }

    @Override
    public List<String> listCategories() {
        return jdbc.queryForList("SELECT DISTINCT category FROM solution_template WHERE category IS NOT NULL ORDER BY category", String.class);
    }

    @Override
    public void delete(SolutionTemplateId id) {
        jdbc.update("DELETE FROM solution_template WHERE id=? AND is_system = FALSE", id.getId());
    }

    private SolutionTemplate map(ResultSet rs) throws SQLException {
        SolutionTemplate t = new SolutionTemplate(new SolutionTemplateId((UUID) rs.getObject("id")));
        t.setCreatedTime(rs.getLong("created_time"));
        t.setName(rs.getString("name"));
        t.setTitle(rs.getString("title"));
        t.setCategory(rs.getString("category"));
        t.setDescription(rs.getString("description"));
        t.setPreviewImage(rs.getString("preview_image"));
        String bundle = rs.getString("bundle");
        if (bundle != null) t.setBundle(JacksonUtil.toJsonNode(bundle));
        t.setSystem(rs.getBoolean("is_system"));
        UUID tenant = (UUID) rs.getObject("tenant_id");
        if (tenant != null) t.setTenantId(TenantId.fromUUID(tenant));
        String addl = rs.getString("additional_info");
        if (addl != null) t.setAdditionalInfo(JacksonUtil.toJsonNode(addl));
        return t;
    }
}
