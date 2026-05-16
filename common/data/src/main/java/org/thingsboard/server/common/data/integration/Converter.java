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
package org.thingsboard.server.common.data.integration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.thingsboard.server.common.data.BaseData;
import org.thingsboard.server.common.data.HasName;
import org.thingsboard.server.common.data.HasTenantId;
import org.thingsboard.server.common.data.HasVersion;
import org.thingsboard.server.common.data.id.ConverterId;
import org.thingsboard.server.common.data.id.TenantId;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Converter extends BaseData<ConverterId> implements HasName, HasTenantId, HasVersion {

    public enum ConverterType { UPLINK, DOWNLINK }

    private TenantId tenantId;
    private String name;
    private ConverterType type = ConverterType.UPLINK;
    private boolean debugMode = false;

    /**
     * Configuration JSON shape:
     *   {
     *     "scriptLang": "JS" | "TBEL",
     *     "decoder": "function Decode(payload, metadata) { ... }",
     *     "encoder": "function Encode(msg, metadata) { ... }"
     *   }
     */
    private JsonNode configuration;

    private String additionalInfo;
    private ConverterId externalId;
    private Long version;

    public Converter(ConverterId id) {
        super(id);
    }
}
