/*
 * Copyright (c) 2020 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.ballerinalang.debugger.test.adapter.test;

import org.apache.commons.lang3.tuple.Pair;
import org.ballerinalang.debugger.test.DebugAdapterBaseTestCase;
import org.ballerinalang.debugger.test.utils.BallerinaTestDebugPoint;
import org.ballerinalang.debugger.test.utils.DebugUtils;
import org.ballerinalang.test.context.BallerinaTestException;
import org.eclipse.lsp4j.debug.StoppedEventArguments;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Paths;

/**
 * Test class for tests file related debug scenarios for test command.
 */
@Test
public class MultiModuleTestDebugTest extends DebugAdapterBaseTestCase {

    @BeforeClass
    public void setup() {
        testProjectName = "breakpoint-tests";
        testModuleFileName = "tests" + File.separator + "main_test.bal";
        testProjectPath = testProjectBaseDir.toString() + File.separator + testProjectName;
        testEntryFilePath = Paths.get(testProjectPath, testModuleFileName).toString();
    }

    @Test
    public void testMultiModuleDebugScenarios() throws BallerinaTestException {
        addBreakPoint(new BallerinaTestDebugPoint(testEntryFilePath, 22));
        addBreakPoint(new BallerinaTestDebugPoint(testEntryFilePath, 27));
        addBreakPoint(new BallerinaTestDebugPoint(testEntryFilePath, 36));
        addBreakPoint(new BallerinaTestDebugPoint(testEntryFilePath, 44));
        addBreakPoint(new BallerinaTestDebugPoint(testEntryFilePath, 51));
        addBreakPoint(new BallerinaTestDebugPoint(testEntryFilePath, 57));
        addBreakPoint(new BallerinaTestDebugPoint(testEntryFilePath, 64));
        initDebugSession(DebugUtils.DebuggeeExecutionKind.TEST);

        // Test for debug engage and break point hit @test:BeforeSuite
        Pair<BallerinaTestDebugPoint, StoppedEventArguments> debugHitInfo = waitForDebugHit(20000);
        Assert.assertEquals(debugHitInfo.getLeft(), testBreakpoints.get(0));

        // Test for break point hit at beforeFunc()
        resumeProgram(debugHitInfo.getRight(), DebugResumeKind.NEXT_BREAKPOINT);
        debugHitInfo = waitForDebugHit(10000);
        Assert.assertEquals(debugHitInfo.getLeft(), testBreakpoints.get(1));

        // Test for break point hit at testFunc()
        resumeProgram(debugHitInfo.getRight(), DebugResumeKind.NEXT_BREAKPOINT);
        debugHitInfo = waitForDebugHit(10000);
        Assert.assertEquals(debugHitInfo.getLeft(), testBreakpoints.get(2));

        // Test for break point hit at afterFunc()
        resumeProgram(debugHitInfo.getRight(), DebugResumeKind.NEXT_BREAKPOINT);
        debugHitInfo = waitForDebugHit(10000);
        Assert.assertEquals(debugHitInfo.getLeft(), testBreakpoints.get(4));

        resumeProgram(debugHitInfo.getRight(), DebugResumeKind.NEXT_BREAKPOINT);
        debugHitInfo = waitForDebugHit(10000);
        Assert.assertEquals(debugHitInfo.getLeft(), testBreakpoints.get(3));

        // Test for break point hit in mock function
        resumeProgram(debugHitInfo.getRight(), DebugResumeKind.NEXT_BREAKPOINT);
        debugHitInfo = waitForDebugHit(10000);
        Assert.assertEquals(debugHitInfo.getLeft(), testBreakpoints.get(6));

        // Test for break point hit @test:AfterSuite
        resumeProgram(debugHitInfo.getRight(), DebugResumeKind.NEXT_BREAKPOINT);
        debugHitInfo = waitForDebugHit(10000);
        Assert.assertEquals(debugHitInfo.getLeft(), testBreakpoints.get(5));
    }

    @AfterClass(alwaysRun = true)
    private void cleanup() {
        terminateDebugSession();
    }
}
