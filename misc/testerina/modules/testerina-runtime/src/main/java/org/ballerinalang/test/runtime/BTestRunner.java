/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
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

package org.ballerinalang.test.runtime;

import com.google.gson.Gson;
import io.ballerina.projects.util.ProjectConstants;
import io.ballerina.runtime.api.types.ArrayType;
import io.ballerina.runtime.api.types.BooleanType;
import io.ballerina.runtime.api.types.ByteType;
import io.ballerina.runtime.api.types.DecimalType;
import io.ballerina.runtime.api.types.FloatType;
import io.ballerina.runtime.api.types.IntegerType;
import io.ballerina.runtime.api.types.MapType;
import io.ballerina.runtime.api.types.ObjectType;
import io.ballerina.runtime.api.types.RecordType;
import io.ballerina.runtime.api.types.StringType;
import io.ballerina.runtime.api.types.TupleType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.types.XmlType;
import io.ballerina.runtime.api.values.BArray;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BString;
import io.ballerina.runtime.internal.scheduling.Scheduler;
import io.ballerina.runtime.internal.scheduling.Strand;
import io.ballerina.runtime.internal.values.ArrayValue;
import io.ballerina.runtime.internal.values.DecimalValue;
import io.ballerina.runtime.internal.values.MapValue;
import io.ballerina.runtime.internal.values.ObjectValue;
import io.ballerina.runtime.internal.values.XmlValue;
import org.ballerinalang.test.runtime.entity.Test;
import org.ballerinalang.test.runtime.entity.TestSuite;
import org.ballerinalang.test.runtime.entity.TesterinaFunction;
import org.ballerinalang.test.runtime.entity.TesterinaReport;
import org.ballerinalang.test.runtime.entity.TesterinaResult;
import org.ballerinalang.test.runtime.exceptions.BallerinaTestException;
import org.ballerinalang.test.runtime.util.TesterinaConstants;
import org.ballerinalang.test.runtime.util.TesterinaUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * BTestRunner entity class.
 */
public class BTestRunner {

    public static final String MODULE_INIT_CLASS_NAME = "$_init";
    private static final String FILE_NAME_PERIOD_SEPARATOR = "$$$";
    private static final String INIT_FUNCTION_NAME = ".<init>";
    private static final String START_FUNCTION_NAME = ".<start>";
    private static final String STOP_FUNCTION_NAME = ".<stop>";
    private static final String TEST_INIT_FUNCTION_NAME = ".<testinit>";
    private static final String TEST_START_FUNCTION_NAME = ".<teststart>";
    private static final String TEST_STOP_FUNCTION_NAME = ".<teststop>";
    private static final String CONFIGURATION_CLASS_NAME = "$configurationMapper";

    private PrintStream errStream;
    private PrintStream outStream;
    private TesterinaReport tReport;

    /**
     * Create Test Runner with given loggers.
     *
     * @param outStream The info log stream.
     * @param errStream The error log strem.
     */
    public BTestRunner(PrintStream outStream, PrintStream errStream) {
        this.outStream = outStream;
        this.errStream = errStream;
        tReport = new TesterinaReport(this.outStream);
    }

    /**
     * Executes a given set of ballerina program files when running tests using the build command.
     *
     * @param suite test meta data for module
     */
    public void runTest(TestSuite suite)  {
        // validate test suite
        validateTestSuite(suite);
        int[] testExecutionOrder = checkCyclicDependencies(suite.getTests());
        List<Test> sortedTests = orderTests(suite.getTests(), testExecutionOrder);
        suite.setTests(sortedTests);
        // execute the test programs
        execute(suite);
    }

    private static List<Test> orderTests(List<Test> tests, int[] testExecutionOrder) {
        List<Test> sortedTests = new ArrayList<>();
        for (int idx : testExecutionOrder) {
            sortedTests.add(tests.get(idx));
        }
        return sortedTests;
    }

