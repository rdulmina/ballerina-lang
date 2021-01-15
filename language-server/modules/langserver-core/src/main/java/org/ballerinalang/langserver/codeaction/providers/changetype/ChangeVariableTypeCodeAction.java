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
package org.ballerinalang.langserver.codeaction.providers.changetype;

import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.api.symbols.VariableSymbol;
import io.ballerina.compiler.syntax.tree.AssignmentStatementNode;
import io.ballerina.compiler.syntax.tree.BindingPatternNode;
import io.ballerina.compiler.syntax.tree.CaptureBindingPatternNode;
import io.ballerina.compiler.syntax.tree.ExpressionNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NonTerminalNode;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SimpleNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.compiler.syntax.tree.TypedBindingPatternNode;
import io.ballerina.compiler.syntax.tree.VariableDeclarationNode;
import io.ballerina.tools.diagnostics.Diagnostic;
import io.ballerina.tools.diagnostics.properties.DiagnosticProperty;
import io.ballerina.tools.diagnostics.properties.DiagnosticPropertyKind;
import org.ballerinalang.annotation.JavaSPIService;
import org.ballerinalang.langserver.codeaction.CodeActionUtil;
import org.ballerinalang.langserver.common.constants.CommandConstants;
import org.ballerinalang.langserver.common.utils.CommonUtil;
import org.ballerinalang.langserver.commons.CodeActionContext;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.TextEdit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Code Action for change variable type.
 *
 * @since 2.0.0
 */
@JavaSPIService("org.ballerinalang.langserver.commons.codeaction.spi.LSCodeActionProvider")
public class ChangeVariableTypeCodeAction extends TypeCastCodeAction {
    private static final int FOUND_SYMBOL_INDEX = 1;

    /**
     * {@inheritDoc}
     */
    @Override
    public List<CodeAction> getDiagBasedCodeActions(Diagnostic diagnostic,
                                                    CodeActionContext context) {
        if (!(diagnostic.message().contains(CommandConstants.INCOMPATIBLE_TYPES))) {
            return Collections.emptyList();
        }
        List<DiagnosticProperty<?>> props = diagnostic.properties();
        if (props.size() != 2 || props.get(FOUND_SYMBOL_INDEX).kind() != DiagnosticPropertyKind.SYMBOLIC) {
            return Collections.emptyList();
        }

        Symbol rhsTypeSymbol = ((DiagnosticProperty<Symbol>) props.get(FOUND_SYMBOL_INDEX)).value();

        // Skip, non-local var declarations
        Node matchedNode = context.positionDetails().matchedNode();
        if (matchedNode.kind() != SyntaxKind.LOCAL_VAR_DECL &&
                matchedNode.kind() != SyntaxKind.MODULE_VAR_DECL &&
                matchedNode.kind() != SyntaxKind.ASSIGNMENT_STATEMENT) {
            return Collections.emptyList();
        }

        Optional<ExpressionNode> typeNode = getTypeNode(matchedNode, context);
        Optional<String> variableName = getVariableName(matchedNode);
        if (typeNode.isEmpty() || variableName.isEmpty()) {
            return Collections.emptyList();
        }

        // Derive possible types
        List<CodeAction> actions = new ArrayList<>();
        List<TextEdit> importEdits = new ArrayList<>();
        List<String> types = CodeActionUtil.getPossibleTypes((TypeSymbol) rhsTypeSymbol, importEdits, context);
        for (String type : types) {
            List<TextEdit> edits = new ArrayList<>();
            edits.add(new TextEdit(CommonUtil.toRange(typeNode.get().lineRange()), type));
            String commandTitle = String.format(CommandConstants.CHANGE_VAR_TYPE_TITLE, variableName.get(), type);
            actions.add(createQuickFixCodeAction(commandTitle, edits, context.fileUri()));
        }
        return actions;
    }

    private Optional<ExpressionNode> getTypeNode(Node matchedNode, CodeActionContext context) {
        switch (matchedNode.kind()) {
            case LOCAL_VAR_DECL:
                return Optional.of(
                        ((VariableDeclarationNode) matchedNode).typedBindingPattern().typeDescriptor());
            case ASSIGNMENT_STATEMENT:
                Optional<VariableSymbol> optVariableSymbol = getVariableSymbol(context, matchedNode);
                if (optVariableSymbol.isEmpty()) {
                    return Optional.empty();
                }
                SyntaxTree syntaxTree = context.workspace().syntaxTree(context.filePath()).orElseThrow();
                NonTerminalNode node = CommonUtil.findNode(optVariableSymbol.get(), syntaxTree);
                if (node.kind() == SyntaxKind.TYPED_BINDING_PATTERN) {
                    return Optional.of(((TypedBindingPatternNode) node).typeDescriptor());
                } else {
                    return Optional.empty();
                }
            default:
                return Optional.empty();
        }
    }

    private Optional<String> getVariableName(Node matchedNode) {
        switch (matchedNode.kind()) {
            case LOCAL_VAR_DECL:
                VariableDeclarationNode variableDeclrNode = (VariableDeclarationNode) matchedNode;
                BindingPatternNode bindingPatternNode = variableDeclrNode.typedBindingPattern().bindingPattern();
                if (bindingPatternNode.kind() != SyntaxKind.CAPTURE_BINDING_PATTERN) {
                    return Optional.empty();
                }
                CaptureBindingPatternNode captureBindingPatternNode = (CaptureBindingPatternNode) bindingPatternNode;
                return Optional.of(captureBindingPatternNode.variableName().text());
            case ASSIGNMENT_STATEMENT:
                AssignmentStatementNode assignmentStmtNode = (AssignmentStatementNode) matchedNode;
                Node varRef = assignmentStmtNode.varRef();
                if (varRef.kind() == SyntaxKind.SIMPLE_NAME_REFERENCE) {
                    return Optional.of(((SimpleNameReferenceNode) varRef).name().text());
                } else if (varRef.kind() == SyntaxKind.QUALIFIED_NAME_REFERENCE) {
                    return Optional.of(((QualifiedNameReferenceNode) varRef).identifier().text());
                }
                return Optional.empty();
            default:
                return Optional.empty();
        }
    }
}
