/*
 *  Copyright (c) 2021, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package io.ballerina.runtime.test.config.negative;

import io.ballerina.runtime.api.Module;
import io.ballerina.runtime.api.PredefinedTypes;
import io.ballerina.runtime.api.creators.TypeCreator;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.internal.configurable.ConfigResolver;
import io.ballerina.runtime.internal.configurable.VariableKey;
import io.ballerina.runtime.internal.configurable.providers.cli.CliProvider;
import io.ballerina.runtime.internal.configurable.providers.toml.TomlFileProvider;
import io.ballerina.runtime.internal.diagnostics.RuntimeDiagnosticLog;
import io.ballerina.runtime.internal.types.BIntersectionType;
import io.ballerina.runtime.internal.types.BType;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.ballerina.runtime.test.TestUtils.getConfigPathForNegativeCases;

/**
 * Test cases specific for configuration.
 */
public class ConfigNegativeTest {

    private static final Module ROOT_MODULE = new Module("rootOrg", "rootMod", "1.0.0");
    private final Module module = new Module("org", "mod1", "1.0.0");

    @Test(dataProvider = "different-config-use-cases-data-provider")
    public void testConfigErrors(String[] args, String tomlFilePath, VariableKey[] varKeys, int errorCount,
                                 int warnCount, String[] expectedDiagnosticMsgs) {
        RuntimeDiagnosticLog diagnosticLog = new RuntimeDiagnosticLog();
        Map<Module, VariableKey[]> configVarMap = new HashMap<>();
        configVarMap.put(module, varKeys);
        ConfigResolver configResolver;
        if (tomlFilePath != null) {
            configResolver = new ConfigResolver(ROOT_MODULE, configVarMap,
                                                diagnosticLog, List.of(
                                                new CliProvider(ROOT_MODULE, args),
                                                new TomlFileProvider(ROOT_MODULE,
                                                        getConfigPathForNegativeCases(tomlFilePath), Set.of(module))));

        } else {
            configResolver = new ConfigResolver(ROOT_MODULE, configVarMap,
                                                diagnosticLog, List.of(new CliProvider(ROOT_MODULE, args)));
        }
        configResolver.resolveConfigs();
        Assert.assertEquals(diagnosticLog.getErrorCount(), errorCount);
        Assert.assertEquals(diagnosticLog.getWarningCount(), warnCount);
        for (int i = 0; i < expectedDiagnosticMsgs.length; i++) {
            Assert.assertEquals(diagnosticLog.getDiagnosticList().get(i).toString(), expectedDiagnosticMsgs[i]);
        }
    }

