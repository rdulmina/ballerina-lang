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

import io.ballerina.compiler.syntax.tree.ClassDefinitionNode;
import io.ballerina.compiler.syntax.tree.Token;
import org.ballerinalang.annotation.JavaSPIService;
import org.ballerinalang.langserver.commons.BallerinaCompletionContext;
import org.ballerinalang.langserver.commons.completion.LSCompletionItem;
import org.ballerinalang.langserver.completions.SnippetCompletionItem;
import org.ballerinalang.langserver.completions.providers.AbstractCompletionProvider;
import org.ballerinalang.langserver.completions.providers.context.util.ClassDefinitionNodeContextUtil;
import org.ballerinalang.langserver.completions.util.Snippet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Completion provider for {@link ClassDefinitionNode} context.
 *
 * @since 2.0.0
 */
@JavaSPIService("org.ballerinalang.langserver.commons.completion.spi.BallerinaCompletionProvider")
public class ClassDefinitionNodeContext extends AbstractCompletionProvider<ClassDefinitionNode> {

    public ClassDefinitionNodeContext() {
        super(ClassDefinitionNode.class);
    }

    @Override
    public List<LSCompletionItem> getCompletions(BallerinaCompletionContext context, ClassDefinitionNode node) {
        if (this.withinBody(context, node)) {
            return this.getClassBodyCompletions(context, node);
        }

        if (onClassTypeQualifiers(context, node)) {
            return getClassTypeCompletions(context);
        }

        return Collections.emptyList();
    }

    @Override
    public boolean onPreValidation(BallerinaCompletionContext context, ClassDefinitionNode node) {
        return !node.classKeyword().isMissing();
    }

    private boolean onClassTypeQualifiers(BallerinaCompletionContext context, ClassDefinitionNode node) {
        int cursor = context.getCursorPositionInTree();
        Token classKeyword = node.classKeyword();

        return cursor < classKeyword.textRange().startOffset();
    }

    private List<LSCompletionItem> getClassTypeCompletions(BallerinaCompletionContext context) {
        ArrayList<LSCompletionItem> completionItems = new ArrayList<>();
        List<Snippet> snippets = Arrays.asList(
                Snippet.KW_DISTINCT, Snippet.KW_READONLY, Snippet.KW_ISOLATED, Snippet.KW_CLIENT, Snippet.KW_SERVICE
        );
        snippets.forEach(snippet -> completionItems.add(new SnippetCompletionItem(context, snippet.get())));

        return completionItems;
    }

    private boolean withinBody(BallerinaCompletionContext context, ClassDefinitionNode node) {
        int cursor = context.getCursorPositionInTree();
        Token openBrace = node.openBrace();
        Token closeBrace = node.closeBrace();

        if (openBrace.isMissing() || closeBrace.isMissing()) {
            return false;
        }

        return cursor >= openBrace.textRange().endOffset() && cursor <= closeBrace.textRange().startOffset();
    }

    private List<LSCompletionItem> getClassBodyCompletions(BallerinaCompletionContext context,
                                                           ClassDefinitionNode node) {
        List<LSCompletionItem> completionItems = new ArrayList<>();

        completionItems.add(new SnippetCompletionItem(context, Snippet.KW_PRIVATE.get()));
        completionItems.add(new SnippetCompletionItem(context, Snippet.KW_PUBLIC.get()));
        completionItems.add(new SnippetCompletionItem(context, Snippet.KW_FINAL.get()));
        completionItems.add(new SnippetCompletionItem(context, Snippet.KW_REMOTE.get()));
        completionItems.add(new SnippetCompletionItem(context, Snippet.DEF_REMOTE_FUNCTION.get()));
        completionItems.add(new SnippetCompletionItem(context, Snippet.DEF_FUNCTION.get()));
        completionItems.add(new SnippetCompletionItem(context, Snippet.KW_FUNCTION.get()));
        completionItems.add(new SnippetCompletionItem(context, Snippet.KW_ISOLATED.get()));
        completionItems.add(new SnippetCompletionItem(context, Snippet.KW_TRANSACTIONAL.get()));
        if (ClassDefinitionNodeContextUtil.onSuggestResourceSnippet(node)) {
            completionItems.add(new SnippetCompletionItem(context, Snippet.DEF_RESOURCE_FUNCTION_SIGNATURE.get()));
        }
        completionItems.addAll(this.getTypeItems(context));
        completionItems.addAll(this.getModuleCompletionItems(context));

        return completionItems;
    }
}
