import ballerina/module1;

int moduleVar = 3;

enum TestEnum {
    E1,
    E2
}

function matchTest(any v) returns string {
    int localVar = 4;

    match v
     {
        E1 | module1:TEST_INT_CONST1 
    
        _ => {

        }
    }

    return "";
}