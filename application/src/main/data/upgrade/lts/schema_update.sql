--
-- Copyright © 2016-2026 The Thingsboard Authors
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.
--

-- LTS cumulative schema update file.
-- All statements must be idempotent (use IF NOT EXISTS, ADD COLUMN IF NOT EXISTS, DO $$ ... END $$ guards, etc.).
-- This file is executed by SystemPatchApplier on every version increase within the LTS family.

-- CALCULATED FIELD ADDITIONAL INFO ADDITION START

ALTER TABLE calculated_field ADD COLUMN IF NOT EXISTS additional_info varchar;

-- CALCULATED FIELD ADDITIONAL INFO ADDITION END

-- PE-EQUIVALENT FEATURE TABLES (community-build additions) START

CREATE TABLE IF NOT EXISTS role (
    id uuid NOT NULL CONSTRAINT role_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    tenant_id uuid NOT NULL,
    customer_id uuid,
    name varchar(255) NOT NULL,
    type varchar(32) NOT NULL,
    permissions jsonb NOT NULL,
    additional_info varchar,
    external_id uuid,
    version bigint DEFAULT 1,
    CONSTRAINT role_name_unq_key UNIQUE (tenant_id, name),
    CONSTRAINT role_external_id_unq_key UNIQUE (tenant_id, external_id)
);

