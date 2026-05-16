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
package org.thingsboard.server.common.data.wl;

import lombok.Data;

@Data
public class WhiteLabelingParams {

    private String logoImageUrl;
    private Integer logoImageHeight;
    private String appTitle;
    private Favicon favicon;
    private PaletteSettings paletteSettings;
    private String helpLinkBaseUrl;
    private Boolean enableHelpLinks;
    private Boolean showNameVersion;
    private String platformName;
    private String platformVersion;
    private String customCss;

    @Data
    public static class Favicon {
        private String url;
        private String type;
    }

    @Data
    public static class PaletteSettings {
        private Palette primaryPalette;
        private Palette accentPalette;
    }

    @Data
    public static class Palette {
        private String type;
        private String extendsPalette;
        private String colorPrimary;
        private String colorAccent;
    }
}
