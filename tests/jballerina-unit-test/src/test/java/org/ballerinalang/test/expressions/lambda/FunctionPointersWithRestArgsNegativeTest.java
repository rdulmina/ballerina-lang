/*
*   Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package org.ballerinalang.test.expressions.lambda;

import org.ballerinalang.test.BAssertUtil;
import org.ballerinalang.test.BCompileUtil;
import org.ballerinalang.test.CompileResult;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

/**
 * Negative test cases for function pointers with rest params.
 *
 * @since 1.1.0
 */
public class FunctionPointersWithRestArgsNegativeTest {

    private CompileResult fpProgram;

    @BeforeClass
    public void setup() {
        fpProgram = BCompileUtil.compile("test-src/expressions/lambda/function-pointers-with-rest-args-negative.bal");
    }

    @Test
    public void testNegative() {
        Assert.assertEquals(fpProgram.getErrorCount(), 1);
        int index = 0;
        BAssertUtil.validateError(fpProgram, index, "incompatible types: expected 'function (string,int[]) returns " +
                "(string)', found 'function (string, int...) returns (string)'", 2, 54);
    }

    @AfterClass
    public void tearDown() {
        fpProgram = null;
    }
}
