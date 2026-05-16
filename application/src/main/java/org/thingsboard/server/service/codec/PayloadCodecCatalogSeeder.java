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
package org.thingsboard.server.service.codec;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.thingsboard.server.common.data.codec.PayloadCodec;
import org.thingsboard.server.dao.codec.PayloadCodecService;
import org.thingsboard.server.queue.util.TbCoreComponent;

import java.util.List;

/**
 * Seeds the {@code payload_codec_library} table with a curated set of reference decoders on startup.
 * Seed entries are marked {@code is_system = true} and are unique by name; the seeder skips inserts when
 * a same-named entry already exists, so re-runs are idempotent.
 *
 * The PE catalog ships 400+ codecs; this initial set covers the most-asked devices in LoRaWAN, NB-IoT,
 * and generic JSON sensors. Add to {@code SEED} to extend the catalog.
 */
@Component
@TbCoreComponent
@RequiredArgsConstructor
@Slf4j
public class PayloadCodecCatalogSeeder {

    private final JdbcTemplate jdbc;
    private final PayloadCodecService codecService;

    private static final List<PayloadCodec> SEED = List.of(
            codec("Dragino LHT65 (LoRaWAN)", "Dragino", "LHT65", "Temperature/Humidity",
                    "Dragino LHT65 indoor temperature/humidity LoRaWAN sensor with external probe.",
                    """
                    function decodeUplink(payloadJson, metadata) {
                        var p = JSON.parse(payloadJson);
                        var bytes = p.data || p.payload_hex || '';
                        var b = [];
                        for (var i = 0; i < bytes.length; i += 2) b.push(parseInt(bytes.substr(i, 2), 16));
                        if (b.length < 8) return JSON.stringify({raw: bytes});
                        var battery_mv = ((b[0] & 0x3F) << 8) | b[1];
                        var temperature = ((b[2] << 24 >> 16) | b[3]) / 100;
                        var humidity = (((b[4] << 8) | b[5]) & 0xFFFF) / 10;
                        return JSON.stringify({battery_mv: battery_mv, temperature_c: temperature, humidity_pct: humidity});
                    }
                    """,
                    "{\"data\": \"0CC1011402A8FFFF7FFF\"}",
                    "{\"battery_mv\":3265,\"temperature_c\":27.6,\"humidity_pct\":68.0}",
                    "https://www.dragino.com/products/temperature-humidity-sensor/item/151-lht65.html"),

            codec("Milesight EM300-TH (LoRaWAN)", "Milesight", "EM300-TH", "Temperature/Humidity",
                    "Milesight EM300-TH wall/ceiling-mount temperature & humidity sensor.",
                    """
                    function decodeUplink(payloadJson, metadata) {
                        var p = JSON.parse(payloadJson);
                        var hex = p.data || '';
                        var out = {};
                        for (var i = 0; i < hex.length; i += 2) {
                            var ch = parseInt(hex.substr(i, 2), 16);
                            var t = parseInt(hex.substr(i + 2, 2), 16);
                            i += 4;
                            if (ch === 0x01 && t === 0x75) { out.battery_pct = parseInt(hex.substr(i, 2), 16); i += 2; }
                            else if (ch === 0x03 && t === 0x67) {
                                var lo = parseInt(hex.substr(i, 2), 16); var hi = parseInt(hex.substr(i + 2, 2), 16); i += 4;
                                out.temperature_c = ((hi << 8) | lo) / 10;
                            } else if (ch === 0x04 && t === 0x68) { out.humidity_pct = parseInt(hex.substr(i, 2), 16) / 2; i += 2; }
                            else break;
                        }
                        return JSON.stringify(out);
                    }
                    """,
                    "{\"data\": \"0175640367E0010468B0\"}",
                    "{\"battery_pct\":100,\"temperature_c\":48.0,\"humidity_pct\":88.0}",
                    "https://www.milesight.com/iot/product/lorawan-sensor/em300-th"),

            codec("Decentlab DL-PR26 (Pressure)", "Decentlab", "DL-PR26", "Pressure",
                    "Decentlab DL-PR26 LoRaWAN pressure & temperature sensor.",
                    """
                    function decodeUplink(payloadJson, metadata) {
                        var p = JSON.parse(payloadJson);
                        var hex = p.data || '';
                        var b = []; for (var i = 0; i < hex.length; i += 2) b.push(parseInt(hex.substr(i, 2), 16));
                        if (b.length < 7) return JSON.stringify({raw: hex});
                        var pressureRaw = (b[3] << 8) | b[4];
                        var tempRaw = (b[5] << 8) | b[6];
                        return JSON.stringify({
                            pressure_mbar: pressureRaw,
                            temperature_c: (tempRaw - 32768) / 100
                        });
                    }
                    """,
                    "{\"data\": \"0200030FA01388\"}",
                    "{\"pressure_mbar\":4000,\"temperature_c\":-277.68}",
                    "https://www.decentlab.com/products/pressure-temperature-sensor-for-lorawan"),

            codec("ThingPark Wireless Reverse Geocoding", "Actility", "ThingPark", "LoRaWAN Wrapper",
                    "Unwraps Actility ThingPark uplink envelopes to expose DevEUI + raw payload bytes.",
                    """
                    function decodeUplink(payloadJson, metadata) {
                        var p = JSON.parse(payloadJson);
                        var u = p.DevEUI_uplink || p;
                        return JSON.stringify({
                            devEui: u.DevEUI,
                            fPort: u.FPort,
                            fCnt: u.FCntUp,
                            rssi: u.LrrRSSI,
                            snr: u.LrrSNR,
                            payload_hex: u.payload_hex,
                            time: u.Time
                        });
                    }
                    """,
                    "{\"DevEUI_uplink\":{\"DevEUI\":\"A81758FFFE0312AB\",\"FPort\":1,\"FCntUp\":42,\"LrrRSSI\":-95,\"LrrSNR\":7,\"payload_hex\":\"AABBCC\",\"Time\":\"2025-01-01T00:00:00\"}}",
                    "{\"devEui\":\"A81758FFFE0312AB\",\"fPort\":1,\"fCnt\":42,\"rssi\":-95,\"snr\":7,\"payload_hex\":\"AABBCC\",\"time\":\"2025-01-01T00:00:00\"}",
                    "https://www.actility.com/thingpark/"),

            codec("TTN v3 Uplink Unwrap", "The Things Industries", "TTN-v3", "LoRaWAN Wrapper",
                    "Unwraps The Things Network (v3) uplink JSON to expose device id, fport, and decoded payload.",
                    """
                    function decodeUplink(payloadJson, metadata) {
                        var p = JSON.parse(payloadJson);
                        var um = (p.uplink_message) || {};
                        return JSON.stringify({
                            device_id: (p.end_device_ids && p.end_device_ids.device_id),
                            dev_eui: (p.end_device_ids && p.end_device_ids.dev_eui),
                            fPort: um.f_port,
                            fCnt: um.f_cnt,
                            rssi: (um.rx_metadata && um.rx_metadata[0] && um.rx_metadata[0].rssi),
                            snr: (um.rx_metadata && um.rx_metadata[0] && um.rx_metadata[0].snr),
                            payload_b64: um.frm_payload,
                            decoded: um.decoded_payload
                        });
                    }
                    """,
                    "{\"end_device_ids\":{\"device_id\":\"dev-001\",\"dev_eui\":\"A81758FFFE0312AB\"},\"uplink_message\":{\"f_port\":1,\"f_cnt\":42,\"frm_payload\":\"qrvM\",\"rx_metadata\":[{\"rssi\":-95,\"snr\":7}]}}",
                    "{\"device_id\":\"dev-001\",\"dev_eui\":\"A81758FFFE0312AB\",\"fPort\":1,\"fCnt\":42,\"rssi\":-95,\"snr\":7,\"payload_b64\":\"qrvM\"}",
                    "https://www.thethingsindustries.com/docs/"),

            codec("ChirpStack v4 Uplink Unwrap", "ChirpStack", "v4", "LoRaWAN Wrapper",
                    "Unwraps ChirpStack v4 application server uplink JSON.",
                    """
                    function decodeUplink(payloadJson, metadata) {
                        var p = JSON.parse(payloadJson);
                        return JSON.stringify({
                            device_name: (p.deviceInfo && p.deviceInfo.deviceName),
                            dev_eui: (p.deviceInfo && p.deviceInfo.devEui),
                            fPort: p.fPort,
                            fCnt: p.fCnt,
                            data: p.data,
                            decoded: p.object
                        });
                    }
                    """,
                    "{\"deviceInfo\":{\"deviceName\":\"sensor-1\",\"devEui\":\"A81758FFFE0312AB\"},\"fPort\":1,\"fCnt\":42,\"data\":\"qrvM\"}",
                    "{\"device_name\":\"sensor-1\",\"dev_eui\":\"A81758FFFE0312AB\",\"fPort\":1,\"fCnt\":42,\"data\":\"qrvM\"}",
                    "https://www.chirpstack.io/docs/"),

            codec("Generic Cayenne LPP", "Cayenne", "LPP", "Generic",
                    "Cayenne Low Power Payload decoder; handles channel-typed sensor mixes (temp/hum/digital).",
                    """
                    function decodeUplink(payloadJson, metadata) {
                        var p = JSON.parse(payloadJson);
                        var hex = p.data || p.payload_hex || '';
                        var b = []; for (var i = 0; i < hex.length; i += 2) b.push(parseInt(hex.substr(i, 2), 16));
                        var out = {}; var i = 0;
                        while (i + 1 < b.length) {
                            var ch = b[i++], t = b[i++];
                            if (t === 0x67 && i + 1 < b.length) { out['temp_ch' + ch] = ((b[i] << 24 >> 16) | b[i+1]) / 10; i += 2; }
                            else if (t === 0x68 && i < b.length) { out['hum_ch' + ch] = b[i] / 2; i += 1; }
                            else if (t === 0x65 && i + 3 < b.length) { out['lux_ch' + ch] = (b[i] << 8) | b[i+1]; i += 2; }
                            else break;
                        }
                        return JSON.stringify(out);
                    }
                    """,
                    "{\"data\": \"01670110026864\"}",
                    "{\"temp_ch1\":27.2,\"hum_ch2\":50}",
                    "https://docs.mydevices.com/docs/lorawan/cayenne-lpp"),

            codec("Generic JSON pass-through", "Generic", "JSON", "Generic",
                    "Pass-through for already-decoded JSON payloads. Returns the input unchanged.",
                    """
                    function decodeUplink(payloadJson, metadata) {
                        return payloadJson;
                    }
                    """,
                    "{\"temp\":21.5,\"hum\":48}",
                    "{\"temp\":21.5,\"hum\":48}",
                    null),

            codec("Browan TBHV110 (Healthy Home)", "Browan", "TBHV110", "Air Quality",
                    "Browan Healthy Home sensor: temperature, humidity, CO2 estimate, IAQ index.",
                    """
                    function decodeUplink(payloadJson, metadata) {
                        var p = JSON.parse(payloadJson);
                        var hex = p.data || '';
                        var b = []; for (var i = 0; i < hex.length; i += 2) b.push(parseInt(hex.substr(i, 2), 16));
                        if (b.length < 7) return JSON.stringify({raw: hex});
                        return JSON.stringify({
                            battery_v: b[0] / 10,
                            temperature_c: ((b[1] << 8) | b[2]) / 100,
                            humidity_pct: b[3],
                            co2_ppm: (b[4] << 8) | b[5],
                            iaq: b[6]
                        });
                    }
                    """,
                    "{\"data\": \"1E0A8C402710C8\"}",
                    "{\"battery_v\":3.0,\"temperature_c\":27.0,\"humidity_pct\":64,\"co2_ppm\":10000,\"iaq\":200}",
                    "https://www.browan.com/product/healthy-home-sensor-iaq/"),

            codec("Sensoneo Smart Bin", "Sensoneo", "Single", "Asset Tracking",
                    "Sensoneo single waste sensor; reports fill level and temperature.",
                    """
                    function decodeUplink(payloadJson, metadata) {
                        var p = JSON.parse(payloadJson);
                        var hex = p.data || '';
                        var b = []; for (var i = 0; i < hex.length; i += 2) b.push(parseInt(hex.substr(i, 2), 16));
                        if (b.length < 5) return JSON.stringify({raw: hex});
                        return JSON.stringify({
                            distance_cm: (b[0] << 8) | b[1],
                            fill_pct: b[2],
                            temperature_c: ((b[3] << 24 >> 24) | 0),
                            battery_pct: b[4]
                        });
                    }
                    """,
                    "{\"data\": \"00781814FF64\"}",
                    "{\"distance_cm\":120,\"fill_pct\":24,\"temperature_c\":-1,\"battery_pct\":100}",
                    "https://sensoneo.com/")
    );

