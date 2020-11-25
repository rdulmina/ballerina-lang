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

public isolated class IsolatedClassWithNoMutableFields {
    public final int[] & readonly a;
    final readonly & record {int i;} b;

    function init(int[] & readonly a, record {int i;} & readonly b) {
        self.a = a;
        self.b = b;
    }
}

isolated class IsolatedClassWithPrivateMutableFields {
    public final int[] & readonly a = [10, 20, 30];
    final readonly & record {int i;} b;

    private int c;
    private map<int>[] d;

    isolated function init(record {int i;} & readonly b, int c) {
        self.b = b;
        self.c = c;
    }
}

final readonly & string[] immutableStringArray = ["hello", "world"];

type IsolatedObjectType isolated object {
    int a;
    string[] b;
};

isolated class IsolatedClassOverridingMutableFieldsInIncludedIsolatedObject {
    *IsolatedObjectType;

    final int a = 100;
    private string[] b;

    function init() {
        self.b = [];
    }

    function accessImmutableField() returns int => self.a + 1;

    isolated function accessMutableField() returns int {
        lock {
            self.b.push(...immutableStringArray);
            return self.b.length();
        }
    }
}

function testIsolatedObjectOverridingMutableFieldsInIncludedIsolatedObject() {
    isolated object {} isolatedObjectOverridingMutableFieldsInIncludedIsolatedObject = isolated object IsolatedObjectType {

        final int a = 100;
        private string[] b = [];

        function accessImmutableField() returns int => self.a + 1;

        isolated function accessMutableField() returns int {
            lock {
                self.b.push(...immutableStringArray);
                return self.b.length();
            }
        }
    };
}

isolated class IsolatedClassWithMethodsAccessingPrivateMutableFieldsWithinLockStatements {
    public final int[] & readonly a = [10, 20, 30];
    final readonly & record {int i;} b;

    private int c;
    private map<int[]> d;

    isolated function init(record {int i;} & readonly b, int c, map<int[]> d) {
        self.b = b;
        self.c = c;
        self.d = d.clone();
    }

    isolated function accessMutableFieldInLockOne() returns int[] {
        int i = 1;

        lock {
            self.c = i;

            int j = i;
            self.c = i;

            map<int[]> k = {
                a: [1, 2, i, j, i + j],
                b: self.a
            };
            self.d = k;
            k = self.d;
        }

        return self.a;
    }

    function accessMutableFieldInLockTwo() returns int[] {
        int i = 1;
        int[] x;

        lock {
            int j = i;

            lock {
                int[] y = [1, 2, 3];
                x = y.clone();
                self.c = i;
            }

            map<int[]> k = {
                a: [1, 2, i, j, i + j],
                b: self.a
            };
            self.d = k;
            k = self.d;
        }

        return self.a;
    }

    isolated function accessImmutableFieldOutsideLock() returns int[] {
        int[] arr = [];

        arr.push(...self.a);
        arr[5] = self.b.i;
        return arr;
    }
}

isolated object {} isolatedObjectWithMethodsAccessingPrivateMutableFieldsWithinLockStatements = object {
    public final int[] & readonly a = [10, 20, 30];
    final readonly & record {int i;} b = {i: 101};

    private int c = 1;
    private map<int[]> d = {};

    function accessMutableFieldInLockOne() returns int[] {
        int i = 1;

        lock {
            self.c = i;

            int j = i;
            self.c = i;

            map<int[]> k = {
                a: [1, 2, i, j, i + j],
                b: self.a
            };
            self.d = k;
            k = self.d;
        }

        return self.a;
    }

    isolated function accessMutableFieldInLockTwo() returns int[] {
        int i = 1;
        int[] x;

        lock {
            int j = i;

            lock {
                int[] y = [1, 2, 3];
                x = y.clone();
                self.c = i;
            }

            map<int[]> k = {
                a: [1, 2, i, j, i + j],
                b: self.a
            };
            self.d = k;
            k = self.d;
        }

        return self.a;
    }

    isolated function accessImmutableFieldOutsideLock() returns int[] {
        int[] arr = [];

        arr.push(...self.a);
        arr[5] = self.b.i;
        return arr;
    }
};

isolated class IsolatedClassWithNonPrivateIsolatedObjectFields {
    final isolated object {} a = isolated object {
        final int i = 1;
        private map<int> j = {};
    };
    final IsolatedClassWithMethodsAccessingPrivateMutableFieldsWithinLockStatements b;
    private final IsolatedClassWithMethodsAccessingPrivateMutableFieldsWithinLockStatements c = new ({i: 1}, 102, {});
    private int[] d = [1, 2];

    function init() {
        self.b = new ({i: 1}, 102, {});
    }

    isolated function accessNonPrivateIsoltedObjectFieldOutsideLock() {
        int[] x = self.b.accessMutableFieldInLockOne();
    }
}

isolated object {} isolatedObjectWithNonPrivateIsolatedObjectFields = object {
    final isolated object { function foo() returns int; } a = isolated object {
        final int i = 1;
        private map<int> j = {};

        isolated function foo() returns int {
            lock {
                return self.i + (self.j["a"] ?: 1);
            }
        }
    };
    final int|IsolatedClassWithMethodsAccessingPrivateMutableFieldsWithinLockStatements b = new ({i: 1}, 102, {});
    private final IsolatedClassWithMethodsAccessingPrivateMutableFieldsWithinLockStatements c = new ({i: 1}, 102, {});
    private int[] d = [1, 2];

    function accessNonPrivateIsoltedObjectFieldOutsideLock() {
        int x = self.a.foo();
    }
};

const INT = 100;