    /**
     * Resolve function names to {@link TesterinaFunction}s.
     *
     * @param suite {@link TestSuite} whose functions to be resolved.
     */
    private static void validateTestSuite(TestSuite suite) {
        Set<String> functionNames = suite.getTestUtilityFunctions().keySet();
        for (Test test : suite.getTests()) {
            if (test.getBeforeTestFunction() != null) {
                if (!functionNames.contains(test.getBeforeTestFunction())) {
                    String msg = String.format("Cannot find the specified before function : [%s] for testerina " +
                            "function : [%s]", test.getBeforeTestFunction(), test.getTestName());
                    throw new BallerinaTestException(msg);
                }
            }
            if (test.getAfterTestFunction() != null) {
                if (!functionNames.contains(test.getAfterTestFunction())) {
                    String msg = String.format("Cannot find the specified after function : [%s] for testerina " +
                            "function : [%s]", test.getAfterTestFunction(), test.getTestName());
                    throw new BallerinaTestException(msg);
                }
            }

            if (test.getDataProvider() != null && !functionNames.contains(test.getDataProvider())) {
                String dataProvider = test.getDataProvider();
                String message = String.format("Data provider function [%s] cannot be found.", dataProvider);
                throw new BallerinaTestException(message);
            }

            for (String dependsOnFn : test.getDependsOnTestFunctions()) {
                if (functionNames.stream().noneMatch(func -> func.equals(dependsOnFn))) {
                    throw new BallerinaTestException("Cannot find the specified dependsOn function : "
                            + dependsOnFn);
                }
            }
        }
    }

    private static int[] checkCyclicDependencies(List<Test> tests) {
        int numberOfNodes = tests.size();
        int[] indegrees = new int[numberOfNodes];
        int[] sortedElts = new int[numberOfNodes];

        List<Integer>[] dependencyMatrix = new ArrayList[numberOfNodes];
        for (int i = 0; i < numberOfNodes; i++) {
            dependencyMatrix[i] = new ArrayList<>();
        }
        List<String> testNames = tests.stream()
                .map(Test::getTestName)
                .collect(Collectors.toList());

        int i = 0;
        for (Test test : tests) {
            if (!test.getDependsOnTestFunctions().isEmpty()) {
                for (String dependsOnFn : test.getDependsOnTestFunctions()) {
                    int idx = testNames.indexOf(dependsOnFn);
                    if (idx == -1) {
                        String message = String.format("Test [%s] depends on function [%s], but it couldn't be found" +
                                ".", test, dependsOnFn);
                        throw new BallerinaTestException(message);
                    }
                    dependencyMatrix[i].add(idx);
                }
            }
            i++;
        }

        // fill in degrees
        for (int j = 0; j < numberOfNodes; j++) {
            List<Integer> dependencies = dependencyMatrix[j];
            for (int node : dependencies) {
                indegrees[node]++;
            }
        }

        // Create a queue and enqueue all vertices with indegree 0
        Stack<Integer> stack = new Stack<>();
        for (i = 0; i < numberOfNodes; i++) {
            if (indegrees[i] == 0) {
                stack.add(i);
            }
        }

        // Initialize count of visited vertices
        int cnt = 0;

        // Create a vector to store result (A topological ordering of the vertices)
        Vector<Integer> topOrder = new Vector<Integer>();
        while (!stack.isEmpty()) {
            // Extract front of queue (or perform dequeue) and add it to topological order
            int u = stack.pop();
            topOrder.add(u);

            // Iterate through all its neighbouring nodes of dequeued node u and decrease their in-degree by 1
            for (int node : dependencyMatrix[u]) {
                // If in-degree becomes zero, add it to queue
                if (--indegrees[node] == 0) {
                    stack.push(node);
                }
            }
            cnt++;
        }

        // Check if there was a cycle
        if (cnt != numberOfNodes) {
            String message = "Cyclic test dependency detected";
            throw new BallerinaTestException(message);
        }

        i = numberOfNodes - 1;
        for (int elt : topOrder) {
            sortedElts[i] = elt;
            i--;
        }

        return sortedElts;
    }

