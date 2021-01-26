//  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
//  WSO2 Inc. licenses this file to you under the Apache License,
//  Version 2.0 (the "License"); you may not use this file except
//  in compliance with the License.
//  You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

type userDefinedError error <basicErrorDetail>;
type basicErrorDetail record {|
    int basicErrorNo?;
    anydata...;
|};

userDefinedError error (message1, basicErrorNo = detail1) = error userDefinedError("error message one", basicErrorNo = 1);

function testBasic() {
    assertEquality("error message one", message1);
    assertEquality(1, detail1);
}

error cause = error ("error message one", basicErrorNo = 1);
userDefinedError error (message2, errorCause2, basicErrorNo = detail2) = error userDefinedError("error message two",
                                                                                            cause, basicErrorNo = 2);
function testWithErrorCause() {
    assertEquality("error message one", error:message(<error> cause));
    assertEquality("error message two", message2);
    assertEquality(2, detail2);
}

type userDefinedErrorWithTuple error <errorWithTupleDetail>;
type errorWithTupleDetail record {
    [int, string] basicErrorNo?;
};

userDefinedErrorWithTuple error (message3, basicErrorNo = [detail3, otherDetails]) =
                            error userDefinedErrorWithTuple("error message three", basicErrorNo = [3, "myErrorList"]);

function testTupleVarInsideErrorVar() {
    assertEquality("error message three", message3);
    assertEquality(3, detail3);
    assertEquality("myErrorList", otherDetails);
}

type myRecord record {
    int firstValue;
    string secondValue;
};

type userDefinedError2 error<userDefinedErrorDetail2>;
type userDefinedErrorDetail2 record {
    myRecord recordVar?;
    userDefinedError errorVar?;
    int errorNo?;
};

userDefinedError2 error (message4, recordVar = {firstValue, secondValue}) = error userDefinedError2(
                                        "error message four", recordVar = {firstValue:5, secondValue:"Second value"});

function testRecordVarInsideErrorVar() {
    assertEquality("error message four", message4);
    assertEquality(5, firstValue);
    assertEquality("Second value", secondValue);
}

userDefinedError2 error (message5, errorVar = error (message6, basicErrorNo = detail6)) =
                            error userDefinedError2("error message five", errorVar = error userDefinedError("error message six",
                            basicErrorNo = 7));

function testErrorVarInsideErrorVar() {
    assertEquality("error message five", message5);
    assertEquality("error message six", message6);
    assertEquality(7, detail6);
}

const annotation annot on source var;
@annot
userDefinedError2 error(message7) = error userDefinedError2("error message seven");

function testErrorVarWithAnnotations() {
    assertEquality("error message seven", message7);
}

userDefinedError2 error(message9, errorNo = errorNo2) = error userDefinedError2(message8, errorNo = <int> errorNo1);
userDefinedError2 error(message8, errorNo = errorNo1) = error userDefinedError2("error message nine", errorNo = 1);

function testVariableForwardReferencing() {
    assertEquality("error message nine", message9);
    assertEquality(1, errorNo2);
}

userDefinedError error(message10, ...otherDetails2) = error userDefinedError("error message ten", time = 2.21, riskLevel = "High");

function testErrorVarWithRestVariable() {
    assertEquality(2.21, otherDetails2["time"]);
    assertEquality("High", otherDetails2["riskLevel"]);
}

var error(message11, basicErrorNo = errorNo3, ...otherDetails3) = error userDefinedError(
                        "error message eleven", basicErrorNo = 8, lineNo = 342, fileName = "myfile", recoverable = true);

function testErrorVarDeclaredWithVar() {
    assertEquality("error message eleven", message11);
    assertEquality(8, errorNo3);
    assertEquality(342, otherDetails3["lineNo"]);
    assertEquality("myfile", otherDetails3["fileName"]);
    assertTrue(otherDetails3["recoverable"]);
}

function assertTrue(any|error actual) {
    assertEquality(true, actual);
}

function assertFalse(any|error actual) {
    assertEquality(false, actual);
}

function assertEquality(any|error expected, any|error actual) {
    if expected is anydata && actual is anydata && expected == actual {
        return;
    }

    if expected === actual {
        return;
    }

    string expectedValAsString = expected is error ? expected.toString() : expected.toString();
    string actualValAsString = actual is error ? actual.toString() : actual.toString();
    panic error(string `expected '${expectedValAsString}', found '${actualValAsString}'`);
}
