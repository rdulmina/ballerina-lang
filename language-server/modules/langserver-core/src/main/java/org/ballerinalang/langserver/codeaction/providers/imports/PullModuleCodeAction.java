/*
 * Copyright (c) 2019, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ballerinalang.langserver.codeaction.providers.imports;

import io.ballerina.tools.diagnostics.Diagnostic;
import org.ballerinalang.annotation.JavaSPIService;
import org.ballerinalang.langserver.codeaction.CodeActionUtil;
import org.ballerinalang.langserver.codeaction.providers.AbstractCodeActionProvider;
import org.ballerinalang.langserver.common.constants.CommandConstants;
import org.ballerinalang.langserver.commons.CodeActionContext;
import org.ballerinalang.langserver.commons.LanguageServerContext;
import org.ballerinalang.langserver.commons.capability.LSClientCapabilities;
import org.ballerinalang.langserver.commons.codeaction.spi.DiagBasedPositionDetails;
import org.ballerinalang.langserver.commons.command.CommandArgument;
import org.ballerinalang.util.diagnostic.DiagnosticErrorCode;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Code Action for pulling a package from central.
 *
 * @since 1.1.1
 */
@JavaSPIService("org.ballerinalang.langserver.commons.codeaction.spi.LSCodeActionProvider")
public class PullModuleCodeAction extends AbstractCodeActionProvider {

    public static final String NAME = "Pull Module";

    /** Command used at the LS client side to trigger pull module code command. */
    private static final String PULL_MODULE_COMMAND = "ballerina.packages.pull";
    private static final int MISSING_MODULE_NAME_INDEX = 0;

    @Override
    public boolean isEnabled(LanguageServerContext serverContext) {
        LSClientCapabilities clientCapabilities = serverContext.get(LSClientCapabilities.class);
        return clientCapabilities != null && clientCapabilities.getInitializationOptions().isPullModuleSupported();
    }

    @Override
    public List<CodeAction> getDiagBasedCodeActions(Diagnostic diagnostic,
                                                    DiagBasedPositionDetails positionDetails,
                                                    CodeActionContext context) {
        if (!DiagnosticErrorCode.MODULE_NOT_FOUND.diagnosticId().equals(diagnostic.diagnosticInfo().code())) {
            return Collections.emptyList();
        }

        Optional<String> moduleName = positionDetails.diagnosticProperty(MISSING_MODULE_NAME_INDEX);
        if (moduleName.isEmpty()) {
            return Collections.emptyList();
        }

        CommandArgument uriArg = CommandArgument.from(CommandConstants.ARG_KEY_DOC_URI, context.fileUri());
        List<Diagnostic> diagnostics = new ArrayList<>();

        List<Object> args = new ArrayList<>();
        args.add(uriArg);
        args.add(CommandArgument.from(CommandConstants.ARG_KEY_MODULE_NAME, moduleName.get()));

        String commandTitle = CommandConstants.PULL_MOD_TITLE;
        CodeAction action = new CodeAction(commandTitle);
        action.setKind(CodeActionKind.QuickFix);
        action.setCommand(new Command(commandTitle, PULL_MODULE_COMMAND, args));
        action.setDiagnostics(CodeActionUtil.toDiagnostics(diagnostics));
        return Collections.singletonList(action);
    }

    @Override
    public String getName() {
        return NAME;
    }
}