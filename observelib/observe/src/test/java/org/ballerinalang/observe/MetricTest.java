/*
 * Copyright (c) 2018 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.observe;

import io.ballerina.runtime.observability.metrics.DefaultMetricRegistry;
import io.ballerina.runtime.observability.metrics.MetricRegistry;
import org.ballerinalang.observe.metrics.extension.defaultimpl.DefaultMetricProvider;
import org.testng.annotations.BeforeSuite;

/**
 * This is the bases test class which enables the metrics reporting.
 *
 * @since 0.980.0
 */
public class MetricTest {

    @BeforeSuite
    public void init() {
        DefaultMetricProvider metricProvider = new DefaultMetricProvider();
        metricProvider.init();
        DefaultMetricRegistry.setInstance(new MetricRegistry(metricProvider));
    }
}
