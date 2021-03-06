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
package org.ballerinalang.langserver.extensions.ballerina.document;

import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.jsonrpc.services.JsonRequest;
import org.eclipse.lsp4j.jsonrpc.services.JsonSegment;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * An extension interface for Language server to add features related to ballerina files.
 *
 * @since 0.981.2
 */
@JsonSegment("ballerinaDocument")
public interface BallerinaDocumentService {

    @JsonRequest
    CompletableFuture<BallerinaSyntaxTreeResponse> syntaxTree(BallerinaSyntaxTreeRequest request);

    @JsonRequest
    CompletableFuture<BallerinaSyntaxTreeResponse> syntaxTreeByRange(BallerinaSyntaxTreeByRangeRequest request);

    @JsonRequest
    CompletableFuture<BallerinaSyntaxTreeResponse> syntaxTreeModify(BallerinaSyntaxTreeModifyRequest request);

    @JsonRequest
    CompletableFuture<BallerinaSyntaxTreeResponse> syntaxTreeLocate(BallerinaSyntaxTreeByRangeRequest request);

    @JsonRequest
    CompletableFuture<BallerinaSyntaxTreeResponse> triggerModify(BallerinaTriggerModifyRequest request);

    @JsonRequest
    CompletableFuture<BallerinaProject> project(BallerinaProjectParams params);

    @JsonRequest
    CompletableFuture<List<PublishDiagnosticsParams>> diagnostics(BallerinaProjectParams params);
}
