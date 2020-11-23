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

package org.wso2.ballerinalang.compiler.semantics.model.symbols;

import io.ballerina.tools.diagnostics.DiagnosticCode;
import io.ballerina.tools.diagnostics.Location;

import java.util.ArrayList;
import java.util.List;

/**
 * Used in taint analysis to maintains tainted status of return parameters or any taint checking related errors.
 *
 * @since 0.965.0
 */
public class TaintRecord {
    /**
     * Represents taint status or a return or a parameter. When used in maintaining parameter tainted status, "ignored"
     * state is used to denote that the tainted status of the argument should be left unchanged, where as tainted and
     * untainted statues are used to change the argument tainted status accordingly.
     */
    public enum TaintedStatus {
        UNTAINTED((byte) 0), TAINTED((byte) 1), IGNORED((byte) 2);

        // Value used to represent the taint status in the compiled code.
        private final byte byteValue;

        TaintedStatus(byte byteValue) {
            this.byteValue = byteValue;
        }

        public byte getByteValue() {
            return byteValue;
        }
    }

    public TaintedStatus returnTaintedStatus;
    public List<TaintedStatus> parameterTaintedStatusList;
    public List<TaintError> taintError;

    public TaintRecord(List<TaintError> taintError) {
        this.taintError = taintError;
    }

    public TaintRecord(TaintedStatus returnTaintedStatus, List<TaintedStatus> parameterTaintedStatusList) {
        this.returnTaintedStatus = returnTaintedStatus;
        this.parameterTaintedStatusList = parameterTaintedStatusList;
    }

    /**
     * Used to propagate taint checking related error information through taint-table.
     */
    public static class TaintError {
        public Location pos;
        public List<String> paramName;
        public DiagnosticCode diagnosticCode;

        public TaintError(Location location, String paramName, DiagnosticCode diagnosticCode) {
            this.pos = location;
            this.paramName = new ArrayList<>(1);
            this.paramName.add(paramName);
            this.diagnosticCode = diagnosticCode;
        }

        public TaintError(Location location, List<String> paramName, DiagnosticCode diagnosticCode) {
            this.pos = location;
            this.paramName = paramName;
            this.diagnosticCode = diagnosticCode;
        }

        public TaintError(Location pos, String paramName, String paramName2,
                          DiagnosticCode diagnosticCode) {
            this.pos = pos;
            this.paramName = new ArrayList<>(1);
            this.paramName.add(paramName);
            this.paramName.add(paramName2);
            this.diagnosticCode = diagnosticCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            TaintError that = (TaintError) o;

            if (!pos.toString().equals(that.pos.toString())) {
                return false;
            }
            if (!paramName.equals(that.paramName)) {
                return false;
            }
            return diagnosticCode == that.diagnosticCode;
        }

        @Override
        public int hashCode() {
            int result = pos.toString().hashCode();
            result = 31 * result + paramName.hashCode();
            result = 31 * result + diagnosticCode.hashCode();
            return result;
        }
    }

    /**
     * Represent the taintedness annotation in function return type.
     */
    public enum TaintAnnotation {
        NON, TAINTED, UNTAINTED
    }
}