    /**
     * Run all tests.
     *
     */
    private void execute(TestSuite suite) {
        // Check if there are tests in the test suite
        if (suite.getTests().size() == 0) {
            outStream.println("\tNo tests found\n");
            return;
        }

        AtomicBoolean shouldSkip = new AtomicBoolean();
        AtomicBoolean shouldSkipAfterSuite = new AtomicBoolean();
        AtomicBoolean shouldSkipAfterGroups = new AtomicBoolean();
        String packageName = suite.getPackageName();
        ClassLoader classLoader = ClassLoader.getSystemClassLoader();
        // Load module init class
        String initClassName = TesterinaUtils.getQualifiedClassName(suite.getOrgName(),
                                                                    suite.getPackageID(),
                                                                    suite.getVersion(),
                                                                    MODULE_INIT_CLASS_NAME);
        Class<?> initClazz;
        try {
            initClazz = classLoader.loadClass(initClassName);
        } catch (Throwable e) {
            throw new BallerinaTestException("failed to load init class :" + initClassName);
        }
        Class<?> configClazz;
        String configClassName = TesterinaUtils
                .getQualifiedClassName(suite.getOrgName(), suite.getPackageID(), suite.getVersion(),
                        CONFIGURATION_CLASS_NAME);
        try {
            configClazz = classLoader.loadClass(configClassName);
        } catch (Throwable e) {
            throw new BallerinaTestException("failed to load configuration class :" + configClassName);
        }
        Scheduler scheduler = new Scheduler(4, false);
        Scheduler initScheduler = new Scheduler(4, false);
        Class<?> testInitClazz = null;
        // For single bal files
        boolean hasTestablePackage = !packageName.equals(TesterinaConstants.DOT);
        if (hasTestablePackage) {
            // Load test init class
            String testClassName = TesterinaUtils.getQualifiedClassName(suite.getOrgName(), suite.getPackageID(),
                    suite.getVersion(), suite.getPackageID().replace(".", FILE_NAME_PERIOD_SEPARATOR));
            try {
                testInitClazz = classLoader.loadClass(testClassName);
            } catch (Throwable e) {
                throw new BallerinaTestException("failed to load Test init class :" + testClassName);
            }
        }
        shouldSkip.set(false);
        shouldSkipAfterSuite.set(false);
        shouldSkipAfterGroups.set(false);
        tReport.addPackageReport(packageName);
        tReport.setReportRequired(suite.isReportRequired());
        // Initialize the test suite.
        // This will init and start the test module.
        startSuite(suite, initScheduler, initClazz, testInitClazz, configClazz, hasTestablePackage);
        // Run Before suite functions
        executeBeforeSuiteFunctions(suite, classLoader, scheduler, shouldSkip, shouldSkipAfterSuite);
        // Run Tests
        executeTests(suite, packageName, classLoader, scheduler, shouldSkip, shouldSkipAfterGroups);
        // Run After suite functions
        executeAfterSuiteFunctions(suite, classLoader, scheduler, shouldSkipAfterSuite);
        // Call module stop and test stop function
        stopSuite(suite, scheduler, initClazz, testInitClazz, hasTestablePackage);
        // print module test results
        tReport.printTestSuiteSummary(packageName);
    }

    private void executeBeforeSuiteFunctions(TestSuite suite, ClassLoader classLoader, Scheduler scheduler,
                                             AtomicBoolean shouldSkip, AtomicBoolean shouldSkipAfterSuite) {
        suite.getBeforeSuiteFunctionNames().forEach(test -> {
            String errorMsg;
            try {
                invokeTestFunction(suite, test, classLoader, scheduler);
            } catch (Throwable e) {
                shouldSkip.set(true);
                shouldSkipAfterSuite.set(true);
                errorMsg = "\t[fail] " + test + " [before test suite function]" + ":\n\t    "
                        + formatErrorMessage(e);
                errStream.println(errorMsg);
            }
        });
    }

