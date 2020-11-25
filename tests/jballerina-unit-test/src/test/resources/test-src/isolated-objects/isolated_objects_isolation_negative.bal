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

isolated class InvalidIsolatedClassWithNonPrivateMutableFields {
    int a;
    public map<int> b;
    private final string c = "invalid";

    function init(int a, map<int> b) {
        self.a = a;
        self.b = b.clone();
    }
}

type IsolatedObject isolated object {};

IsolatedObject invalidIsolatedObjectConstructorWithNonPrivateMutableFields = isolated object {
    int a;
    public map<int> b;
    private final string c = "invalid";

    function init() {
        self.a = 1;
        self.b = {};
    }
};

type IsolatedObjectType isolated object {
    int a;
    string[] b;
};

isolated class InvalidIsolatedClassNotOverridingMutableFieldsInIncludedIsolatedObject {
    *IsolatedObjectType;

    function init() {
        self.a = 1;
        self.b = [];
    }
}

IsolatedObject invalidIsolatedObjectNotOverridingMutableFieldsInIncludedIsolatedObject = isolated object IsolatedObjectType {
   function init() {
       self.a = 1;
       self.b = [];
   }
};

isolated class InvalidIsolatedClassAccessingMutableFieldsOutsideLock {
    final int a = 1;
    private string b = "hello";
    private int[] c;

    function init(int[] c) {
        self.c = c.clone();
    }

    function getB() returns string => self.b;

    function setB(string s) {
        self.b = s;
    }

    function updateAndGetC(int i) returns int[] {
        lock {
            self.c.push(i); // OK
        }
        return self.c;
    }
}

function testInvalidIsolatedObjectConstructorAccessingMutableFieldsOutsideLock() {
    isolated object {} invalidIsolatedObjectConstructorAccessingMutableFieldsOutsideLock = isolated object {
        final int a = 1;
        private string b = "hello";
        private int[] c = [];

        function getB() returns string => self.b;

        function setB(string s) {
            self.b = s;
        }

        function updateAndGetC(int i) returns int[] {
            lock {
                self.c.push(i); // OK
            }
            return self.c;
        }
    };
}

int[] globIntArr = [1200, 12, 12345];
int[] globIntArrCopy = globIntArr;
int[] globIntArrCopy2 = globIntArrCopy;
string globStr = "global string";
map<boolean> globBoolMap = {a: true, b: false};

function accessGlobBoolMap(string s) {
    _ = (globBoolMap["a"] ?: true) && (globStr == s);
}

isolated class InvalidIsolatedClassWithNonUniqueInitializerExprs {
    private int[][] a;
    private map<boolean> b = globBoolMap;
    private record {} c = {[globStr]: accessGlobBoolMap(globStr)};
    final string d = globStr;
    private table<record{ readonly int id; }> e = table key (id) [
        {id: 1, "name": "foo"},
        {id: 2, "name": string `str ${globStr}`}
    ];
    final readonly & xml[] f = [xml `hello ${globStr}`, xml `<!-- int: ${globIntArr[0]} -->`, xml `ok`,
                                xml `?pi ${globBoolMap["a"] is boolean ? "true" : globStr}?`];
    final int g = checkpanic trap <int> 'int:fromString(globStr);
    private float h;

    function init(int[][]? a) returns error? {
        self.a = a ?: [globIntArr, globIntArr];
        record {} rec = {"a": 1, "b": 2.0};
        anydata ad = rec;
        self.c = rec;
        self.h = check 'float:fromString(globStr);
    }
}

function testInvalidIsolatedObjectWithNonUniqueInitializerExprs() {
    isolated object {} invalidIsolatedObjectWithNonUniqueInitializerExprs = isolated object {
        private int[][] a = [globIntArr, globIntArr];
        private map<boolean> b = globBoolMap;
        private record {} c = {[globStr]: accessGlobBoolMap(globStr)};
        final string d = globStr;
        private table<record{ readonly int id; }> e = table key (id) [
            {id: 1, "name": "foo"},
            {id: 2, "name": string `str ${globStr}`}
        ];
        final readonly & xml[] f = [xml `hello ${globStr}`, xml `<!-- int: ${globIntArr[0]} -->`, xml `ok`,
                                    xml `?pi ${globBoolMap["a"] is boolean ? "true" : globStr}?`];
        final int g = checkpanic trap <int> 'int:fromString(globStr);
        private float h;

        function init() {
            record {} rec = {"a": 1, "b": 2.0};
            anydata ad = rec;
            self.c = rec;
            self.h = checkpanic 'float:fromString(globStr);
        }
    };
}

