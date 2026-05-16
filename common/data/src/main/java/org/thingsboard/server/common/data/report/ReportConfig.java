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
package org.thingsboard.server.common.data.report;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.thingsboard.server.common.data.BaseData;
import org.thingsboard.server.common.data.HasName;
import org.thingsboard.server.common.data.HasTenantId;
import org.thingsboard.server.common.data.id.CustomerId;
import org.thingsboard.server.common.data.id.DashboardId;
import org.thingsboard.server.common.data.id.ReportConfigId;
import org.thingsboard.server.common.data.id.TenantId;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ReportConfig extends BaseData<ReportConfigId> implements HasName, HasTenantId {

    public enum Format { PDF, PNG }

    private TenantId tenantId;
    private CustomerId customerId;
    private String name;
    private DashboardId dashboardId;
    private String stateId;
    private Format format = Format.PDF;
    private String timezone;
    /** JSON array of email addresses. */
    private JsonNode recipients;
    /** Schedule JSON (same shape as ScheduledEvent schedule). */
    private JsonNode schedule;
    private boolean useDashboardTimewindow = true;
    private JsonNode timewindow;
    private String lastRunStatus;
    private Long lastRunTime;
    private String lastRunError;
    private String additionalInfo;
    private ReportConfigId externalId;

    public ReportConfig(ReportConfigId id) {
        super(id);
    }
}
