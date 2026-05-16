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

import org.thingsboard.server.common.data.id.IntegrationId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.integration.Converter;
import org.thingsboard.server.common.data.id.ConverterId;
import org.thingsboard.server.common.data.integration.Integration;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;

import java.util.List;

public interface IntegrationService {

    Integration saveIntegration(Integration integration);

    Integration findById(TenantId tenantId, IntegrationId id);

    Integration findByRoutingKey(String routingKey);

    PageData<Integration> findByTenantId(TenantId tenantId, PageLink pageLink);

    List<Integration> findEnabled();

    void deleteById(TenantId tenantId, IntegrationId id);

    // --- Converters ---

    Converter saveConverter(Converter converter);

    Converter findConverterById(TenantId tenantId, ConverterId id);

    PageData<Converter> findConvertersByTenantId(TenantId tenantId, PageLink pageLink);

    void deleteConverterById(TenantId tenantId, ConverterId id);
}