    private void executeTests(TestSuite suite, String packageName, ClassLoader classLoader, Scheduler scheduler,
                              AtomicBoolean shouldSkip, AtomicBoolean shouldSkipAfterGroups) {
        List<String> failedOrSkippedTests = new ArrayList<>();
        List<String> failedAfterFuncTests = new ArrayList<>();
        suite.getTests().forEach(test -> {
            AtomicBoolean shouldSkipTest = new AtomicBoolean(false);

            // execute the before groups functions
            executeBeforeGroupFunctions(test, suite, classLoader, scheduler, shouldSkip,
                    shouldSkipTest, shouldSkipAfterGroups);

            // run the before each tests
            executeBeforeEachFunction(test, suite, classLoader, scheduler, shouldSkip, shouldSkipTest);
            // run the before tests
            executeBeforeFunction(test, suite, classLoader, scheduler, shouldSkip, shouldSkipTest);
            // run the test
            executeFunction(test, suite, packageName, classLoader, scheduler, shouldSkip, shouldSkipTest,
                            failedOrSkippedTests, failedAfterFuncTests);
            // run the after tests
            executeAfterFunction(test, suite, classLoader, scheduler, shouldSkip, shouldSkipTest, failedAfterFuncTests);
            // run the after each tests
            executeAfterEachFunction(test, suite, classLoader, scheduler, shouldSkip, shouldSkipTest);

            // execute the after groups functions
            executeAfterGroupFunctions(test, suite, classLoader, scheduler, shouldSkip,
                    shouldSkipTest, shouldSkipAfterGroups);
        });
    }

    private void executeBeforeGroupFunctions(Test test, TestSuite suite, ClassLoader classLoader, Scheduler scheduler,
                                       AtomicBoolean shouldSkip, AtomicBoolean shouldSkipTest,
                                             AtomicBoolean shouldSkipAfterGroups)  {
        if (!shouldSkip.get() && !shouldSkipTest.get()) {
            for (String groupName : test.getGroups()) {
                if (!suite.getGroups().get(groupName).getBeforeGroupsFunctions().isEmpty()
                        && !suite.getGroups().get(groupName).isFirstTestExecuted()) {
                    // run before tests
                    String errorMsg;
                    for (String beforeGroupFunc : suite.getGroups().get(groupName).getBeforeGroupsFunctions()) {
                        try {
                            invokeTestFunction(suite, beforeGroupFunc, classLoader, scheduler);
                        } catch (Throwable e) {
                            shouldSkip.set(true);
                            shouldSkipTest.set(true);
                            shouldSkipAfterGroups.set(true);
                            errorMsg = String.format("\t[fail] " + beforeGroupFunc +
                                            " [before test group function for the test %s] :\n\t    %s", test,
                                    formatErrorMessage(e));
                            errStream.println(errorMsg);
                        }
                    }

                }
            }
        }
    }

    private void executeBeforeEachFunction(Test test, TestSuite suite, ClassLoader classLoader, Scheduler scheduler,
                                           AtomicBoolean shouldSkip, AtomicBoolean shouldSkipTest) {
        if (!shouldSkip.get() && !shouldSkipTest.get()) {
            // run the beforeEach tests
            suite.getBeforeEachFunctionNames().forEach(beforeEachTest -> {
                String errorMsg;
                try {
                    invokeTestFunction(suite, beforeEachTest, classLoader, scheduler);
                } catch (Throwable e) {
                    shouldSkip.set(true);
                    errorMsg = String.format("\t[fail] " + beforeEachTest +
                                                     " [before each test function for the test %s] :\n\t    %s",
                                             test,
                                             formatErrorMessage(e));
                    errStream.println(errorMsg);
                }
            });
        }
    }

    private void executeBeforeFunction(Test test, TestSuite suite, ClassLoader classLoader, Scheduler scheduler,
                                       AtomicBoolean shouldSkip, AtomicBoolean shouldSkipTest)  {
        if (!shouldSkip.get() && !shouldSkipTest.get()) {
            // run before tests
            String errorMsg;
            try {
                if (test.getBeforeTestFunction() != null) {
                    invokeTestFunction(suite, test.getBeforeTestFunction(), classLoader, scheduler);
                }
            } catch (Throwable e) {
                shouldSkipTest.set(true);
                errorMsg = String.format("\t[fail] " + test.getBeforeTestFunction() +
                                                 " [before test function for the test %s] :\n\t    %s",
                                         test, formatErrorMessage(e));
                errStream.println(errorMsg);
            }
        }
    }

