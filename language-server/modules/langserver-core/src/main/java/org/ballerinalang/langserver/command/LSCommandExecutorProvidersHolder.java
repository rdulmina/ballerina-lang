/*
 * Copyright (c) 2018, WSO2 Inc. (http://wso2.com) All Rights Reserved.
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
package org.ballerinalang.langserver.command;

import org.ballerinalang.langserver.commons.LanguageServerContext;
import org.ballerinalang.langserver.commons.command.spi.LSCommandExecutor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;

/**
 * Loads and provides the Command Executor Providers.
 *
 * @since 0.983.0
 */
public class LSCommandExecutorProvidersHolder {
    private static final Map<String, LSCommandExecutor> executors = new HashMap<>();
    private static final LanguageServerContext.Key<LSCommandExecutorProvidersHolder> CMD_EXEC_PROVIDERS_HOLDER_KEY =
            new LanguageServerContext.Key<>();

    public static LSCommandExecutorProvidersHolder getInstance(LanguageServerContext serverContext) {
        LSCommandExecutorProvidersHolder executorProvidersHolder = serverContext.get(CMD_EXEC_PROVIDERS_HOLDER_KEY);
        if (executorProvidersHolder == null) {
            executorProvidersHolder = new LSCommandExecutorProvidersHolder(serverContext);
        }

        return executorProvidersHolder;
    }

    private LSCommandExecutorProvidersHolder(LanguageServerContext serverContext) {
        serverContext.put(CMD_EXEC_PROVIDERS_HOLDER_KEY, this);
        loadServices();
    }

    private void loadServices() {
        ServiceLoader<LSCommandExecutor> loader = ServiceLoader.load(LSCommandExecutor.class);
        for (LSCommandExecutor executor : loader) {
            if (executor == null) {
                continue;
            }
            LSCommandExecutorProvidersHolder.executors.put(executor.getCommand(), executor);
        }
    }

    /**
     * Get the command executor registered for the given.
     *
     * @param command Command name
     * @return {@link Optional}     Mapped command executor
     */
    public Optional<LSCommandExecutor> getCommandExecutor(String command) {
        return Optional.ofNullable(LSCommandExecutorProvidersHolder.executors.get(command));
    }

    /**
     * Get the list of commands available.
     *
     * @return {@link List} Command List
     */
    public List<String> getCommandsList() {
        return new ArrayList<>(LSCommandExecutorProvidersHolder.executors.keySet());
    }
}
