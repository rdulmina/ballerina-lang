//  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

//Test module level mapping binding pattern
type Age record {
    int age;
    string format;
};

type Person record {|
    string name;
    boolean married;
|};

Person {name:Fname, married:Married} = {name:"Jhone", married:true};

public function testBasic() {
    assertEquality("Jhone", Fname);
    assertTrue(Married);
}


// Record var inside record var ------------------------------------------------------------
type PersonWithAge record {
    string name;
    Age age;
    boolean married;
};
PersonWithAge {name: fName1, age: {age: theAge1, format:format1}, married} = getPersonWithAge();

function recordVarInRecordVar() {
    assertEquality("Peter", fName1);
    assertEquality(29, theAge1);
    assertEquality("Y", format1);
    assertTrue(married);
}

function getPersonWithAge() returns PersonWithAge {
    return { name: "Peter", age: {age:29, format: "Y"}, married: true, "work": "SE" };
}
// ------------------------------------------------------------------------------------------------

// Tuple var inside record var ------------------------------------------------------------
type PersonWithAge2 record {
    string name;
    [int, string] age;
    boolean married;
};
PersonWithAge2 {name: fName2, age: [age2, format2], married:married2} = getPersonWithAge2();

function tupleVarInRecordVar() {
    assertEquality("Mac", fName2);
    assertEquality(21, age2);
    assertEquality("Y", format2);
    assertFalse(married2);
}

function getPersonWithAge2() returns PersonWithAge2 {
    return { name: "Mac", age:[21, "Y"], married: false};
}
// ------------------------------------------------------------------------------------------------


// Check annotation support ---------------------------------------------------------------------
const annotation annot on source var;

@annot
Age {age, format} = {age:24, format:"myFormat"};

type AssertionError error;

const ASSERTION_ERROR_REASON = "AssertionError";

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

    panic AssertionError(ASSERTION_ERROR_REASON, message = "expected '" + expected.toString() + "', found '" + actual.toString () + "'");
}