CREATE TABLE IF NOT EXISTS entity_group_permission (
    id uuid NOT NULL CONSTRAINT entity_group_permission_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    tenant_id uuid NOT NULL,
    role_id uuid NOT NULL,
    user_group_id uuid,
    entity_group_id uuid,
    entity_id uuid,
    entity_type varchar(32),
    operations jsonb NOT NULL,
    is_public boolean DEFAULT false,
    CONSTRAINT egp_role_fk FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_entity_group_permission_tenant ON entity_group_permission(tenant_id);
CREATE INDEX IF NOT EXISTS idx_entity_group_permission_entity ON entity_group_permission(entity_id, entity_type);

CREATE TABLE IF NOT EXISTS user_role (
    id uuid NOT NULL CONSTRAINT user_role_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    tenant_id uuid NOT NULL,
    user_id uuid NOT NULL,
    role_id uuid NOT NULL,
    customer_id uuid,
    CONSTRAINT user_role_unq_key UNIQUE (user_id, role_id, customer_id),
    CONSTRAINT ur_user_fk FOREIGN KEY (user_id) REFERENCES tb_user(id) ON DELETE CASCADE,
    CONSTRAINT ur_role_fk FOREIGN KEY (role_id) REFERENCES role(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_user_role_user ON user_role(user_id);

CREATE TABLE IF NOT EXISTS scheduled_event (
    id uuid NOT NULL CONSTRAINT scheduled_event_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    tenant_id uuid NOT NULL,
    customer_id uuid,
    name varchar(255) NOT NULL,
    type varchar(64) NOT NULL,
    schedule jsonb NOT NULL,
    configuration jsonb NOT NULL,
    originator_id uuid,
    originator_type varchar(32),
    enabled boolean DEFAULT true,
    next_fire_time bigint,
    last_fire_time bigint,
    additional_info varchar,
    external_id uuid,
    version bigint DEFAULT 1,
    CONSTRAINT scheduled_event_name_unq_key UNIQUE (tenant_id, name),
    CONSTRAINT scheduled_event_external_id_unq_key UNIQUE (tenant_id, external_id)
);

CREATE INDEX IF NOT EXISTS idx_scheduled_event_fire ON scheduled_event(enabled, next_fire_time);

CREATE TABLE IF NOT EXISTS report_config (
    id uuid NOT NULL CONSTRAINT report_config_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    tenant_id uuid NOT NULL,
    customer_id uuid,
    name varchar(255) NOT NULL,
    dashboard_id uuid NOT NULL,
    state_id varchar(255),
    format varchar(16) DEFAULT 'PDF',
    timezone varchar(64),
    recipients jsonb NOT NULL,
    schedule jsonb,
    use_dashboard_timewindow boolean DEFAULT true,
    timewindow jsonb,
    last_run_status varchar(32),
    last_run_time bigint,
    last_run_error varchar,
    additional_info varchar,
    external_id uuid,
    version bigint DEFAULT 1,
    CONSTRAINT report_config_name_unq_key UNIQUE (tenant_id, name),
    CONSTRAINT report_config_external_id_unq_key UNIQUE (tenant_id, external_id)
);

CREATE TABLE IF NOT EXISTS converter (
    id uuid NOT NULL CONSTRAINT converter_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    tenant_id uuid NOT NULL,
    name varchar(255) NOT NULL,
    type varchar(32) NOT NULL,
    debug_mode boolean DEFAULT false,
    configuration jsonb NOT NULL,
    additional_info varchar,
    external_id uuid,
    version bigint DEFAULT 1,
    CONSTRAINT converter_name_unq_key UNIQUE (tenant_id, name),
    CONSTRAINT converter_external_id_unq_key UNIQUE (tenant_id, external_id)
);

CREATE TABLE IF NOT EXISTS integration (
    id uuid NOT NULL CONSTRAINT integration_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    tenant_id uuid NOT NULL,
    name varchar(255) NOT NULL,
    routing_key varchar(255) NOT NULL,
    secret varchar(255),
    type varchar(64) NOT NULL,
    enabled boolean DEFAULT true,
    is_remote boolean DEFAULT false,
    allow_create_devices_or_assets boolean DEFAULT true,
    default_converter_id uuid,
    downlink_converter_id uuid,
    debug_mode boolean DEFAULT false,
    configuration jsonb NOT NULL,
    additional_info varchar,
    external_id uuid,
    version bigint DEFAULT 1,
    CONSTRAINT integration_name_unq_key UNIQUE (tenant_id, name),
    CONSTRAINT integration_routing_key_unq_key UNIQUE (routing_key),
    CONSTRAINT integration_external_id_unq_key UNIQUE (tenant_id, external_id),
    CONSTRAINT integration_default_converter_fk FOREIGN KEY (default_converter_id) REFERENCES converter(id),
    CONSTRAINT integration_downlink_converter_fk FOREIGN KEY (downlink_converter_id) REFERENCES converter(id)
);

CREATE INDEX IF NOT EXISTS idx_integration_tenant_enabled ON integration(tenant_id, enabled);

CREATE TABLE IF NOT EXISTS payload_codec_library (
    id uuid NOT NULL CONSTRAINT payload_codec_library_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    name varchar(255) NOT NULL,
    vendor varchar(255),
    model varchar(255),
    category varchar(64),
    description varchar,
    decoder_type varchar(16) DEFAULT 'JS',
    decoder_function varchar(10000000) NOT NULL,
    sample_input varchar(100000),
    sample_output varchar(100000),
    documentation_url varchar(1024),
    is_system boolean DEFAULT true,
    tenant_id uuid,
    additional_info jsonb,
    CONSTRAINT payload_codec_library_name_unq_key UNIQUE (tenant_id, name)
);

CREATE INDEX IF NOT EXISTS idx_payload_codec_library_vendor ON payload_codec_library(vendor);
CREATE INDEX IF NOT EXISTS idx_payload_codec_library_category ON payload_codec_library(category);

CREATE TABLE IF NOT EXISTS solution_template (
    id uuid NOT NULL CONSTRAINT solution_template_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    name varchar(255) NOT NULL,
    title varchar(255),
    category varchar(64),
    description varchar,
    preview_image varchar(10000000),
    bundle jsonb NOT NULL,
    is_system boolean DEFAULT true,
    tenant_id uuid,
    additional_info jsonb,
    CONSTRAINT solution_template_name_unq_key UNIQUE (tenant_id, name)
);

CREATE INDEX IF NOT EXISTS idx_solution_template_category ON solution_template(category);

CREATE TABLE IF NOT EXISTS solution_install_record (
    id uuid NOT NULL CONSTRAINT solution_install_record_pkey PRIMARY KEY,
    created_time bigint NOT NULL,
    tenant_id uuid NOT NULL,
    template_id uuid NOT NULL,
    template_name varchar(255) NOT NULL,
    status varchar(32) NOT NULL,
    installed_entities jsonb,
    error_message varchar,
    CONSTRAINT sir_template_fk FOREIGN KEY (template_id) REFERENCES solution_template(id) ON DELETE CASCADE
);

-- PE-EQUIVALENT FEATURE TABLES END