    private void executeFunction(Test test, TestSuite suite, String packageName, ClassLoader classLoader,
                                 Scheduler scheduler, AtomicBoolean shouldSkip, AtomicBoolean shouldSkipTest,
                                 List<String> failedOrSkippedTests, List<String> failedAfterFuncTests) {
        TesterinaResult functionResult;

        try {
            if (isTestDependsOnFailedFunctions(test.getDependsOnTestFunctions(), failedOrSkippedTests) ||
                isTestDependsOnFailedFunctions(test.getDependsOnTestFunctions(), failedAfterFuncTests)) {
                shouldSkipTest.set(true);
            }

            // Check whether the this test depends on any failed or skipped functions
            if (!shouldSkip.get() && !shouldSkipTest.get()) {
                Object valueSets = null;
                if (test.getDataProvider() != null) {
                    valueSets = invokeTestFunction(suite, test.getDataProvider(), classLoader, scheduler);
                }
                if (valueSets == null) {
                    invokeTestFunction(suite, test.getTestName(), classLoader, scheduler);
                    // report the test result
                    functionResult = new TesterinaResult(test.getTestName(), true, shouldSkip
                            .get(), null);
                    tReport.addFunctionResult(packageName, functionResult);
                } else {
                    Class<?>[] argTypes = extractArgumentTypes(valueSets);
                    List<Object[]> argList = extractArguments(valueSets);
                    for (Object[] arg : argList) {
                        invokeTestFunction(suite, test.getTestName(), classLoader, scheduler, argTypes, arg);
                        TesterinaResult result = new TesterinaResult(test.getTestName(), true,
                                                                     shouldSkip.get(), null);
                        tReport.addFunctionResult(packageName, result);
                    }
                }
            } else {
                // If the test function is skipped lets add it to the failed test list
                failedOrSkippedTests.add(test.getTestName());
                // report the test result
                functionResult = new TesterinaResult(test.getTestName(), false, true, null);
                tReport.addFunctionResult(packageName, functionResult);
            }
        } catch (Throwable e) {
            // If the test function is skipped lets add it to the failed test list
            failedOrSkippedTests.add(test.getTestName());
            // report the test result
            functionResult = new TesterinaResult(test.getTestName(), false, shouldSkip.get(),
                                                 formatErrorMessage(e));
            tReport.addFunctionResult(packageName, functionResult);
        }
        for (String groupName : test.getGroups()) {
            suite.getGroups().get(groupName).incrementExecutedCount();
        }

        if (!packageName.equals(TesterinaConstants.DOT)) {
            Path sourceRootPath = Paths.get(suite.getSourceRootPath()).resolve(TesterinaConstants.TARGET_DIR_NAME);
            Path jsonPath = Paths.get(sourceRootPath.toString(), TesterinaConstants.RERUN_TEST_JSON_FILE);
            File jsonFile = new File(jsonPath.toString());
            writeFailedTestsToJson(failedOrSkippedTests, jsonFile);
        }

    }

    private void executeAfterFunction(Test test, TestSuite suite, ClassLoader classLoader, Scheduler scheduler,
                                      AtomicBoolean shouldSkip, AtomicBoolean shouldSkipTest,
                                      List<String> failedAfterFuncTests)  {
        if (!shouldSkip.get() && !shouldSkipTest.get()) {
            try {
                if (test.getAfterTestFunction() != null) {
                    invokeTestFunction(suite, test.getAfterTestFunction(), classLoader, scheduler);
                }
            } catch (Throwable e) {
                failedAfterFuncTests.add(test.getTestName());
                String error = String.format("\t[fail] " + test + " [after test function for the test %s] :\n\t    %s",
                        test, formatErrorMessage(e));
                errStream.println(error);
            }
        }
    }

