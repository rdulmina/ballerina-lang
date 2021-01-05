/*
 * Copyright (c) 2020, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ballerinalang.langserver.config;

import com.google.gson.JsonElement;

/**
 * Represents a ballerina client config change listener.
 */
public interface ClientConfigListener {
    /**
     * Callback method for configuration changes.
     *
     * @param oldConfig old configuration
     * @param newConfig new configuration
     */
    default void didChangeConfig(LSClientConfig oldConfig, LSClientConfig newConfig) {
        // do nothing
    }

    /**
     * Callback method for configuration changes.
     *
     * @param oldConfigJson old configuration json
     * @param newConfigJson new configuration json
     */
    default void didChangeJson(JsonElement oldConfigJson, JsonElement newConfigJson) {
        // do nothing
    }
}
