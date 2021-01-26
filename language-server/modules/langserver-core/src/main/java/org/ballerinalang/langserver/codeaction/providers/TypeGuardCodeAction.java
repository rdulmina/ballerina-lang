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
package org.ballerinalang.langserver.codeaction.providers;

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.api.symbols.SymbolKind;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.api.symbols.UnionTypeSymbol;
import io.ballerina.compiler.api.symbols.VariableSymbol;
import io.ballerina.compiler.syntax.tree.AssignmentStatementNode;
import io.ballerina.compiler.syntax.tree.BindingPatternNode;
import io.ballerina.compiler.syntax.tree.CaptureBindingPatternNode;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NonTerminalNode;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SimpleNameReferenceNode;
import io.ballerina.compiler.syntax.tree.SyntaxKind;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.compiler.syntax.tree.TypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.TypedBindingPatternNode;
import io.ballerina.compiler.syntax.tree.VariableDeclarationNode;
import io.ballerina.projects.Document;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.ballerinalang.annotation.JavaSPIService;
import org.ballerinalang.langserver.codeaction.CodeActionUtil;
import org.ballerinalang.langserver.common.constants.CommandConstants;
import org.ballerinalang.langserver.common.utils.CommonUtil;
import org.ballerinalang.langserver.commons.CodeActionContext;
import org.ballerinalang.langserver.commons.codeaction.CodeActionNodeType;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Code Action for type guard variable assignment.
 *
 * @since 2.0.0
 */
@JavaSPIService("org.ballerinalang.langserver.commons.codeaction.spi.LSCodeActionProvider")
public class TypeGuardCodeAction extends AbstractCodeActionProvider {
    public TypeGuardCodeAction() {
        super(Arrays.asList(CodeActionNodeType.LOCAL_VARIABLE,
                            CodeActionNodeType.ASSIGNMENT));
    }

    @Override
    public List<CodeAction> getNodeBasedCodeActions(CodeActionContext context) {
        Node matchedNode = context.positionDetails().matchedNode();
        boolean isAssignment = matchedNode.kind() == SyntaxKind.ASSIGNMENT_STATEMENT;
        boolean isVarDeclr = matchedNode.kind() == SyntaxKind.LOCAL_VAR_DECL;
        // Skip, if not a var declaration or assignment
        if (!isVarDeclr && !isAssignment) {
            return Collections.emptyList();
        }

        // Get LHS union type-symbol and type-desc-node of the variable
        Optional<Pair<UnionTypeSymbol, TypeDescriptorNode>> varTypeSymbolAndTypeDescNodePair =
                getVarTypeSymbolAndTypeNode(matchedNode, context);
        if (varTypeSymbolAndTypeDescNodePair.isEmpty()) {
            return Collections.emptyList();
        }
        UnionTypeSymbol varTypeSymbol = varTypeSymbolAndTypeDescNodePair.get().getLeft();

        // Get var name
        Optional<String> varName = getVariableName(matchedNode);
        if (varName.isEmpty()) {
            return Collections.emptyList();
        }

        // Add type guard code action
        String commandTitle = CommandConstants.TYPE_GUARD_TITLE;
        Range range = CommonUtil.toRange(matchedNode.lineRange());
        List<TextEdit> edits = CodeActionUtil.getTypeGuardCodeActionEdits(varName.get(), range, varTypeSymbol, context);
        if (edits.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(createQuickFixCodeAction(commandTitle, edits, context.fileUri()));
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

    protected Optional<Pair<UnionTypeSymbol, TypeDescriptorNode>> getVarTypeSymbolAndTypeNode(
            Node matchedNode, CodeActionContext context) {
        TypeSymbol varTypeSymbol;
        TypeDescriptorNode varTypeNode;
        switch (matchedNode.kind()) {
            case LOCAL_VAR_DECL:
                varTypeSymbol = context.positionDetails().matchedExprType();
                varTypeNode = ((VariableDeclarationNode) matchedNode).typedBindingPattern().typeDescriptor();
                break;
            case ASSIGNMENT_STATEMENT:
                Optional<VariableSymbol> optVariableSymbol = getVariableSymbol(context, matchedNode);
                if (optVariableSymbol.isEmpty()) {
                    return Optional.empty();
                }
                varTypeSymbol = optVariableSymbol.get().typeDescriptor();
                SyntaxTree syntaxTree = context.workspace().syntaxTree(context.filePath()).orElseThrow();
                NonTerminalNode node = CommonUtil.findNode(optVariableSymbol.get(), syntaxTree);
                if (node.kind() == SyntaxKind.TYPED_BINDING_PATTERN) {
                    varTypeNode = ((TypedBindingPatternNode) node).typeDescriptor();
                } else {
                    return Optional.empty();
                }
                break;
            default:
                return Optional.empty();
        }
        if (varTypeSymbol.typeKind() != TypeDescKind.UNION) {
            return Optional.empty();
        }
        return Optional.of(new ImmutablePair<>((UnionTypeSymbol) varTypeSymbol, varTypeNode));
    }

    private Optional<VariableSymbol> getVariableSymbol(CodeActionContext context, Node matchedNode) {
        AssignmentStatementNode assignmentStmtNode = (AssignmentStatementNode) matchedNode;
        SemanticModel semanticModel = context.workspace().semanticModel(context.filePath()).orElseThrow();
        Document srcFile = context.workspace().document(context.filePath()).orElseThrow();
        Optional<Symbol> symbol = semanticModel.symbol(srcFile,
                                                       assignmentStmtNode.varRef().lineRange().startLine());
        if (symbol.isEmpty() || symbol.get().kind() != SymbolKind.VARIABLE) {
            return Optional.empty();
        }
        return Optional.of((VariableSymbol) symbol.get());
    }
}