isolated class InvalidIsolatedClassWithInvalidCopyIn {
    public final record {} & readonly a;
    private int b;
    private map<boolean>[] c;

    function init(record {} & readonly a, int b, map<boolean>[] c) {
        self.a = a;
        self.b = b;
        self.c = c.clone();
    }

    function invalidCopyInOne(map<boolean> boolMap) {
        map<boolean> bm1 = {};
        lock {
            map<boolean> bm2 = {a: true, b: false};

            self.c[0] = globBoolMap;
            self.c.push(boolMap);
            self.c = [bm1, bm2];
        }
    }

    isolated function invalidCopyInTwo(map<boolean> boolMap) {
        map<boolean> bm1 = {};
        lock {
            map<boolean> bm2 = {};
            lock {
                map<boolean> bm3 = boolMap;
                bm2 = bm3;
            }

            self.c.push(boolMap);
            self.c[0] = boolMap;
            self.c = [bm1, bm2];
        }
    }
}

IsolatedObject invalidIsolatedObjectWithInvalidCopyIn = isolated object {
    public final record {} & readonly a = {"type": "final"};
    private int b = 0;
    private map<boolean>[] c = [];

    isolated function invalidCopyInOne(map<boolean> boolMap) {
        map<boolean> bm1 = {};
        lock {
            map<boolean> bm2 = {a: true, b: false};

            self.c[0] = boolMap;
            self.c.push(boolMap);
            self.c = [bm1, bm2];
        }
    }

    function invalidCopyInTwo(map<boolean> boolMap) {
        map<boolean> bm1 = {};
        lock {
            map<boolean> bm2 = {};
            lock {
                map<boolean> bm3 = boolMap;
                bm2 = bm3;
            }

            self.c.push(boolMap);
            self.c[0] = globBoolMap;
            self.c = [bm1, bm2];
        }
    }
};

isolated class InvalidIsolatedClassWithInvalidCopyOut {
    public final record {} & readonly a = {"type": "final"};
    private int b = 1;
    private map<boolean>[] c;

    function init() {
        self.c = [];
    }

    function invalidCopyOutOne(map<boolean>[] boolMaps) returns map<boolean>[] {
        map<boolean>[] bm1 = boolMaps;
        lock {
            map<boolean>[] bm2 = [{a: true, b: false}];

            bm1 = self.c;
            globBoolMap = bm2[0];
            bm1 = [self.c[0]];
            return self.c;
        }
    }

    isolated function invalidCopyOutTwo(map<boolean>[] boolMaps) {
        map<boolean> bm1 = {};
        lock {
            map<boolean> bm2 = {};
            lock {
                map<boolean> bm3 = boolMaps[0].clone();
                bm1 = bm3;
            }

            bm1 = self.c[0];
            bm1 = bm2;
        }
    }
}

function testInvalidIsolatedObjectWithInvalidCopyOut() {
    isolated object {} invalidIsolatedObjectWithInvalidCopyOut = isolated object {
        private map<boolean>[] c = [];

        isolated function invalidCopyOutOne(map<boolean>[] boolMaps) returns map<boolean> {
            map<boolean>[] bm1 = boolMaps;
            lock {
                map<boolean>[] bm2 = [{a: true, b: false}];

                bm1 = self.c;
                bm1 = bm2;
                bm1 = [self.c[0]];
                return self.c.pop();
            }
        }

        function invalidCopyOutTwo(map<boolean>[] boolMaps) {
            map<boolean> bm1 = {};
            lock {
                map<boolean> bm2 = {};
                lock {
                    map<boolean> bm3 = boolMaps[0].clone();
                    bm1 = bm3;
                }

                globBoolMap = self.c[0];
                bm1 = bm2;
            }
        }
    };
}

