/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.test.statements.vardeclr;

import org.ballerinalang.core.model.values.BBoolean;
import org.ballerinalang.core.model.values.BByte;
import org.ballerinalang.core.model.values.BFloat;
import org.ballerinalang.core.model.values.BInteger;
import org.ballerinalang.core.model.values.BValue;
import org.ballerinalang.test.util.BCompileUtil;
import org.ballerinalang.test.util.BRunUtil;
import org.ballerinalang.test.util.CompileResult;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import static org.ballerinalang.test.util.BAssertUtil.validateError;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertFalse;

/**
 * Class to test module level variable declaration for all binding patterns.
 *
 * @since 2.0
 */
public class ModuleVariableTest {

    private CompileResult compileResult, compileResultNegative, recordVarCompileResult;

    @BeforeClass
    public void setup() {
//        compileResult = BCompileUtil.compile("test-src/statements/vardeclr/module_tuple_var_decl.bal");
//        compileResultNegative = BCompileUtil.compile("test-src/statements/vardeclr/module_tuple_var_decl_negetive.bal");
        recordVarCompileResult = BCompileUtil.compile("test-src/statements/vardeclr/module_record_var_decl.bal");
    }

    @Test
    public void testBasicModuleLevelTupleVarDecl() {

        BValue[] returns = BRunUtil.invoke(compileResult, "testBasic");

        int index = 0;
        assertTrue(((BBoolean) returns[index++]).booleanValue());
        assertEquals(((BFloat) returns[index++]).floatValue(), 3.25);
        assertEquals(returns[index++].stringValue(), "Dulmina");
        assertEquals(returns.length, index++);
    }
    
    @Test
    public void testComlexModuleLevelTupleVarDecl() {

        BValue[] returns = BRunUtil.invoke(compileResult, "testTupleBindingWithRecordsAndObjects");

        int index = 0;
        Assert.assertEquals(returns[index++].stringValue(), "Test");
        Assert.assertEquals(returns[index++].stringValue(), "Test");
        Assert.assertEquals(((BInteger) returns[index++]).intValue(), 23);
        Assert.assertEquals(((BInteger) returns[index++]).intValue(), 34);
        Assert.assertTrue(((BBoolean) returns[index++]).booleanValue());
        Assert.assertEquals(returns[index++].stringValue(), "Fooo");
        Assert.assertEquals(((BFloat) returns[index++]).floatValue(), 3.7);
        Assert.assertEquals(((BByte) returns[index++]).byteValue(), 23);
        Assert.assertTrue(((BBoolean) returns[index++]).booleanValue());
        Assert.assertEquals(((BInteger) returns[index++]).intValue(), 56);
        Assert.assertEquals(((BInteger) returns[index++]).intValue(), 56);
        assertEquals(returns.length, index++);
    }

    @Test
    public void testModuleLevelTupleVarDeclNegetive() {
        int index = 0;
        validateError(compileResultNegative, index++, "redeclared symbol 'a'", 19, 23);
        validateError(compileResultNegative, index++, "redeclared symbol 'b'", 20, 7);
        validateError(compileResultNegative, index++, "undefined symbol 'd'", 23, 12);
        validateError(compileResultNegative, index++, "undefined symbol 'd'", 24, 9);
        assertEquals(compileResultNegative.getErrorCount(), index++);
    }

    @Test
    public void testUninitializedModuleLevelTupleVar() {
        CompileResult compileResult =
                BCompileUtil.compile("test-src/statements/vardeclr/uninitialized_module_tuple_var_decl.bal");
        int index = 0;
        validateError(compileResult, index++, "uninitialized variable 'a'", 17, 13);
        validateError(compileResult, index++, "uninitialized variable 'b'", 17, 16);
        validateError(compileResult, index++, "variable 'a' is not initialized", 20, 13);
        assertEquals(compileResult.getErrorCount(), index++);
    }

    @Test
    public void testModuleLevelRecordVarDecl() {

        BValue[] returns = BRunUtil.invoke(recordVarCompileResult, "testBasic");
        int index = 0;
        assertEquals(returns[index++].stringValue(), "Jhone");
        assertTrue(((BBoolean) returns[index++]).booleanValue());
        assertEquals(returns.length, index++);

        returns = BRunUtil.invoke(recordVarCompileResult, "recordVarInRecordVar");
        index = 0;
        assertEquals(returns[index++].stringValue(), "Peter");
        assertEquals(((BInteger) returns[index++]).intValue(), 29);
        assertEquals(returns[index++].stringValue(), "Y");
        assertTrue(((BBoolean) returns[index++]).booleanValue());
        assertEquals(returns.length, index++);

        returns = BRunUtil.invoke(recordVarCompileResult, "tupleVarInRecordVar");
        index = 0;
        assertEquals(returns[index++].stringValue(), "Mac");
        assertEquals(((BInteger) returns[index++]).intValue(), 21);
        assertEquals(returns[index++].stringValue(), "Y");
        assertFalse(((BBoolean) returns[index++]).booleanValue());
        assertEquals(returns.length, index++);
    }
}
