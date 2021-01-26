/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.ballerinalang.langserver.common.utils.completion;

import io.ballerina.compiler.api.symbols.FunctionSymbol;
import io.ballerina.compiler.api.symbols.FunctionTypeSymbol;
import io.ballerina.compiler.api.symbols.MapTypeSymbol;
import io.ballerina.compiler.api.symbols.RecordFieldSymbol;
import io.ballerina.compiler.api.symbols.RecordTypeSymbol;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.api.symbols.SymbolKind;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.api.symbols.UnionTypeSymbol;
import io.ballerina.compiler.api.symbols.VariableSymbol;
import org.ballerinalang.langserver.commons.BallerinaCompletionContext;
import org.ballerinalang.langserver.commons.completion.LSCompletionItem;
import org.ballerinalang.langserver.completions.SymbolCompletionItem;
import org.ballerinalang.langserver.completions.builder.FunctionCompletionItemBuilder;
import org.ballerinalang.langserver.completions.builder.VariableCompletionItemBuilder;
import org.eclipse.lsp4j.CompletionItem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utility operations on the BLangRecordLiterals.
 */
public class BLangRecordLiteralUtil {

    private static final String ELLIPSIS = "...";

    private BLangRecordLiteralUtil() {
    }

    public static List<LSCompletionItem> getSpreadCompletionItems(BallerinaCompletionContext context,
                                                                  TypeSymbol evalType) {
        List<LSCompletionItem> completionItems = new ArrayList<>();
        List<TypeSymbol> typeList = getTypeListForMapAndRecords(evalType);
        List<Symbol> visibleSymbols = context.visibleSymbols(context.getCursorPosition());

        for (Symbol symbol : visibleSymbols) {
            getSpreadableCompletionItem(context, symbol, typeList).ifPresent(completionItems::add);
        }

        return completionItems;
    }

    private static Optional<LSCompletionItem> getSpreadableCompletionItem(BallerinaCompletionContext context,
                                                                          Symbol symbol, List<TypeSymbol> refTypeList) {
        Optional<TypeSymbol> typeDescriptor = Optional.empty();
        if (symbol.kind() == SymbolKind.FUNCTION) {
            FunctionTypeSymbol fTypeDesc = ((FunctionSymbol) symbol).typeDescriptor();
            typeDescriptor = fTypeDesc.returnTypeDescriptor();
        } else if (symbol.kind() == SymbolKind.VARIABLE) {
            typeDescriptor = Optional.of(((VariableSymbol) symbol).typeDescriptor());
        }

        if (typeDescriptor.isEmpty()) {
            return Optional.empty();
        }

        List<TypeSymbol> symbolTypeList = getTypeListForMapAndRecords(typeDescriptor.get());
        // if bType is not a map or record, then the symbol type list is empty 
        boolean canSpread = !symbolTypeList.isEmpty() && refTypeList.containsAll(symbolTypeList);

        CompletionItem cItem;
        if (canSpread && symbol.kind() == SymbolKind.FUNCTION) {
            cItem = FunctionCompletionItemBuilder.build((FunctionSymbol) symbol, context);
        } else if (canSpread) {
            cItem = VariableCompletionItemBuilder.build((VariableSymbol) symbol, symbol.name(),
                    typeDescriptor.get().signature());
        } else {
            return Optional.empty();
        }
        // Modify the spread completion item to automatically determine the prefixed number of dots to complete ellipsis
        // TODO: Fix
        modifySpreadCompletionItem(context, cItem);

        return Optional.of(new SymbolCompletionItem(context, symbol, cItem));
    }

    private static List<TypeSymbol> getTypeListForMapAndRecords(TypeSymbol typeDesc) {
        if (typeDesc.typeKind() == TypeDescKind.MAP) {
            Optional<TypeSymbol> memberType = ((MapTypeSymbol) typeDesc).typeParameter();
            if (memberType.isEmpty()) {
                return new ArrayList<>();
            }
            if (memberType.get().typeKind() == TypeDescKind.UNION) {
                return new ArrayList<>(((UnionTypeSymbol) memberType.get()).memberTypeDescriptors());
            }
            return Collections.singletonList(memberType.get());
        } else if (typeDesc.typeKind() == TypeDescKind.RECORD) {
            return ((RecordTypeSymbol) typeDesc).fieldDescriptors().values().stream()
                    .map(RecordFieldSymbol::typeDescriptor)
                    .collect(Collectors.toList());
        }

        return new ArrayList<>();
    }

    private static void modifySpreadCompletionItem(BallerinaCompletionContext context, CompletionItem cItem) {
        // TODO: Fix 
//        List<CommonToken> commonTokens = context.get(SourcePruneKeys.LHS_DEFAULT_TOKENS_KEY);
//        String lastToken = (commonTokens.isEmpty()) ? "" : commonTokens.get(commonTokens.size() - 1).getText();
//        long dotCount = lastToken.codePoints().filter(charVal -> charVal == '.').count();
        String prefix = String.join("", Collections.nCopies(ELLIPSIS.length(), "."));

        cItem.setInsertText(prefix + cItem.getInsertText());
        cItem.setLabel(ELLIPSIS + cItem.getLabel());
    }
}