    @DataProvider(name = "different-config-use-cases-data-provider")
    public Object[][] configErrorCases() {
        return new Object[][]{
                // Required but not given
                {new String[]{}, null,
                        new VariableKey[]{new VariableKey(module, "intVar", PredefinedTypes.TYPE_INT, null, true)}, 1
                        , 0,
                        new String[]{
                                "error: value not provided for required configurable variable 'intVar'"}},
                // Invalid toml value only
                {new String[]{}, "MismatchedTypeValues.toml",
                        new VariableKey[]{new VariableKey(module, "intVar", PredefinedTypes.TYPE_INT, null, true)}, 1
                        , 0,
                        new String[]{
                                "error: [MismatchedTypeValues.toml:(3:10,3:18)] configurable variable 'intVar' " +
                                        "is expected to be of type 'int', but found 'string'"
                        }},
                // Invalid cli value only
                {new String[]{"-Corg.mod1.intVar=waruna"}, null,
                        new VariableKey[]{new VariableKey(module, "intVar", PredefinedTypes.TYPE_INT, null, true)}, 1
                        , 0,
                        new String[]{
                                "error: [org.mod1.intVar=waruna] configurable variable 'intVar' is expected to be of " +
                                        "type 'int', but found 'waruna'"
                        }},
                // valid cli value invalid toml
                {new String[]{"-Corg.mod1.intVar=1234"}, "MismatchedTypeValues.toml",
                        new VariableKey[]{new VariableKey(module, "intVar", PredefinedTypes.TYPE_INT, null, true)}, 0
                        , 1, new String[]{
                                "warning: [MismatchedTypeValues.toml:(3:10,3:18)] configurable variable 'intVar'" +
                                        " is expected to be of type 'int', but found 'string'"
                        }},
                // valid toml value invalid cli
                {new String[]{"-Corg.mod1.intVar=waruna"}, "MatchedTypeValues.toml",
                        new VariableKey[]{new VariableKey(module, "intVar", PredefinedTypes.TYPE_INT, true)}, 0, 3,
                        new String[]{
                                "warning: [org.mod1.intVar=waruna] configurable variable 'intVar' is expected to be " +
                                        "of type 'int', but found 'waruna'"
                        }},
                // invalid toml value invalid cli
                {new String[]{"-Corg.mod1.intVar=waruna"}, "MismatchedTypeValues.toml",
                        new VariableKey[]{new VariableKey(module, "intVar", PredefinedTypes.TYPE_INT, null, true)}, 2
                        , 0, new String[]{
                                "error: [org.mod1.intVar=waruna] configurable variable 'intVar' is expected to be " +
                                        "of type 'int', but found 'waruna'",
                                "error: [MismatchedTypeValues.toml:(3:10,3:18)] configurable variable 'intVar'" +
                                        " is expected to be of type 'int', but found 'string'"
                        }},
                // invalid toml but valid cli
                {new String[]{"-Corg.mod1.intVar=1234"}, "Invalid.toml",
                        new VariableKey[]{new VariableKey(module, "intVar", PredefinedTypes.TYPE_INT, true)}, 0, 1,
                        new String[]{
                                "warning: invalid toml file : \n" +
                                        "[Invalid.toml:(3:1,3:1)] missing equal token\n" +
                                        "[Invalid.toml:(3:1,3:1)] missing value\n"}},
                // supported cli type but not toml type
                {new String[]{"-Corg.mod1.xmlVar=<book/>"}, "MatchedTypeValues.toml",
                        new VariableKey[]{new VariableKey(module, "xmlVar",
                                                          new BIntersectionType(module, new Type[]{},
                                                                                PredefinedTypes.TYPE_XML, 0, true),
                                                         null, true)}, 0, 3,
                        new String[]{
                                "warning: configurable variable 'xmlVar' with type 'xml<(lang.xml:Element|lang" +
                                        ".xml:Comment|lang.xml:ProcessingInstruction|lang.xml:Text)>' is not " +
                                        "supported as a toml value"}},
                // supported toml type but not cli type
                {new String[]{"-Corg.mod1.intArr=1234"}, "MatchedTypeValues.toml",
                        new VariableKey[]{
                                new VariableKey(module, "intArr",
                                                new BIntersectionType(module, new BType[]{},
                                                                      TypeCreator.createArrayType(
                                                                              PredefinedTypes.TYPE_INT), 0, false),
                                                null, true)}, 0, 3,
                        new String[]{
                                "warning: value for configurable variable 'intArr' with type " +
                                        "'" + "int[]' is not supported as a cli arg"}},
                // not supported both toml type and not cli type
                {new String[]{"-Corg.mod1.intArr=1234"}, "MatchedTypeValues.toml",
                        new VariableKey[]{new VariableKey(module, "intVar", PredefinedTypes.TYPE_MAP, null, true)}, 1
                        , 4, new String[]{"error: configurable variable 'intVar' with type 'map' is not supported"}},
                {new String[]{"-Corg.mod1.myMap=1234"}, null,
                        new VariableKey[]{
                                new VariableKey(module, "myMap",
                                                new BIntersectionType(module, new BType[]{}, PredefinedTypes.TYPE_MAP
                                                        , 0, true), null, true)}, 1
                        , 0, new String[]{"error: configurable variable 'myMap' with type 'map' is not supported"}},
        };
    }
}
