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
package org.ballerinalang.langserver.completions.providers.context;

import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.syntax.tree.AnnotationNode;
import io.ballerina.compiler.syntax.tree.NonTerminalNode;
import io.ballerina.compiler.syntax.tree.ParameterizedTypeDescriptorNode;
import io.ballerina.compiler.syntax.tree.QualifiedNameReferenceNode;
import org.ballerinalang.annotation.JavaSPIService;
import org.ballerinalang.langserver.common.utils.completion.QNameReferenceUtil;
import org.ballerinalang.langserver.commons.BallerinaCompletionContext;
import org.ballerinalang.langserver.commons.completion.LSCompletionItem;
import org.ballerinalang.langserver.completions.providers.AbstractCompletionProvider;

import java.util.ArrayList;
import java.util.List;

/**
 * Completion provider for {@link AnnotationNode} context.
 *
 * @since 2.0.0
 */
@JavaSPIService("org.ballerinalang.langserver.commons.completion.spi.BallerinaCompletionProvider")
public class ParameterizedTypeDescriptorNodeContext
        extends AbstractCompletionProvider<ParameterizedTypeDescriptorNode> {

    public ParameterizedTypeDescriptorNodeContext() {
        super(ParameterizedTypeDescriptorNode.class);
    }

    @Override
    public List<LSCompletionItem> getCompletions(BallerinaCompletionContext ctx, ParameterizedTypeDescriptorNode node) {
        NonTerminalNode nodeAtCursor = ctx.getNodeAtCursor();
        if (this.onQualifiedNameIdentifier(ctx, nodeAtCursor)) {
            List<Symbol> typesInModule = QNameReferenceUtil.getTypesInModule(ctx,
                    (QualifiedNameReferenceNode) nodeAtCursor);
            return this.getCompletionItemList(typesInModule, ctx);
        }

        List<LSCompletionItem> completionItems = new ArrayList<>();
        completionItems.addAll(this.getModuleCompletionItems(ctx));
        completionItems.addAll(this.getTypeItems(ctx));

        return completionItems;
    }
}
