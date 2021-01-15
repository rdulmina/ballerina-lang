/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerina.runtime.observability;

/**
 * All constants related to observability.
 * <p>
 * Tags in this class are used for both Metrics and Tracing. Some tags follow the standard names defined by
 * Open Tracing Specification.
 * </p>
 */
public class ObservabilityConstants {

    private ObservabilityConstants() {
    }

    public static final String KEY_OBSERVER_CONTEXT = "__observer_context__";
    public static final String UNKNOWN_SERVICE = "Unknown Service";
    public static final String UNKNOWN_RESOURCE = "Unknown Resource";

    public static final String SERVER_CONNECTOR_HTTP = "http";
    public static final String SERVER_CONNECTOR_WEBSOCKET = "ws";

    public static final String TAG_KEY_HTTP_METHOD = "http.method";
    public static final String TAG_KEY_HTTP_URL = "http.url";
    public static final String TAG_KEY_HTTP_STATUS_CODE_GROUP = "http.status_code_group";
    public static final String TAG_KEY_PROTOCOL = "protocol";

    public static final String TAG_KEY_PEER_ADDRESS = "peer.address";

    // Runtime related tags
    public static final String TAG_KEY_ERROR = "error";     // Standard boolean error tag
    public static final String TAG_KEY_ENTRYPOINT_FUNCTION_MODULE = "entrypoint.function.module";
    public static final String TAG_KEY_ENTRYPOINT_FUNCTION_POSITION = "entrypoint.function.position";
    public static final String TAG_KEY_LISTENER_NAME = "listener.name";

    // Source related tags
    public static final String TAG_KEY_SRC_MODULE = "src.module";
    public static final String TAG_KEY_SRC_POSITION = "src.position";
    public static final String TAG_KEY_SRC_RESOURCE_PATH = "src.resource.path";
    public static final String TAG_KEY_SRC_RESOURCE_ACCESSOR = "src.resource.accessor";
    public static final String TAG_KEY_SRC_OBJECT_NAME = "src.object.name";
    public static final String TAG_KEY_SRC_FUNCTION_NAME = "src.function.name";

    // Source related boolean flag tags
    public static final String TAG_KEY_IS_SRC_MAIN_FUNCTION = "src.main";
    public static final String TAG_KEY_IS_SRC_WORKER = "src.worker";
    public static final String TAG_KEY_IS_SRC_CLIENT_REMOTE = "src.client.remote";
    public static final String TAG_KEY_IS_SRC_SERVICE_REMOTE = "src.service.remote";
    public static final String TAG_KEY_IS_SRC_SERVICE_RESOURCE = "src.service.resource";

    public static final String PROPERTY_HTTP_HOST = "host";
    public static final String PROPERTY_HTTP_PORT = "port";

    public static final String PROPERTY_TRACE_PROPERTIES = "trace_properties";
    public static final String PROPERTY_KEY_HTTP_STATUS_CODE = "http.status_code";
    public static final String PROPERTY_ERROR_MESSAGE = "error_message";
    public static final String PROPERTY_BSTRUCT_ERROR = "bstruct_error";

    public static final String TAG_TRUE_VALUE = "true";
    public static final String STATUS_CODE_GROUP_SUFFIX = "xx";

    // TOML Configs
    public static final String CONFIG_TABLE_OBSERVABILITY = "b7a.observability";
    public static final String CONFIG_TABLE_METRICS = CONFIG_TABLE_OBSERVABILITY + ".metrics";
    public static final String CONFIG_TABLE_TRACING = CONFIG_TABLE_OBSERVABILITY + ".tracing";

    // Observability Configs
    public static final String CONFIG_OBSERVABILITY_ENABLED = CONFIG_TABLE_OBSERVABILITY + ".enabled";
    public static final String CONFIG_OBSERVABILITY_PROVIDER = CONFIG_TABLE_OBSERVABILITY + ".provider";

    // Metrics Configs
    public static final String CONFIG_METRICS_ENABLED = CONFIG_TABLE_METRICS + ".enabled";
    public static final String CONFIG_OBSERVABILITY_METRICS_PROVIDER = CONFIG_TABLE_METRICS + ".provider";
    public static final String CONFIG_OBSERVABILITY_METRICS_REPORTER = CONFIG_TABLE_METRICS + ".reporter";

    // Tracing Configs
    public static final String CONFIG_TRACING_ENABLED = CONFIG_TABLE_TRACING + ".enabled";
    public static final String CONFIG_OBSERVABILITY_TRACING_PROVIDER = CONFIG_TABLE_TRACING + ".provider";

    // Checkpoint Configs
    public static final String CHECKPOINT_EVENT_NAME = "CHECKPOINT";
}
