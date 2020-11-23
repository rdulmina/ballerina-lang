import ballerina/lang.'int;
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

type '\ \/\:\@\[\`\{\~\u{2324}_123_ƮέŞŢ_Student record {
    string '1st_name;
    int 'Ȧɢέ_\ \/\:\@\[\`\{\~\u{2324};
    Grades grades;
};

type Address record {|
    string city;
    string country;
|};

type Grades record {|
    int maths;
    int physics;
    int chemistry;
    int...;
|};

type Employee record {
    readonly int id;
    string name;
    float salary;
};

type EmployeeTable table<Employee> key(id);

class 'Person_\\\ \/\<\>\:\@\[\`\{\~\u{2324}_ƮέŞŢ {
    public string '1st_name = "John";
    public int 'Ȧɢέ_\ \/\:\@\[\`\{\~\u{2324} = 0;
    public 'Person_\\\ \/\<\>\:\@\[\`\{\~\u{2324}_ƮέŞŢ? parent = ();
    string email = "default@abc.com";
    string address = "No 20, Palm grove";

    public function getSum(int a, int b) returns int {
        return a + b;
    }
}

public class Location {
   public string city;
   public string country;

   public function init(string city, string country) {
       self.city = city;
       self.country = country;
   }

   public function value() returns string {
       return self.city + ", " + self.country;
   }
}

public type AnonPerson object {
    public string '1st_name;
    public int 'Ȧɢέ_\ \/\:\@\[\`\{\~\u{2324};
    public 'Person_\\\ \/\<\>\:\@\[\`\{\~\u{2324}_ƮέŞŢ? parent;
    string email;
    string address;
};

type EventType "CURRENT"|"EXPIRED"|"ALL"|"RESET"|"TIMER";

class OddNumberGenerator {
    int i = 1;

    public isolated function next() returns record {|int value;|}|error? {
        self.i += 2;
        return {value: self.i};
    }
}

// constants
const nameWithoutType = "Ballerina";
const string nameWithType = "Ballerina";
const map<string> nameMap = {"name":"John"};
const nilWithoutType = ();
const () nilWithType = ();

// global variables
var stringValue = "Ballerina";
var decimalValue = 100.0d;
var byteValue = <byte>2;
var floatValue = 2.0;
json jsonVar = {name:"John", age:20};
var '\ \/\:\@\[\`\{\~\u{2324}_IL = "IL with global var";

public function main() {
    //------------------------ basic, simple type variables ------------------------//

    var varVariable = ();
    boolean booleanVar = true;
    int intVar = 20;
    float floatVar = -10.0;
    decimal decimalVar = 3.5;

    'int:Unsigned8 unsigned8IntVar = 1;
    'int:Unsigned16 unsigned16IntVar = 100;
    'int:Unsigned32 unsigned32IntVar = 1000;
    'int:Signed8 signed8IntVar = -1;
    'int:Signed16 signed16IntVar = -100;
    'int:Signed32 signed32IntVar = -1000;

    //------------------------ basic, sequence type variables ------------------------//

    string stringVar = "foo";
    xml xmlVar = xml `<person gender="male"><firstname>Praveen</firstname><lastname>Nada</lastname></person>`;

    //------------------------ basic, structured type variables ------------------------//

    any[] arrayVar = [1, 20, -10.0, "foo"];
    [int, string] tupleVar = [20, "foo"];
    map<string> mapVar = {line1: "No. 20", line2: "Palm Grove", city: "Colombo 03", country: "Sri Lanka"};

    '\ \/\:\@\[\`\{\~\u{2324}_123_ƮέŞŢ_Student recordVar = {
        '1st_name: "John Doe",
        'Ȧɢέ_\ \/\:\@\[\`\{\~⌤: 20,
        grades: {
            maths: 80,
            physics: 75,
            chemistry: 65
        }
    };

    record {|string city; string country;|} anonRecord = {city: "London", country: "UK"};

    EmployeeTable tableVar = table [
      {id: 1, name: "John", salary: 300.50},
      {id: 2, name: "Bella", salary: 500.50},
      {id: 3, name: "Peter", salary: 750.0}
    ];

    error errorVar = error("SimpleErrorType", message = "Simple error occurred");

    //------------------------ basic, behavioral type variables ------------------------//

    function (string, string) returns string anonFunctionVar =
        function (string x, string y) returns string {
        return x + y;
    };

    future<int> futureVar = start sum(40, 50);
    _ = wait futureVar;

    'Person_\\\ \/\<\>\:\@\[\`\{\~\u{2324}_ƮέŞŢ objectVar = new;

    AnonPerson anonObjectVar = new 'Person_\\\ \/\<\>\:\@\[\`\{\~\u{2324}_ƮέŞŢ();

    typedesc<int> typedescVar = int;
    stream<int, error> oddNumberStream = new stream<int, error>(new OddNumberGenerator());

    //------------------------ Other types ------------------------//

    string|error unionVar = "foo";
    string? optionalVar = "foo";
    any anyVar = 15.0;
    anydata anydataVar = 619;
    byte byteVar = 128;
    json jsonVar = {name: "apple", color: "red", price: 40};
    xml<never> neverVar = <xml<never>> 'xml:concat();

    // quoted identifiers
    string '\ \/\:\@\[\`\{\~\u{2324}_var = "IL with special characters in var";
    string 'üňĩćőđę_var = "IL with unicode characters in var";
    json 'ĠĿŐΒȂɭ_\ \/\:\@\[\`\{\~\u{2324}_json = {};
}