    private void executeAfterEachFunction(Test test, TestSuite suite, ClassLoader classLoader, Scheduler scheduler,
                                          AtomicBoolean shouldSkip, AtomicBoolean shouldSkipTest) {
        if (!shouldSkip.get() && !shouldSkipTest.get()) {
            suite.getAfterEachFunctionNames().forEach(afterEachTest -> {
                try {
                    invokeTestFunction(suite, afterEachTest, classLoader, scheduler);
                } catch (Throwable e) {
                    shouldSkip.set(true);
                    String errorMsg = String.format("\t[fail] " + afterEachTest +
                                    " [after each test function for the test %s] :\n\t    %s",
                            test, formatErrorMessage(e));
                    errStream.println(errorMsg);
                }
            });
        }
    }

    private void executeAfterGroupFunctions(Test test, TestSuite suite, ClassLoader classLoader, Scheduler scheduler,
                                             AtomicBoolean shouldSkip, AtomicBoolean shouldSkipTest,
                                            AtomicBoolean shouldSkipAfterGroups)  {
        if (!shouldSkipAfterGroups.get() && !shouldSkip.get() && !shouldSkipTest.get()) {
            for (String groupName : test.getGroups()) {
                if (!suite.getGroups().get(groupName).getAfterGroupsFunctions().isEmpty()
                        && suite.getGroups().get(groupName).isLastTestExecuted()) {
                    // run before tests
                    String errorMsg;
                    for (String afterGroupFunc : suite.getGroups().get(groupName).getAfterGroupsFunctions()) {
                        try {
                            invokeTestFunction(suite, afterGroupFunc, classLoader, scheduler);
                        } catch (Throwable e) {
                            shouldSkip.set(true);
                            shouldSkipTest.set(true);
                            shouldSkipAfterGroups.set(true);
                            errorMsg = String.format("\t[fail] " + afterGroupFunc +
                                            " [after test group function for the test %s] :\n\t    %s", test,
                                    formatErrorMessage(e));
                            errStream.println(errorMsg);
                        }
                    }

                }
            }
        }
    }

    private void executeAfterSuiteFunctions(TestSuite suite, ClassLoader classLoader, Scheduler scheduler,
                                            AtomicBoolean shouldSkipAfterSuite) {
        suite.getAfterSuiteFunctionNames().forEach((func, alwaysRun) -> {
            if (!shouldSkipAfterSuite.get() || alwaysRun.get()) {
                String errorMsg;
                try {
                    invokeTestFunction(suite, func, classLoader, scheduler);
                } catch (Throwable e) {
                    errorMsg = String.format("\t[fail] " + func + " [after test suite function] :\n\t    " +
                            "%s", formatErrorMessage(e));
                    errStream.println(errorMsg);
                }
            }
        });
    }

    private void startSuite(TestSuite suite, Scheduler initScheduler, Class<?> initClazz, Class<?> testInitClazz,
                            Class<?> configClazz, boolean hasTestablePackage) {
        TesterinaFunction init = new TesterinaFunction(initClazz, INIT_FUNCTION_NAME, initScheduler);
        TesterinaFunction start = new TesterinaFunction(initClazz, START_FUNCTION_NAME, initScheduler);
        TesterinaFunction configInit = new TesterinaFunction(configClazz, "$configureInit", initScheduler);
        // As the init function we need to use $moduleInit to initialize all the dependent modules
        // properly.

        Object response = configInit.directInvoke(new Class[]{Path.class}, new Object[]{getConfigPath(suite)});
        if (response instanceof Throwable) {
            throw new BallerinaTestException("Configurable initialization for test suite failed due to " +
                    response.toString(), (Throwable) response);
        }

        init.setName("$moduleInit");
        response = init.invoke();
        if (response instanceof Throwable) {
            throw new BallerinaTestException("Dependant module initialization for test suite failed due to " +
                    response.toString(), (Throwable) response);
        }
        // Now we initialize the init of testable module.
        if (hasTestablePackage) {
            TesterinaFunction testInit =
                    new TesterinaFunction(testInitClazz, TEST_INIT_FUNCTION_NAME, initScheduler);
            response = testInit.invoke();
            if (response instanceof Throwable) {
                throw new BallerinaTestException("Test module initialization for test suite failed due to " +
                        response.toString(), (Throwable) response);
            }
        }
        // As the start function we need to use $moduleStart to start all the dependent modules
        // properly.
        start.setName("$moduleStart");
        start.invoke();
        // Invoke start function of the testable module
        if (hasTestablePackage) {
            TesterinaFunction testStart =
                    new TesterinaFunction(testInitClazz, TEST_START_FUNCTION_NAME, initScheduler);
            testStart.invoke();
        }
        // Once the start function finish we will re start the scheduler with immortal true
        initScheduler.setImmortal(true);
        Thread immortalThread = new Thread(initScheduler::start, "module-start");
        immortalThread.setDaemon(true);
        immortalThread.start();
    }

