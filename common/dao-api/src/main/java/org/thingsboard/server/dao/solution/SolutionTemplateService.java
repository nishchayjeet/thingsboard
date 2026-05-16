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

import org.thingsboard.server.common.data.id.SolutionTemplateId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;
import org.thingsboard.server.common.data.solution.SolutionTemplate;

import java.util.List;

public interface SolutionTemplateService {

    SolutionTemplate save(SolutionTemplate template);

    SolutionTemplate findById(SolutionTemplateId id);

    PageData<SolutionTemplate> findAll(TenantId tenantId, String category, PageLink pageLink);

    List<String> listCategories();

    void delete(SolutionTemplateId id);
}