isolated class IsolatedClassWithUniqueInitializerExprs {
    private map<int> a = <map<int>> {};
    final int[] & readonly b = [1, INT];
    private table<record {readonly int i; string name;}> c;

    isolated function init(table<record {readonly int i; string name;}> tb) {
        self.c = tb.clone();
    }
}

isolated object {} isolatedObjectWithUniqueInitializerExprs = object {
    private map<int> a = <map<int>> {};
    final int[] & readonly b = [1, INT];
    private table<record {readonly int id; string name;}> c;

    isolated function init() {
        self.c = table [
            {id: INT, name: "foo"},
            {id: INT + 100, name: "bar"}
        ];
    }
};

isolated class IsolatedClassWithValidCopyInInsideBlock {
    private string[] uniqueGreetings = [];

    isolated function add(string[] greetings) {
        lock {
            if self.uniqueGreetings.length() == 0 {
                self.uniqueGreetings = greetings.clone();
            }
        }
    }
}

isolated class IsolatedClassWithValidCopyyingWithClone {
    private anydata[] arr = [];
    private anydata[] arr2 = [];

    isolated function add(anydata val) {
        lock {
            self.arr.push(val.clone());

            self.addAgain(val.clone());
            anydata clonedVal = val.cloneReadOnly();
            self.addAgain(clonedVal.clone());
        }
    }

    isolated function addAgain(anydata val) {
        lock {
            self.arr2.push(val.clone());
            self.arr2.push(val.cloneReadOnly());
        }
    }
}

isolated class IsolatedClassPassingCopiedInVarsToIsolatedFunctions {

    private anydata[] arr = [];

    isolated function add(anydata val) {
        lock {
            anydata val2 = val.clone();
            self.arr.push(val2);
            self.addAgain(val2);
            outerAdd(val.clone());

            if val is anydata[] {
                self.arr.push(val[0].clone());
                self.addAgain(val[0].clone());
                anydata val3 = val[0].cloneReadOnly();
                outerAdd(val3);
            }

            lock {
                if val2 is anydata[] {
                    self.arr.push(val2[0].clone());
                    self.addAgain(val2[0].clone());
                    anydata val3 = val2[0].cloneReadOnly();
                    outerAdd(val3);
                }
            }
        }
    }

    isolated function addAgain(anydata val) {

    }
}

isolated function outerAdd(anydata val) {

}

isolated class ArrayGen {
    isolated function getArray() returns int[] => [];
}

ArrayGen|(int[] & readonly) unionVal = [1, 2, 3];

isolated class IsolatedClassAccessingSubTypeOfReadOnlyOrIsolatedObjectUnion {
    private int[] a;

    function testAccessingSubTypeOfReadOnlyOrIsolatedObjectUnionInIsolatedClass() {
        lock {
            var val = unionVal;
            self.a = val is ArrayGen ? val.getArray() : val;
        }
    }
}

int[] a = [];
readonly & int[] b = [];
final readonly & int[] c = [];

isolated class IsolatedClassWithValidVarRefs {
    private any[] w = [];
    private int[][] x = [];
    private int[] y;
    private int[] z = b;

    function init() {
        self.y = b;
    }

    function updateFields() {
        lock {
            self.x = let int[] u = b, int[] v = a.clone() in [b, u, v, c];
            self.y = (let int[]? u = b in ((u is int[]) ? u : <int[]> []));
            self.z = let int[] u = a.clone() in u;
        }
    }

    isolated function isolateUpdateFields() {
        lock {
            self.x = let int[] u = c, int[] v = c in [c, u, v];
            self.y = (let int[]? u = c in ((u is int[]) ? u : <int[]> []));
            self.z = let int[] u = c.clone() in u;
        }
    }

    isolated function nested() {
        any[] arr = let int[] u = c in [u];

        lock {
            self.w = let int[] u = c in [u, let int[] v = c.clone() in isolated function () returns int[2][] { return [c, []]; }];
        }
    }
}

function testObjectConstrExprImplicitIsolatedness() {
    var ob = object {
        final int[] & readonly x = [];
        final IsolatedObjectType y = object {
            final int a = 1;
            final readonly & string[] b = [];
        };
        final (readonly & string[])|IsolatedClassWithPrivateMutableFields z = new IsolatedClassWithPrivateMutableFields({i: 2}, 3);
    };

    isolated object {} isolatedOb = ob;
    assertTrue(<any> isolatedOb is isolated object {});

    var ob2 = object {
        final int[] x = [];
        final object {} y = object {
            final int a = 1;
            final string[] b = [];
        };
        final IsolatedClassWithPrivateMutableFields z = new ({i: 2}, 3);
    };

    assertFalse(<any> ob2 is isolated object {});

    var ob3 = object {
        final int[] & readonly x = [];
        final IsolatedObjectType y = object {
            final int a = 1;
            final readonly & string[] b = [];
        };
        final string[]|IsolatedClassWithPrivateMutableFields z = new IsolatedClassWithPrivateMutableFields({i: 2}, 3);
    };

    assertFalse(<any> ob3 is isolated object {});
}

class NonIsolatedClass {
    int i = 1;
}

function testRuntimeIsolatedFlag() {
    IsolatedClassWithPrivateMutableFields x = new ({i: 2}, 3);
    assertTrue(<any> x is isolated object {});

    NonIsolatedClass y = new;
    assertFalse(<any> y is isolated object {});

    testObjectConstrExprImplicitIsolatedness();

    object {} ob1 = isolated object {
        private int i = 1;

        function updateI() {
            lock {
                self.i = 2;
            }
        }
    };
    assertTrue(<any> ob1 is isolated object {});

    object {} ob2 = object {
        private int i = 1;

        function updateI() {
            self.i = 2;
        }
    };
    assertFalse(<any> ob2 is isolated object {});
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

    panic error("expected '" + expected.toString() + "', found '" + actual.toString () + "'");
}
