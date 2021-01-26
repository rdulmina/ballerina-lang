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
package org.ballerinalang.langserver.codeaction;

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.syntax.tree.Node;
import io.ballerina.compiler.syntax.tree.NonTerminalNode;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import org.ballerinalang.langserver.LSClientLogger;
import org.ballerinalang.langserver.common.utils.CommonUtil;
import org.ballerinalang.langserver.commons.CodeActionContext;
import org.ballerinalang.langserver.commons.codeaction.CodeActionNodeType;
import org.ballerinalang.langserver.commons.codeaction.spi.PositionDetails;
import org.eclipse.lsp4j.CodeAction;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.ballerinalang.langserver.codeaction.CodeActionUtil.computePositionDetails;

/**
 * Represents the Code Action router.
 *
 * @since 1.1.1
 */
public class CodeActionRouter {

    /**
     * Returns a list of supported code actions.
     *
     * @param ctx {@link CodeActionContext}
     * @return list of code actions
     */
    public static List<CodeAction> getAvailableCodeActions(CodeActionContext ctx) {
        LSClientLogger clientLogger = LSClientLogger.getInstance(ctx.languageServercontext());
        List<CodeAction> codeActions = new ArrayList<>();
        CodeActionProvidersHolder codeActionProvidersHolder
                = CodeActionProvidersHolder.getInstance(ctx.languageServercontext());

        // Get available node-type based code-actions
        SyntaxTree syntaxTree = ctx.workspace().syntaxTree(ctx.filePath()).orElseThrow();
        Optional<NonTerminalNode> matchedNode = CodeActionUtil.getTopLevelNode(ctx.cursorPosition(), syntaxTree);
        CodeActionNodeType matchedNodeType = CodeActionUtil.codeActionNodeType(matchedNode.orElse(null));
        SemanticModel semanticModel = ctx.workspace().semanticModel(ctx.filePath()).orElseThrow();
        if (matchedNode.isPresent() && matchedNodeType != CodeActionNodeType.NONE) {
            Range range = CommonUtil.toRange(matchedNode.get().lineRange());
            Node expressionNode = CodeActionUtil.largestExpressionNode(matchedNode.get(), range);
            TypeSymbol matchedTypeSymbol = semanticModel.type(expressionNode.lineRange()).orElse(null);

            PositionDetails posDetails = CodeActionPositionDetails.from(matchedNode.get(), null, matchedTypeSymbol);
            ctx.setPositionDetails(posDetails);
            codeActionProvidersHolder.getActiveNodeBasedProviders(matchedNodeType, ctx).forEach(provider -> {
                try {
                    List<CodeAction> codeActionsOut = provider.getNodeBasedCodeActions(ctx);
                    if (codeActionsOut != null) {
                        codeActions.addAll(codeActionsOut);
                    }
                } catch (Exception e) {
                    String msg = "CodeAction '" + provider.getClass().getSimpleName() + "' failed!";
                    clientLogger.logError(msg, e, null, (Position) null);
                }
            });
        }

        // Get available diagnostics based code-actions
        ctx.allDiagnostics().stream().
                filter(diag -> CommonUtil.isWithinRange(ctx.cursorPosition(),
                                                        CommonUtil.toRange(diag.location().lineRange())))
                .forEach(diagnostic -> {
                    Range range = CommonUtil.toRange(diagnostic.location().lineRange());
                    PositionDetails positionDetails = computePositionDetails(range, syntaxTree, ctx);
                    ctx.setPositionDetails(positionDetails);
                    codeActionProvidersHolder.getActiveDiagnosticsBasedProviders(ctx).forEach(provider -> {
                        try {
                            List<CodeAction> codeActionsOut = provider.getDiagBasedCodeActions(diagnostic, ctx);
                            if (codeActionsOut != null) {
                                codeActions.addAll(codeActionsOut);
                            }
                        } catch (Exception e) {
                            String msg = "CodeAction '" + provider.getClass().getSimpleName() + "' failed!";
                            clientLogger.logError(msg, e, null, (Position) null);
                        }
                    });
                });
        return codeActions;
    }
}