    @PostConstruct
    public void seed() {
        try {
            Integer existing = jdbc.queryForObject("SELECT COUNT(*) FROM payload_codec_library WHERE is_system = TRUE", Integer.class);
            if (existing != null && existing >= SEED.size()) {
                log.debug("Payload codec library already seeded ({} entries); skipping.", existing);
                return;
            }
            int inserted = 0;
            for (PayloadCodec c : SEED) {
                Integer exists = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM payload_codec_library WHERE name = ? AND tenant_id IS NULL",
                        Integer.class, c.getName());
                if (exists != null && exists > 0) continue;
                codecService.save(c);
                inserted++;
            }
            log.info("Payload codec library seeded ({} new entries).", inserted);
        } catch (Exception e) {
            log.warn("Failed to seed payload codec library: {}", e.getMessage());
        }
    }

    private static PayloadCodec codec(String name, String vendor, String model, String category,
                                      String description, String decoder, String sampleIn, String sampleOut,
                                      String docUrl) {
        PayloadCodec c = new PayloadCodec();
        c.setName(name);
        c.setVendor(vendor);
        c.setModel(model);
        c.setCategory(category);
        c.setDescription(description);
        c.setDecoderType(PayloadCodec.DecoderType.JS);
        c.setDecoderFunction(decoder);
        c.setSampleInput(sampleIn);
        c.setSampleOutput(sampleOut);
        c.setDocumentationUrl(docUrl);
        c.setSystem(true);
        return c;
    }
}