    private Path getConfigPath(TestSuite testSuite) {
        String moduleName = testSuite.getModuleName();
        Path configFilePath = Paths.get(testSuite.getSourceRootPath());
        if (!moduleName.equals("")) {
            configFilePath = configFilePath.resolve(ProjectConstants.MODULES_ROOT).resolve(moduleName);
        }
        return configFilePath.resolve(ProjectConstants.TEST_DIR_NAME);
    }

    private void stopSuite(TestSuite suite, Scheduler scheduler, Class<?> initClazz, Class<?> testInitClazz,
                           boolean hasTestablePackage) {
        TesterinaFunction stop = new TesterinaFunction(initClazz, STOP_FUNCTION_NAME, scheduler);
        // Invoke stop function of the testable module.
        if (hasTestablePackage) {
            TesterinaFunction testStop =
                    new TesterinaFunction(testInitClazz, TEST_STOP_FUNCTION_NAME, scheduler);
            testStop.scheduler = scheduler;
            testStop.invoke();
        }
        stop.setName("$moduleStop");
        stop.directInvoke(new Class<?>[]{Scheduler.ListenerRegistry.class},
                new Object[]{scheduler.getListenerRegistry()});
    }

    private Object invokeTestFunction(TestSuite suite, String functionName, ClassLoader classLoader,
                                      Scheduler scheduler) throws ClassNotFoundException {
        Class<?> functionClass = classLoader.loadClass(suite.getTestUtilityFunctions().get(functionName));
        TesterinaFunction testerinaFunction = new TesterinaFunction(functionClass, functionName, scheduler);
        return testerinaFunction.invoke();
    }

    public void invokeTestFunction(TestSuite suite, String functionName, ClassLoader classLoader,
                                   Scheduler scheduler, Class<?>[] types, Object[] args) throws ClassNotFoundException {
        Class<?> functionClass = classLoader.loadClass(suite.getTestUtilityFunctions().get(functionName));
        TesterinaFunction testerinaFunction = new TesterinaFunction(functionClass, functionName, scheduler);
        testerinaFunction.invoke(types, args);
    }

    private String formatErrorMessage(Throwable e) {
        String message;
        if (e.getCause() instanceof BError) {
            try {
                message = ((BError) e.getCause()).getPrintableStackTrace();
            } catch (ClassCastException castException) {
                // throw the exception to top
                throw new BallerinaTestException(e);
            }
        } else if (e instanceof BallerinaTestException) {
            throw (BallerinaTestException) e;
        } else {
            throw new BallerinaTestException(e);
        }
        return message;
    }

    private boolean isTestDependsOnFailedFunctions(List<String> failedOrSkippedTests, List<String> dependentTests) {
        return failedOrSkippedTests.stream().parallel().anyMatch(dependentTests::contains);
    }

