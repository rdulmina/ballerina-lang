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

import ballerina/jballerina.java;

public class Listener {

    public isolated function init(int port) {
        externInitEndpoint(self, port);
    }

    public isolated function attach(service object {} s, string[]|string? name = ()) returns error? {
        string basePath;
        if (name is string[]) {
            basePath = "/".'join(...name);
        } else if (name is string) {
            basePath = name;
        } else {
            basePath = "";
        }
        return externAttach(self, s, basePath);
    }

    public isolated function detach(service object {} s) returns error? {
        return externDetach(self, s);
    }

    public isolated function 'start() returns error? {
        externStart(self);
    }

    public isolated function gracefulStop() returns error? {
        return externShutdownGracefully(self);
    }

    public isolated function immediateStop() returns error? {
        return externShutdownNow(self);
    }
}

isolated function externInitEndpoint(Listener listenerObj, int port) = @java:Method {
    'class: "org.ballerina.testobserve.listenerendpoint.Endpoint",
    name: "initEndpoint"
} external;

isolated function externAttach(Listener listenerObj, service object {} s, string basePath) returns error? = @java:Method {
    'class: "org.ballerina.testobserve.listenerendpoint.Endpoint",
    name: "attachService"
} external;

isolated function externDetach(Listener listenerObj, service object {} s) returns error? = @java:Method {
    'class: "org.ballerina.testobserve.listenerendpoint.Endpoint",
    name: "detachService"
} external;

isolated function externStart(Listener listenerObj) = @java:Method {
    'class: "org.ballerina.testobserve.listenerendpoint.Endpoint",
    name: "start"
} external;

isolated function externShutdownGracefully(Listener listenerObj) returns error? = @java:Method {
    'class: "org.ballerina.testobserve.listenerendpoint.Endpoint",
    name: "shutdownGracefully"
} external;

isolated function externShutdownNow(Listener listenerObj) returns error? = @java:Method {
    'class: "org.ballerina.testobserve.listenerendpoint.Endpoint",
    name: "shutdownNow"
} external;
