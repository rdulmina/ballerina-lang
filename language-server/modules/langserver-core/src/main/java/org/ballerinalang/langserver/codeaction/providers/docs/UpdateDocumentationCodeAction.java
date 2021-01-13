/*
 * Copyright (c) 2020, WSO2 Inc. (http://wso2.com) All Rights Reserved.
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
package org.ballerinalang.langserver.codeaction.providers.docs;

import io.ballerina.compiler.syntax.tree.NonTerminalNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import org.ballerinalang.annotation.JavaSPIService;
import org.ballerinalang.langserver.codeaction.CodeActionUtil;
import org.ballerinalang.langserver.codeaction.providers.AbstractCodeActionProvider;
import org.ballerinalang.langserver.command.executors.UpdateDocumentationExecutor;
import org.ballerinalang.langserver.common.constants.CommandConstants;
import org.ballerinalang.langserver.common.utils.CommonUtil;
import org.ballerinalang.langserver.commons.CodeActionContext;
import org.ballerinalang.langserver.commons.command.CommandArgument;
import org.ballerinalang.util.diagnostic.DiagnosticWarningCode;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.CodeActionKind;
import org.eclipse.lsp4j.Command;
import org.eclipse.lsp4j.Diagnostic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Code Action for adding single documentation.
 *
 * @since 2.0.0
 */
@JavaSPIService("org.ballerinalang.langserver.commons.codeaction.spi.LSCodeActionProvider")
public class UpdateDocumentationCodeAction extends AbstractCodeActionProvider {

    @Override
    public List<CodeAction> getDiagBasedCodeActions(Diagnostic diagnostic, CodeActionContext context) {
        if (!DiagnosticWarningCode.UNDOCUMENTED_PARAMETER.diagnosticId().equals(diagnostic.getCode()) &&
                !DiagnosticWarningCode.NO_SUCH_DOCUMENTABLE_PARAMETER.diagnosticId().equals(diagnostic.getCode()) &&
                !DiagnosticWarningCode.PARAMETER_ALREADY_DOCUMENTED.diagnosticId().equals(diagnostic.getCode()) &&
                !DiagnosticWarningCode.UNDOCUMENTED_FIELD.diagnosticId().equals(diagnostic.getCode()) &&
                !DiagnosticWarningCode.NO_SUCH_DOCUMENTABLE_FIELD.diagnosticId().equals(diagnostic.getCode()) &&
                !DiagnosticWarningCode.FIELD_ALREADY_DOCUMENTED.diagnosticId().equals(diagnostic.getCode()) &&
                !DiagnosticWarningCode.UNDOCUMENTED_VARIABLE.diagnosticId().equals(diagnostic.getCode()) &&
                !DiagnosticWarningCode.NO_SUCH_DOCUMENTABLE_VARIABLE.diagnosticId().equals(diagnostic.getCode()) &&
                !DiagnosticWarningCode.VARIABLE_ALREADY_DOCUMENTED.diagnosticId().equals(diagnostic.getCode()) &&
                !DiagnosticWarningCode.UNDOCUMENTED_RETURN_PARAMETER.diagnosticId().equals(diagnostic.getCode()) &&
                !DiagnosticWarningCode.NO_DOCUMENTABLE_RETURN_PARAMETER.diagnosticId().equals(diagnostic.getCode()) &&
                !DiagnosticWarningCode.INVALID_DOCUMENTATION_REFERENCE.diagnosticId().equals(diagnostic.getCode()) &&
                !DiagnosticWarningCode.INVALID_USAGE_OF_PARAMETER_REFERENCE.diagnosticId().equals(
                        diagnostic.getCode()) &&
                !DiagnosticWarningCode.NO_SUCH_DOCUMENTABLE_ATTRIBUTE.diagnosticId().equals(diagnostic.getCode()) &&
                !DiagnosticWarningCode.INVALID_USE_OF_ENDPOINT_DOCUMENTATION_ATTRIBUTE.diagnosticId().equals(
                        diagnostic.getCode()) &&
                !DiagnosticWarningCode.DUPLICATE_DOCUMENTED_ATTRIBUTE.diagnosticId().equals(diagnostic.getCode()) &&
                !DiagnosticWarningCode.USAGE_OF_DEPRECATED_CONSTRUCT.diagnosticId().equals(diagnostic.getCode())) {
            return Collections.emptyList();
        }
        String docUri = context.fileUri();
        SyntaxTree syntaxTree = context.workspace().syntaxTree(context.filePath()).orElseThrow();
        Optional<NonTerminalNode> topLevelNode = CodeActionUtil.getTopLevelNode(context.cursorPosition(), syntaxTree);
        if (topLevelNode.isEmpty()) {
            return Collections.emptyList();
        }
        NonTerminalNode node = topLevelNode.get();
        if (node.kind() == SyntaxKind.MARKDOWN_DOCUMENTATION) {
            // If diagnostic message positions inside docs, get parent() node
            node = node.parent().parent();
        }
        CommandArgument docUriArg = CommandArgument.from(CommandConstants.ARG_KEY_DOC_URI, docUri);
        CommandArgument lineStart = CommandArgument.from(CommandConstants.ARG_KEY_NODE_RANGE,
                                                         CommonUtil.toRange(node.lineRange()));
        List<Object> args = new ArrayList<>(Arrays.asList(docUriArg, lineStart));

        CodeAction action = new CodeAction(CommandConstants.UPDATE_DOCUMENTATION_TITLE);
        action.setKind(CodeActionKind.QuickFix);
        action.setDiagnostics(Collections.singletonList(diagnostic));
        Command command = new Command(CommandConstants.UPDATE_DOCUMENTATION_TITLE, UpdateDocumentationExecutor.COMMAND,
                                      args);
        action.setCommand(command);
        return Collections.singletonList(action);
    }
}
