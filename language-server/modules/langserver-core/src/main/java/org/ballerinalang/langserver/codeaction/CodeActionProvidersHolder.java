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
package org.ballerinalang.langserver.codeaction;

import org.ballerinalang.langserver.commons.CodeActionContext;
import org.ballerinalang.langserver.commons.LanguageServerContext;
import org.ballerinalang.langserver.commons.codeaction.CodeActionNodeType;
import org.ballerinalang.langserver.commons.codeaction.spi.LSCodeActionProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

/**
 * Represents the Code Action provider factory.
 *
 * @since 1.1.1
 */
public class CodeActionProvidersHolder {
    private static final Map<CodeActionNodeType, List<LSCodeActionProvider>> nodeBasedProviders = new HashMap<>();
    private static final List<LSCodeActionProvider> diagnosticsBasedProviders = new ArrayList<>();
    private static final LanguageServerContext.Key<CodeActionProvidersHolder> CODE_ACTION_PROVIDERS_HOLDER_KEY =
            new LanguageServerContext.Key<>();

    /**
     * Returns the instance of Holder.
     *
     * @return code action provider holder instance
     */
    public static CodeActionProvidersHolder getInstance(LanguageServerContext serverContext) {
        CodeActionProvidersHolder codeActionProvidersHolder = serverContext.get(CODE_ACTION_PROVIDERS_HOLDER_KEY);
        if (codeActionProvidersHolder == null) {
            codeActionProvidersHolder = new CodeActionProvidersHolder(serverContext);
        }

        return codeActionProvidersHolder;
    }

    private CodeActionProvidersHolder(LanguageServerContext serverContext) {
        serverContext.put(CODE_ACTION_PROVIDERS_HOLDER_KEY, this);
        loadServices();
    }

    private void loadServices() {
        if (!CodeActionProvidersHolder.nodeBasedProviders.isEmpty()) {
            return;
        }
        ServiceLoader<LSCodeActionProvider> serviceLoader = ServiceLoader.load(LSCodeActionProvider.class);
        for (CodeActionNodeType nodeType : CodeActionNodeType.values()) {
            CodeActionProvidersHolder.nodeBasedProviders.put(nodeType, new ArrayList<>());
        }
        for (LSCodeActionProvider provider : serviceLoader) {
            if (provider == null) {
                continue;
            }
            if (provider.isNodeBasedSupported()) {
                for (CodeActionNodeType nodeType : provider.getCodeActionNodeTypes()) {
                    CodeActionProvidersHolder.nodeBasedProviders.get(nodeType).add(provider);
                }
            }
            if (provider.isDiagBasedSupported()) {
                CodeActionProvidersHolder.diagnosticsBasedProviders.add(provider);
            }
        }
    }

    /**
     * Returns active node based providers for this node type.
     *
     * @param nodeType node type
     * @return node based providers
     */
    List<LSCodeActionProvider> getActiveNodeBasedProviders(CodeActionNodeType nodeType, CodeActionContext ctx) {
        if (CodeActionProvidersHolder.nodeBasedProviders.containsKey(nodeType)) {
            return CodeActionProvidersHolder.nodeBasedProviders.get(nodeType).stream()
                    .filter(provider -> provider.isEnabled(ctx.languageServercontext()))
                    .sorted(Comparator.comparingInt(LSCodeActionProvider::priority))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * Returns active diagnostic based providers.
     *
     * @return diagnostic based providers
     */
    List<LSCodeActionProvider> getActiveDiagnosticsBasedProviders(CodeActionContext ctx) {
        return CodeActionProvidersHolder.diagnosticsBasedProviders.stream()
                .filter(provider -> provider.isEnabled(ctx.languageServercontext()))
                .sorted(Comparator.comparingInt(LSCodeActionProvider::priority))
                .collect(Collectors.toList());
    }
}