isolated class InvalidIsolatedClassWithNonIsolatedFunctionInvocation {
    private int[] x = [];

    function testInvalidNonIsolatedInvocation() {
        lock {
            int[] a = self.x;

            IsolatedClass ic = new;
            a[0] = ic.nonIsolatedFunc();
            a.push(ic.nonIsolatedFunc());

            a = nonIsolatedFunc();
        }
    }
}

IsolatedObject invalidIsolatedObjectWithNonIsolatedFunctionInvocation = isolated object {
    private int[] x = [];

    function testInvalidNonIsolatedInvocation() {
        lock {
            int[] a = self.x;

            IsolatedClass ic = new;
            a[0] = ic.nonIsolatedFunc();
            a.push(ic.nonIsolatedFunc());

            a = nonIsolatedFunc();
        }
    }
};

isolated class InvalidIsolatedClassWithNonInvalidObjectFields {
    IsolatedClass a = new; // Should be `final`
    isolated object {} b = isolated object { // Should be `final`
        final int i = 1;
        private map<int> j = {};
    };
    final object {} c = object {}; // should be an `isolated object`
}

isolated class InvalidIsolatedClassReferringSelfOutsideLock {
    final int a = 1;
    private int[] b = [];

    function foo() {
        bar(self);
        self.baz();
    }

    function baz() {

    }
}

function bar(InvalidIsolatedClassReferringSelfOutsideLock x) {

}

isolated class IsolatedClass {
    function nonIsolatedFunc() returns int => 1;
}

function nonIsolatedFunc() returns int[] {
    return [1, 2, 3];
}

isolated class InvalidIsolatedClassWithCopyInInsideBlock {
    private string[] uniqueGreetings = [];

    isolated function add(string[] greetings) {
        lock {
            if self.uniqueGreetings.length() == 0 {
                self.uniqueGreetings = greetings;
            }
        }
    }
}

isolated class InvalidIsolatedClassWithInvalidCopyingWithClone {
    private anydata[] arr = [];
    private anydata[] arr2 = [];

    isolated function add(anydata val) {
        lock {
            self.arr.push(val);

            self.addAgain(val);
            outerAdd(val);
            anydata clonedVal = val;
            self.addAgain(clonedVal); // OK
            if val is anydata[] {
                self.arr.push(val.pop());

                lock {
                    self.arr.push(val.pop());
                }
            }

            if clonedVal is anydata[] {
                self.arr.push(clonedVal.pop()); // OK

                lock {
                    self.arr.push(clonedVal.pop());
                }
            }
        }
    }

    isolated function addAgain(anydata val) { }
}

isolated function outerAdd(anydata val) { }

decimal globDecimal = 0;

isolated class CurrentConfig {
    private decimal[2] x = [1, 2.0];
    private record {
        int[] a;
        decimal b;
        boolean c;
    } y = {a: [1, 2], b: 1.0, c: true};

    function foo(decimal arr) {
        decimal a = 0;
        decimal a2 = 0;

        lock {
            [a, globDecimal] = self.x.clone();
            [a2, globDecimal] = self.x;
        }
    }

    function bar() {
        int[] a;
        decimal b;

        lock {
            boolean c;
            {a, b, c} = self.y;
            {a, b, c} = self.y.cloneReadOnly();
        }
    }
}

int[] y = [];
readonly & int[] z = [];

isolated class IsolatedClassWithInvalidVarRefs {
    private int[][] x;
    private int[] y;
    private any[] z;

    function init() {
        self.y = y;
    }

    function push() {
        lock {
            self.x = let int[] z = y in [y, z];
            self.y = (let int[]? z = y in ((z is int[]) ? z : <int[]> []));
            self.x = let int[] v = z, int[] w = y in <int[][]> [z, v, w];
        }
    }

    isolated function nested() {
        any[] arr = let int[] v = y in [v];

        lock {
            self.z = let int[] v = y in [v, let int[] w = y in isolated function () returns int[2][] { return [w, v]; }];
        }
    }
}