    /**
     * Extract function arguments from the values sets.
     *
     * @param valueSets user provided value sets
     * @return a list of function arguments
     */
    private List<Object[]> extractArguments(Object valueSets) {
        List<Object[]> argsList = new ArrayList<>();

        if (valueSets instanceof BArray) {
            BArray bArray = (BArray) valueSets;
            if (bArray.getElementType() instanceof ArrayType) {
                // Ok we have an array of an array
                for (int i = 0; i < bArray.size(); i++) {
                    // Iterate array elements and set parameters
                    setTestFunctionParams(argsList, (BArray) bArray.get(i));
                }
            } else {
                // Iterate array elements and set parameters
                setTestFunctionParams(argsList, bArray);
            }
        }
        return argsList;
    }

    /**
     * Extract the parameter types from a valueset.
     * @param valueSets use provided value sets
     * @return a list of calss types.
     */
    private static Class<?>[] extractArgumentTypes(Object valueSets) {
        List<Class<?>> typeList = new ArrayList<>();
        typeList.add(Strand.class);
        if (valueSets instanceof BArray) {
            BArray bArray = (BArray) valueSets;
            if (bArray.getElementType() instanceof ArrayType) {
                // Ok we have an array of an array
                // Get the first entry
                // Iterate elements and get class types.
                setTestFunctionSignature(typeList, (BArray) bArray.get(0));
            } else {
                // Iterate elements and get class types.
                setTestFunctionSignature(typeList, bArray);
            }
        }
        Class<?>[] typeListArray = new Class[typeList.size()];
        typeList.toArray(typeListArray);
        return typeListArray;
    }

    private static void setTestFunctionSignature(List<Class<?>> typeList, BArray bArray) {
        Class<?> type = getArgTypeToClassMapping(bArray.getElementType());
        for (int i = 0; i < bArray.size(); i++) {
            // Add the param type.
            typeList.add(type);
            // This is in jvm function signature to tel if args is passed or not.
            typeList.add(Boolean.TYPE);
        }
    }

    private static void setTestFunctionParams(List<Object[]> valueList, BArray bArray) {
        List<Object> params = new ArrayList<>();
        // Add a place holder to Strand
        params.add(new Object());
        for (int i = 0; i < bArray.size(); i++) {
            // Add the param type.
            params.add(bArray.get(i));
            // This is in jvm function signature to tel if args is passed or not.
            params.add(Boolean.TRUE);
        }
        valueList.add(params.toArray());
    }

    private static Class<?> getArgTypeToClassMapping(Type elementType) {
        Class<?> type;
        // Refer jvm_method_gen.bal getArgTypeSignature for proper type matching
        if (elementType instanceof StringType) {
            type = BString.class;
        } else if (elementType instanceof IntegerType) {
            type = Long.TYPE;
        } else if (elementType instanceof BooleanType) {
            type = Boolean.TYPE;
        } else if (elementType instanceof DecimalType) {
            type = DecimalValue.class;
        } else if (elementType instanceof ByteType) {
            type = Integer.TYPE;
        } else if (elementType instanceof ArrayType || elementType instanceof TupleType) {
            type = ArrayValue.class;
        } else if (elementType instanceof FloatType) {
            type = Double.TYPE;
        } else if (elementType instanceof MapType || elementType instanceof RecordType) {
            type = MapValue.class;
        } else if (elementType instanceof XmlType) {
            type = XmlValue.class;
        } else if (elementType instanceof ObjectType) {
            type = ObjectValue.class;
        } else {
            // default case
            type = Object.class;
        }
        return type;
    }

    /**
     * Return the Test report of program runner.
     *
     * @return {@link TesterinaReport} object
     */
    public TesterinaReport getTesterinaReport() {
        return tReport;
    }

    /**
     * Store the failed tests as an array in the JSON cache.
     * @param failedTests List of failed tests
     * @param jsonFile File to save failed tests
     */
    private void writeFailedTestsToJson(List<String> failedTests, File jsonFile) {
        String errorMsg;

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(jsonFile), StandardCharsets.UTF_8)) {
            Gson gson = new Gson();
            String json = gson.toJson(failedTests);
            writer.write(new String(json.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));
        } catch (IOException e) {
            errorMsg = "Could not write to Rerun Test json. Rerunning tests will not work";
            errStream.println(errorMsg + ":" + e.getMessage());
        }

    }

}
