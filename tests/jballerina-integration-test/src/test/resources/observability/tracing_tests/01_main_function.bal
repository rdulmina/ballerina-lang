// Copyright (c) 2020 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

import ballerina/testobserve;

public function main() returns error? {
    testClient->callAnotherRemoteFunction();

    var a = 63;
    var b = 81;
    var sum = testClient->calculateSum(a, b);
    var expectedSum = a + b;
    if (sum != expectedSum) {   // Check for validating if normal execution is intact from instrumentation
        error err = error("failed to find the sum of " + a.toString() + " and " + b.toString()
            + ". expected: " + expectedSum.toString() + " received: " + sum.toString());
        panic err;
    }

    var ret1 = trap testClient->callWithPanic();
    if (!(ret1 is error)) {
        error e = error("Expected error not found");
        panic e;
    }

    var ret2 = testClient->callWithErrorReturn();
    if (!(ret2 is error)) {
        error e = error("Expected error not found");
        panic e;
    }

    service object {} testServiceInMain = service object {
        resource function get resourceOne(testobserve:Caller caller, string body) {
            int numberCount = checkpanic 'int:fromString(body);
            var sum = 0;
            foreach var i in 1 ... numberCount {
                sum = sum + i;
            }
            checkpanic caller->respond("Sum of numbers: " + sum.toString());
        }
    };
    var testObserveListener = new testobserve:Listener(9091);
    check testObserveListener.attach(testServiceInMain);
    check testObserveListener.start();
}
