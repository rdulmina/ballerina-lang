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
package org.ballerinalang.langserver.command.executors;

import org.apache.commons.io.IOUtils;
import org.ballerinalang.annotation.JavaSPIService;
import org.ballerinalang.langserver.common.constants.CommandConstants;
import org.ballerinalang.langserver.common.utils.CommonUtil;
import org.ballerinalang.langserver.commons.ExecuteCommandContext;
import org.ballerinalang.langserver.commons.command.CommandArgument;
import org.ballerinalang.langserver.commons.command.LSCommandExecutorException;
import org.ballerinalang.langserver.commons.command.spi.LSCommandExecutor;
import org.ballerinalang.langserver.diagnostic.DiagnosticsHelper;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.services.LanguageClient;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.ballerinalang.langserver.command.CommandUtil.notifyClient;

/**
 * Command executor for pulling a package from central.
 *
 * @since 0.983.0
 */
@JavaSPIService("org.ballerinalang.langserver.commons.command.spi.LSCommandExecutor")
public class PullModuleExecutor implements LSCommandExecutor {

    // A newCachedThreadPool with a limited max-threads
    private static final ExecutorService executor =
            new ThreadPoolExecutor(0, Runtime.getRuntime().availableProcessors(), 60L, TimeUnit.SECONDS,
                    new SynchronousQueue<>());

    public static final String COMMAND = "PULL_MODULE";

    /**
     * {@inheritDoc}
     *
     * @param context
     */
    @Override
    public Object execute(ExecuteCommandContext context) throws LSCommandExecutorException {
        executor.submit(() -> {
            // Derive module name and document uri
            String moduleName = "";
            String documentUri = "";
            for (CommandArgument arg : context.getArguments()) {
                switch (arg.key()) {
                    case CommandConstants.ARG_KEY_MODULE_NAME:
                        moduleName = arg.valueAs(String.class);
                        break;
                    case CommandConstants.ARG_KEY_DOC_URI:
                        documentUri = arg.valueAs(String.class);
                        break;
                }
            }
            // If no package, or no doc uri; then just skip
            if (moduleName.isEmpty() || documentUri.isEmpty()) {
                return;
            }
            // Execute `bal pull` command
            String ballerinaCmd = Paths.get(CommonUtil.BALLERINA_CMD).toString();
            ProcessBuilder processBuilder = new ProcessBuilder(ballerinaCmd, "pull", moduleName);
            LanguageClient client = context.getLanguageClient();
            DiagnosticsHelper diagnosticsHelper = DiagnosticsHelper.getInstance(context.languageServercontext());
            try {
                notifyClient(client, MessageType.Info, "Pulling '" + moduleName + "' from the Ballerina Central...");
                Process process = processBuilder.start();
                InputStream inputStream = process.getInputStream();
                // Consume and skip input-stream
                int data = inputStream.read();
                while (data != -1) {
                    data = inputStream.read();
                }
                // Check error stream for errors
                InputStream errorStream = process.getErrorStream();
                String error = IOUtils.toString(errorStream, StandardCharsets.UTF_8);

                if (error == null || error.isEmpty()) {
                    notifyClient(client, MessageType.Info, "Pulling success for the '" + moduleName + "' module!");
                    // TODO: fix
//                    if (client instanceof ExtendedLanguageClient) {
//                        clearDiagnostics((ExtendedLanguageClient) client, diagnosticsHelper, documentUri, context);
//                    }
                } else {
                    notifyClient(client, MessageType.Error,
                            "Pulling failed for the '" + moduleName + "' module!\n" + error);
                }
            } catch (IOException e) {
                notifyClient(client, MessageType.Error, "Pulling failed for the '" + moduleName + "' module!");
            }
        });

        return new Object();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getCommand() {
        return COMMAND;
    }
}
