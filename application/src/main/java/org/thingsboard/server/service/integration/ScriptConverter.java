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
package org.thingsboard.server.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.thingsboard.server.common.data.integration.Converter;

import java.util.Map;

/**
 * Converter dispatch point. v1 hands the raw payload through unchanged — actual decoding happens in
 * downstream rule-engine script nodes which use the platform's existing JsInvokeService / TbelInvokeService
 * infrastructure (sandboxed, clustered).
 *
 * To bypass rule-engine-side decoding and run the converter inline, wire {@code JsInvokeService} into
 * this class and call {@code eval(decoder)} + {@code invokeScript(scriptId, payload, metadata)} here.
 * Inline execution costs a script-compile per invocation unless cached.
 */
@Slf4j
public final class ScriptConverter {

    private ScriptConverter() {}

    public static JsonNode runUplink(Converter converter, JsonNode payload, Map<String, String> metadata) {
        if (converter == null || converter.getConfiguration() == null
                || !converter.getConfiguration().hasNonNull("decoder")) {
            return payload;
        }
        // Inline execution intentionally deferred to rule-engine side.
        // Adding metadata so downstream rule nodes know which converter is in play.
        if (metadata != null && converter.getId() != null) {
            metadata.put("converterId", converter.getId().getId().toString());
            metadata.put("converterName", converter.getName());
        }
        return payload;
    }
}
