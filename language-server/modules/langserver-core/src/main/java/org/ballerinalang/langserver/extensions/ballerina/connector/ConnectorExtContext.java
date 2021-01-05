/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.ballerinalang.langserver.extensions.ballerina.connector;

import java.util.HashMap;
import java.util.Map;

/**
 * Ballerina Language server context.
 *
 * @since 0.970.0
 */
public class ConnectorExtContext {

    private final Map<ConnectorExtContext.Key<?>, Object> props = new HashMap<>();

    public <V> void put(ConnectorExtContext.Key<V> key, V value) {
        props.put(key, value);
    }

    @SuppressWarnings("unchecked")
    public <V> V get(ConnectorExtContext.Key<V> key) {
        return (V) this.props.get(key);
    }

    /**
     * @param <K> Property Key
     * @since 2.0.0
     */
    public static class Key<K> {
    }
}
