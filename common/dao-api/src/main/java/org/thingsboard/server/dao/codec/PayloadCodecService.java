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

import org.thingsboard.server.common.data.codec.PayloadCodec;
import org.thingsboard.server.common.data.id.PayloadCodecId;
import org.thingsboard.server.common.data.id.TenantId;
import org.thingsboard.server.common.data.page.PageData;
import org.thingsboard.server.common.data.page.PageLink;

import java.util.List;

public interface PayloadCodecService {

    PayloadCodec save(PayloadCodec codec);

    PayloadCodec findById(PayloadCodecId id);

    PageData<PayloadCodec> findAll(TenantId tenantId, String category, String vendor, PageLink pageLink);

    List<String> listVendors();

    List<String> listCategories();

    void delete(PayloadCodecId id);
}
