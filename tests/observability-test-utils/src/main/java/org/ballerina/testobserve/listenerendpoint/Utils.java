/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package org.ballerina.testobserve.listenerendpoint;

import io.ballerina.runtime.api.creators.ErrorCreator;
import io.ballerina.runtime.api.utils.StringUtils;
import io.ballerina.runtime.api.values.BError;

import java.io.PrintStream;

import static org.ballerina.testobserve.listenerendpoint.Constants.MOCK_LISTENER_ERROR_TYPE;
import static org.ballerina.testobserve.listenerendpoint.Constants.TEST_OBSERVE_PACKAGE;

/**
 * Mock listener endpoint related utilities.
 */
public class Utils {
    private static final PrintStream out = System.out;

    /**
     * Create a Ballerina error using a throwable.
     *
     * @param t Throwable from which the error should be created
     * @return The corresponding ballerina error object
     */
    public static BError createError(Throwable t) {
        return ErrorCreator.createDistinctError(MOCK_LISTENER_ERROR_TYPE, TEST_OBSERVE_PACKAGE,
                                                StringUtils.fromString(t.getMessage()));
    }

    public static void logInfo(String format, Object ... args) {
        out.printf("[Mock Listener] - INFO - " + format + "\n", args);
    }

    public static void logError(String format, Object ... args) {
        out.printf("[Mock Listener] - ERROR - " + format + "\n", args);
    }

    public static String normalizeResourcePath(String resourcePath) {
        if (!resourcePath.startsWith("/")) {
            resourcePath = "/"  + resourcePath;
        }
        return resourcePath;
    }
}
