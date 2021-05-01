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
package org.ballerinalang.langserver.common.utils;

import io.ballerina.compiler.api.ModuleID;
import io.ballerina.compiler.api.symbols.ArrayTypeSymbol;
import io.ballerina.compiler.api.symbols.ClassSymbol;
import io.ballerina.compiler.api.symbols.IntersectionTypeSymbol;
import io.ballerina.compiler.api.symbols.ModuleSymbol;
import io.ballerina.compiler.api.symbols.ObjectTypeSymbol;
import io.ballerina.compiler.api.symbols.ParameterSymbol;
import io.ballerina.compiler.api.symbols.RecordFieldSymbol;
import io.ballerina.compiler.api.symbols.RecordTypeSymbol;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.api.symbols.SymbolKind;
import io.ballerina.compiler.api.symbols.TupleTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeDefinitionSymbol;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeReferenceTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.api.symbols.UnionTypeSymbol;
import io.ballerina.compiler.syntax.tree.IdentifierToken;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.ImportPrefixNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.NonTerminalNode;
import io.ballerina.compiler.syntax.tree.SeparatedNodeList;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.projects.Module;
import io.ballerina.projects.Package;
import io.ballerina.projects.Project;
import io.ballerina.tools.diagnostics.Location;
import io.ballerina.tools.text.LinePosition;
import io.ballerina.tools.text.LineRange;
import io.ballerina.tools.text.TextDocument;
import io.ballerina.tools.text.TextRange;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.ballerinalang.langserver.codeaction.CodeActionModuleId;
import org.ballerinalang.langserver.common.ImportsAcceptor;
import org.ballerinalang.langserver.common.constants.PatternConstants;
import org.ballerinalang.langserver.commons.BallerinaCompletionContext;
import org.ballerinalang.langserver.commons.CompletionContext;
import org.ballerinalang.langserver.commons.DocumentServiceContext;
import org.ballerinalang.langserver.commons.PositionedOperationContext;
import org.ballerinalang.langserver.commons.completion.LSCompletionItem;
import org.ballerinalang.langserver.completions.RecordFieldCompletionItem;
import org.ballerinalang.langserver.completions.StaticCompletionItem;
import org.ballerinalang.langserver.completions.util.ItemResolverConstants;
import org.ballerinalang.langserver.completions.util.Priority;
import org.ballerinalang.model.elements.Flag;
import org.ballerinalang.model.tree.TopLevelNode;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BSymbol;
import org.wso2.ballerinalang.compiler.tree.BLangNode;
import org.wso2.ballerinalang.compiler.tree.BLangTypeDefinition;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangInvocation;
import org.wso2.ballerinalang.compiler.util.Names;

import java.io.File;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import static io.ballerina.compiler.api.symbols.SymbolKind.MODULE;
import static org.ballerinalang.langserver.common.utils.CommonKeys.PKG_DELIMITER_KEYWORD;
import static org.ballerinalang.langserver.common.utils.CommonKeys.SEMI_COLON_SYMBOL_KEY;
import static org.ballerinalang.langserver.common.utils.CommonKeys.SLASH_KEYWORD_KEY;

/**
 * Common utils to be reuse in language server implementation.
 */
public class CommonUtil {
    private static final Path TEMP_DIR = Paths.get(System.getProperty("java.io.tmpdir"));

    public static final String MD_LINE_SEPARATOR = "  " + System.lineSeparator();

    public static final String LINE_SEPARATOR = System.lineSeparator();

    public static final String FILE_SEPARATOR = File.separator;

    public static final Pattern MD_NEW_LINE_PATTERN = Pattern.compile("\\s\\s\\r\\n?|\\s\\s\\n|\\r\\n?|\\n");

    public static final String BALLERINA_HOME;

    public static final String BALLERINA_CMD;

    public static final String MARKDOWN_MARKUP_KIND = "markdown";

    public static final String BALLERINA_ORG_NAME = "ballerina";

    public static final String SDK_VERSION = System.getProperty("ballerina.version");

    public static final Path LS_STDLIB_CACHE_DIR = TEMP_DIR.resolve("ls_stdlib_cache").resolve(SDK_VERSION);

    public static final List<String> PRE_DECLARED_LANG_LIBS = Arrays.asList("lang.boolean", "lang.decimal",
            "lang.error", "lang.float", "lang.future", "lang.int", "lang.map", "lang.object", "lang.stream",
            "lang.string", "lang.table", "lang.transaction", "lang.typedesc", "lang.xml");

    public static final List<String> BALLERINA_KEYWORDS;

    static {
        BALLERINA_HOME = System.getProperty("ballerina.home");
        BALLERINA_CMD = BALLERINA_HOME + File.separator + "bin" + File.separator + "bal" +
                (SystemUtils.IS_OS_WINDOWS ? ".bat" : "");
        BALLERINA_KEYWORDS = getBallerinaKeywords();
    }

    private CommonUtil() {
    }

    /**
     * Convert the syntax-node line range into a lsp4j range.
     *
     * @param lineRange - line range
     * @return {@link Range} converted range
     */
    public static Range toRange(LineRange lineRange) {
        return new Range(toPosition(lineRange.startLine()), toPosition(lineRange.endLine()));
    }

    /**
     * Converts syntax-node line position into a lsp4j position.
     *
     * @param linePosition - line position
     * @return {@link Position} converted position
     */
    public static Position toPosition(LinePosition linePosition) {
        return new Position(linePosition.line(), linePosition.offset());
    }

    /**
     * Get the text edit for an auto import statement.
     * Here we do not check whether the package is not already imported or a predeclared lang-lib, Particular
     * check should be done before usage
     *
     * @param orgName package org name
     * @param pkgName package name
     * @param context Language server context
     * @return {@link List}     List of Text Edits to apply
     */
    public static List<TextEdit> getAutoImportTextEdits(@Nonnull String orgName, String pkgName,
                                                        DocumentServiceContext context) {
        List<ImportDeclarationNode> currentDocImports = context.currentDocImports();
        Position start = new Position(0, 0);
        if (currentDocImports != null && !currentDocImports.isEmpty()) {
            ImportDeclarationNode last = CommonUtil.getLastItem(currentDocImports);
            int endLine = last.lineRange().endLine().line();
            start = new Position(endLine, 0);
        }
        String importStatement = ItemResolverConstants.IMPORT + " "
                + (!orgName.isEmpty() ? orgName + SLASH_KEYWORD_KEY : orgName)
                + pkgName + SEMI_COLON_SYMBOL_KEY
                + CommonUtil.LINE_SEPARATOR;

        return Collections.singletonList(new TextEdit(new Range(start, start), importStatement));
    }

    /**
     * Get the text edit for an auto import statement.
     * Here we do not check whether the package is not already imported or a predeclared lang-lib, Particular
     * check should be done before usage
     *
     * @param orgName package org name
     * @param pkgName package name
     * @param alias   import alias
     * @param context Language server context
     * @return {@link List}     List of Text Edits to apply
     */
    public static List<TextEdit> getAutoImportTextEdits(@Nonnull String orgName, String pkgName, String alias,
                                                        DocumentServiceContext context) {
        List<ImportDeclarationNode> currentDocImports = context.currentDocImports();
        Position start = new Position(0, 0);
        if (currentDocImports != null && !currentDocImports.isEmpty()) {
            ImportDeclarationNode last = CommonUtil.getLastItem(currentDocImports);
            int endLine = last.lineRange().endLine().line();
            start = new Position(endLine, 0);
        }
        StringBuilder builder = new StringBuilder(ItemResolverConstants.IMPORT + " "
                + (!orgName.isEmpty() ? orgName + SLASH_KEYWORD_KEY : orgName)
                + pkgName);
        if (!alias.isEmpty()) {
            builder.append(" as ").append(alias).append(" ");
        }
        builder.append(SEMI_COLON_SYMBOL_KEY).append(CommonUtil.LINE_SEPARATOR);

        return Collections.singletonList(new TextEdit(new Range(start, start), builder.toString()));
    }

    /**
     * Get the default value for the given BType.
     *
     * @param bType Type descriptor to get the default value
     * @return {@link String}   Default value as a String
     */
    public static String getDefaultValueForType(TypeSymbol bType) {
        String typeString;
        if (bType == null) {
            return "()";
        }

        TypeSymbol rawType = getRawType(bType);
        TypeDescKind typeKind = rawType.typeKind();
        switch (typeKind) {
            case FLOAT:
                typeString = Float.toString(0);
                break;
            case BOOLEAN:
                typeString = Boolean.toString(false);
                break;
            case TUPLE:
                TupleTypeSymbol tupleType = (TupleTypeSymbol) bType;
                String memberTypes = tupleType.memberTypeDescriptors().stream()
                        .map(CommonUtil::getDefaultValueForType)
                        .collect(Collectors.joining(", "));
                typeString = "[" + memberTypes + "]";
                break;
            case ARRAY:
                // Filler value of an array is []
                ArrayTypeSymbol arrayType = (ArrayTypeSymbol) bType;
                if (arrayType.memberTypeDescriptor().typeKind() == TypeDescKind.ARRAY) {
                    typeString = "[" + getDefaultValueForType(arrayType.memberTypeDescriptor()) + "]";
                } else {
                    typeString = "[]";
                }
                break;
            case RECORD:
                // TODO: Here we have disregarded the formatting of the record fields. Need to consider that in future
                RecordTypeSymbol recordTypeSymbol = (RecordTypeSymbol) rawType;
                typeString = "{";
                typeString += getMandatoryRecordFields(recordTypeSymbol).stream()
                        .filter(recordFieldSymbol -> recordFieldSymbol.getName().isPresent())
                        .map(recordFieldSymbol -> recordFieldSymbol.getName().get() + ": " +
                                getDefaultValueForType(recordFieldSymbol.typeDescriptor()))
                        .collect(Collectors.joining(", "));
                typeString += "}";
                break;
            case MAP:
                typeString = "{}";
                break;
            case OBJECT:
                ObjectTypeSymbol objectTypeSymbol = (ObjectTypeSymbol) rawType;
                if (objectTypeSymbol.kind() == SymbolKind.CLASS) {
                    ClassSymbol classSymbol = (ClassSymbol) objectTypeSymbol;
                    if (classSymbol.initMethod().isPresent()) {
                        List<ParameterSymbol> params = classSymbol.initMethod().get().typeDescriptor().params().get();
                        String text = params.stream()
                                .map(param -> getDefaultValueForType(param.typeDescriptor()))
                                .collect(Collectors.joining(", "));
                        typeString = "new (" + text + ")";
                    } else {
                        typeString = "new ()";
                    }
                } else {
                    typeString = "object {}";
                }
                break;
            // Fixme
//            case FINITE:
//                List<BLangExpression> valueSpace = new ArrayList<>(((BFiniteType) bType).getValueSpace());
//                String value = valueSpace.get(0).toString();
//                BType type = valueSpace.get(0).type;
//                typeString = value;
//                if (type.toString().equals("string")) {
//                    typeString = "\"" + typeString + "\"";
//                }
//                break;
            case UNION:
                List<TypeSymbol> members =
                        new ArrayList<>(((UnionTypeSymbol) bType).memberTypeDescriptors());
                typeString = getDefaultValueForType(members.get(0));
                break;
            case STREAM:
//            case TABLE:
            default:
                if (typeKind.isIntegerType()) {
                    typeString = Integer.toString(0);
                    break;
                }

                if (typeKind.isStringType()) {
                    typeString = "\"\"";
                    break;
                }

                typeString = "()";
                break;
        }
        return typeString;
    }

    /**
     * Get completion items list for struct fields.
     *
     * @param context Language server operation context
     * @param fields  Map of field descriptors
     * @return {@link List}     List of completion items for the struct fields
     */
    public static List<LSCompletionItem> getRecordFieldCompletionItems(BallerinaCompletionContext context,
                                                                       Map<String, RecordFieldSymbol> fields) {
        List<LSCompletionItem> completionItems = new ArrayList<>();
        AtomicInteger fieldCounter = new AtomicInteger();
        fields.forEach((name, field) -> {
            fieldCounter.getAndIncrement();
            String insertText =
                    getRecordFieldCompletionInsertText(field, Collections.emptyList(), 0, fieldCounter.get());
            CompletionItem fieldItem = new CompletionItem();
            fieldItem.setInsertText(insertText);
            fieldItem.setInsertTextFormat(InsertTextFormat.Snippet);
            fieldItem.setLabel(name);
            fieldItem.setDetail(ItemResolverConstants.FIELD_TYPE);
            fieldItem.setKind(CompletionItemKind.Field);
            fieldItem.setSortText(Priority.PRIORITY120.toString());
            completionItems.add(new RecordFieldCompletionItem(context, field, fieldItem));
        });

        return completionItems;
    }

    /**
     * Get the completion item to fill all the struct fields.
     *
     * @param context Language Server Operation Context
     * @param fields  Map of fields
     * @return {@link LSCompletionItem}   Completion Item to fill all the options
     */
    public static LSCompletionItem getFillAllStructFieldsItem(BallerinaCompletionContext context,
                                                              Map<String, RecordFieldSymbol> fields) {
        List<String> fieldEntries = new ArrayList<>();

        for (Map.Entry<String, RecordFieldSymbol> fieldSymbolEntry : fields.entrySet()) {
            String defaultFieldEntry = fieldSymbolEntry.getKey()
                    + PKG_DELIMITER_KEYWORD + " "
                    + getDefaultValueForType(fieldSymbolEntry.getValue().typeDescriptor());
            fieldEntries.add(defaultFieldEntry);
        }

        String insertText = String.join(("," + LINE_SEPARATOR), fieldEntries);
        String label = "Add All Attributes";

        CompletionItem completionItem = new CompletionItem();
        completionItem.setLabel(label);
        completionItem.setInsertText(insertText);
        completionItem.setDetail(ItemResolverConstants.NONE);
        completionItem.setKind(CompletionItemKind.Property);
        completionItem.setSortText(Priority.PRIORITY110.toString());

        return new StaticCompletionItem(context, completionItem, StaticCompletionItem.Kind.OTHER);
    }

    /**
     * Get the completion Item for the error type.
     *
     * @param context LS Operation context
     * @return {@link LSCompletionItem} generated for error type
     */
    @Deprecated
    public static LSCompletionItem getErrorTypeCompletionItem(BallerinaCompletionContext context) {
        CompletionItem errorTypeCItem = new CompletionItem();
        errorTypeCItem.setInsertText(ItemResolverConstants.ERROR);
        errorTypeCItem.setLabel(ItemResolverConstants.ERROR);
        errorTypeCItem.setDetail(ItemResolverConstants.ERROR);
        errorTypeCItem.setInsertTextFormat(InsertTextFormat.Snippet);
        errorTypeCItem.setKind(CompletionItemKind.Event);

        return new StaticCompletionItem(context, errorTypeCItem, StaticCompletionItem.Kind.TYPE);
    }

    /**
     * Filter a type in the module by the name.
     *
     * @param context  language server operation context
     * @param alias    module alias
     * @param typeName type name to be filtered against
     * @return {@link Optional} type found
     */
    public static Optional<TypeSymbol> getTypeFromModule(BallerinaCompletionContext context, String alias,
                                                         String typeName) {
        Optional<ModuleSymbol> module = CommonUtil.searchModuleForAlias(context, alias);
        if (module.isEmpty()) {
            return Optional.empty();
        }

        ModuleSymbol moduleSymbol = module.get();
        for (TypeDefinitionSymbol typeDefinitionSymbol : moduleSymbol.typeDefinitions()) {
            if (typeDefinitionSymbol.getName().get().equals(typeName)) {
                return Optional.of(typeDefinitionSymbol.typeDescriptor());
            }
        }

        for (ClassSymbol clazz : moduleSymbol.classes()) {
            if (clazz.getName().get().equals(typeName)) {
                return Optional.of(clazz);
            }
        }

        return Optional.empty();
    }

    /**
     * Get the module symbol associated with the given alias.
     *
     * @param context Language server operation context
     * @param alias   alias value
     * @return {@link Optional} scope entry for the module symbol
     */
    public static Optional<ModuleSymbol> searchModuleForAlias(PositionedOperationContext context, String alias) {
        List<Symbol> visibleSymbols = context.visibleSymbols(context.getCursorPosition());
        for (Symbol symbol : visibleSymbols) {
            if (symbol.kind() == MODULE && Objects.equals(symbol.getName().orElse(null), alias)) {
                return Optional.of((ModuleSymbol) symbol);
            }
        }

        return Optional.empty();
    }

    /**
     * Get the last item of the List.
     *
     * @param list List to get the Last Item
     * @param <T>  List content Type
     * @return Extracted last Item
     */
    public static <T> T getLastItem(List<T> list) {
        return (list.size() == 0) ? null : list.get(list.size() - 1);
    }

    /**
     * Get the last item of the Array.
     *
     * @param list Array to get the Last Item
     * @param <T>  Array content Type
     * @return Extracted last Item
     */
    public static <T> T getLastItem(T[] list) {
        return (list.length == 0) ? null : list[list.length - 1];
    }

    /**
     * Check whether the source is a test source.
     *
     * @param relativeFilePath source path relative to the package
     * @return {@link Boolean}  Whether a test source or not
     */
    public static boolean isTestSource(String relativeFilePath) {
        return relativeFilePath.startsWith("tests" + FILE_SEPARATOR);
    }

    /**
     * Get the package name components combined.
     *
     * @param importNode {@link ImportDeclarationNode}
     * @return {@link String}   Combined package name
     */
    public static String getPackageNameComponentsCombined(ImportDeclarationNode importNode) {
        return importNode.moduleName().stream()
                .map(Token::text)
                .collect(Collectors.joining("."));
    }

    public static boolean symbolContainsInvalidChars(BSymbol bSymbol) {
        List<String> symbolNameComponents = Arrays.asList(bSymbol.getName().getValue().split("\\."));
        String symbolName = CommonUtil.getLastItem(symbolNameComponents);

        return symbolName != null && (symbolName.contains(CommonKeys.LT_SYMBOL_KEY)
                || symbolName.contains(CommonKeys.GT_SYMBOL_KEY)
                || symbolName.contains(CommonKeys.DOLLAR_SYMBOL_KEY)
                || symbolName.equals("main")
                || symbolName.endsWith(".new")
                || symbolName.startsWith("0"));
    }

    /**
     * Extract the required fields from the records.
     *
     * @param recordType record type descriptor to evaluate
     * @return {@link List} of required fields captured
     */
    public static List<RecordFieldSymbol> getMandatoryRecordFields(RecordTypeSymbol recordType) {
        return recordType.fieldDescriptors().values().stream()
                .filter(field -> !field.hasDefaultValue() && !field.isOptional())
                .collect(Collectors.toList());
    }

    /**
     * Get the completion item insert text for a BField.
     *
     * @param bField  BField to evaluate
     * @param parents Parent record field symbols
     * @return {@link String} Insert text
     */
    public static String getRecordFieldCompletionInsertText(RecordFieldSymbol bField,
                                                            List<RecordFieldSymbol> parents,
                                                            int tabOffset, int fieldId) {
        TypeSymbol fieldType = CommonUtil.getRawType(bField.typeDescriptor());
        StringBuilder insertText = new StringBuilder(bField.getName().get() + ": ");
        if (fieldType.typeKind() == TypeDescKind.RECORD) {
            List<RecordFieldSymbol> requiredFields = getMandatoryRecordFields((RecordTypeSymbol) fieldType);
            if (requiredFields.isEmpty()) {
                insertText.append("{").append("${").append(fieldId).append("}}");
                return insertText.toString();
            }
            insertText.append("{").append(LINE_SEPARATOR);
            List<String> requiredFieldInsertTexts = new ArrayList<>();

            for (int i = 0; i < requiredFields.size(); i++) {
                // If the field refers to the same type as bField or a parent of bField, 
                // it results in a stack overflow error. Avoiding that using the following check
                RecordFieldSymbol field = requiredFields.get(i);
                if (!parents.contains(field)) {
                    List<RecordFieldSymbol> newParentsList = new ArrayList<>(parents);
                    newParentsList.add(field);
                    String fieldText = String.join("", Collections.nCopies(tabOffset + 1, "\t")) +
                            getRecordFieldCompletionInsertText(field, newParentsList, tabOffset + 1, i + 1);
                    requiredFieldInsertTexts.add(fieldText);
                }
            }
            insertText.append(String.join("," + CommonUtil.LINE_SEPARATOR, requiredFieldInsertTexts));
            insertText.append(LINE_SEPARATOR)
                    .append(String.join("", Collections.nCopies(tabOffset, "\t")))
                    .append("}");
        } else if (fieldType.typeKind() == TypeDescKind.ARRAY) {
            insertText.append("[").append("${").append(fieldId).append("}").append("]");
        } else if (fieldType.typeKind().isStringType()) {
            insertText.append("\"").append("${").append(fieldId).append("}").append("\"");
        } else {
            insertText.append("${").append(fieldId).append(":")
                    .append(getDefaultValueForType(bField.typeDescriptor())).append("}");
        }

        return insertText.toString();
    }

    /**
     * Predicate to check for the invalid type definitions.
     *
     * @return {@link Predicate}    Predicate for the check
     */
    public static Predicate<TopLevelNode> checkInvalidTypesDefs() {
        return topLevelNode -> {
            if (topLevelNode instanceof BLangTypeDefinition) {
                BLangTypeDefinition typeDefinition = (BLangTypeDefinition) topLevelNode;
                return !(typeDefinition.flagSet.contains(Flag.SERVICE) ||
                        typeDefinition.flagSet.contains(Flag.RESOURCE));
            }
            return true;
        };
    }

    /**
     * Generates a random name.
     *
     * @param value    index of the argument
     * @param argNames argument set
     * @return random argument name
     */
    public static String generateName(int value, Set<String> argNames) {
        StringBuilder result = new StringBuilder();
        int index = value;
        while (--index >= 0) {
            result.insert(0, (char) ('a' + index % 26));
            index /= 26;
        }
        while (argNames.contains(result.toString())) {
            result = new StringBuilder(generateName(++value, argNames));
        }
        return result.toString();
    }

    /**
     * Generates a variable name.
     *
     * @param bLangNode {@link BLangNode}
     * @return random argument name
     */
    public static String generateVariableName(BLangNode bLangNode, Set<String> names) {
        String newName = generateName(1, names);
        if (bLangNode instanceof BLangInvocation) {
            return generateVariableName(1, ((BLangInvocation) bLangNode).name.value, names);
        }
        return newName;
    }

    /**
     * Generates a variable name.
     *
     * @param name {@link BLangNode}
     * @return random argument name
     */
    public static String generateVariableName(String name, Set<String> names) {
        return generateVariableName(1, name, names);
    }

    /**
     * Generates a variable name.
     *
     * @param symbol {@link Symbol}
     * @return random argument name
     */
    public static String generateVariableName(Symbol symbol, Set<String> names) {
        return generateVariableName(1, symbol.kind().name(), names);
    }

    /**
     * Generates a variable name.
     *
     * @param symbol {@link Symbol}
     * @return random argument name
     */
    public static String generateVariableName(Symbol symbol, TypeSymbol typeSymbol, Set<String> names) {
        // In some scenarios the compiler sends the symbol name as empty string. Hence add the check
        if (symbol != null && symbol.getName().isPresent() && !symbol.getName().get().isEmpty()) {
            // Start naming with symbol-name
            return generateVariableName(1, symbol.getName().get(), names);
        } else if (typeSymbol != null) {
            // If symbol is null, try typeSymbol
            String name;
            if (typeSymbol.typeKind() == TypeDescKind.TYPE_REFERENCE && typeSymbol.getName().isPresent()
                    && !typeSymbol.getName().get().startsWith("$")) {
                name = typeSymbol.getName().get();
            } else {
                TypeSymbol rawType = CommonUtil.getRawType(typeSymbol);
                switch (rawType.typeKind()) {
                    case RECORD:
                        name = "mappingResult";
                        break;
                    case TUPLE:
                    case ARRAY:
                        name = "listResult";
                        break;
                    default:
                        name = rawType.typeKind().getName() + "Result";
                        break;
                }
            }
            return generateVariableName(1, name, names);
        } else {
            return generateName(1, names);
        }
    }

    /**
     * Whether the given module is a langlib module.
     *
     * @param moduleID Module ID to evaluate
     * @return {@link Boolean} whether langlib or not
     */
    public static boolean isLangLib(ModuleID moduleID) {
        return isLangLib(moduleID.orgName(), moduleID.moduleName());
    }

    public static boolean isLangLib(String orgName, String moduleName) {
        return orgName.equals("ballerina") && moduleName.startsWith("lang.");
    }

    private static String generateVariableName(int suffix, String name, Set<String> names) {
        names.addAll(BALLERINA_KEYWORDS);
        String newName = name.replaceAll(".+[\\:\\.]", "");
        if (suffix == 1 && !name.isEmpty()) {
            BiFunction<String, String, String> replacer = (search, text) ->
                    (text.startsWith(search)) ? text.replaceFirst(search, "") : text;
            // Replace common prefixes
            newName = replacer.apply("get", newName);
            newName = replacer.apply("put", newName);
            newName = replacer.apply("delete", newName);
            newName = replacer.apply("update", newName);
            newName = replacer.apply("set", newName);
            newName = replacer.apply("add", newName);
            newName = replacer.apply("create", newName);
            newName = replacer.apply("to", newName);
            // Remove '_' underscores
            while (newName.contains("_")) {
                String[] parts = newName.split("_");
                List<String> restParts = Arrays.stream(parts, 1, parts.length).collect(Collectors.toList());
                newName = parts[0] + StringUtils.capitalize(String.join("", restParts));
            }
            // If empty, revert back to original name
            if (newName.isEmpty()) {
                newName = name;
            }
            // Lower first letter
            newName = newName.substring(0, 1).toLowerCase(Locale.getDefault()) + newName.substring(1);
            // if already available, try appending 'Result', 'Out', 'Value'
            boolean alreadyExists = false;
            String[] specialSuffixes = new String[]{"Result", "Out", "Value"};
            boolean[] flagSpecialSuffixes = new boolean[specialSuffixes.length];
            boolean addNoSpecialSuffix = false;
            // If any of special suffix already found in new-name, don't use any special suffix
            for (String currentSuffix : specialSuffixes) {
                if (newName.endsWith(currentSuffix)) {
                    addNoSpecialSuffix = true;
                    break;
                }
            }
            for (String nextName : names) {
                if (nextName.equals(newName)) {
                    // If new-name already exists
                    alreadyExists = true;
                } else if (!addNoSpecialSuffix) {
                    // Check a particular special suffix and new-name combination already exists
                    for (int i = 0; i < specialSuffixes.length; i++) {
                        String currentSuffix = specialSuffixes[i];
                        if (nextName.equals(newName + currentSuffix)) {
                            flagSpecialSuffixes[i] = true;
                        }
                    }
                }
            }
            // if already available, try appending 'Result' or 'Out'
            if (alreadyExists) {
                if (!addNoSpecialSuffix) {
                    for (int i = 0; i < flagSpecialSuffixes.length; i++) {
                        if (!flagSpecialSuffixes[i]) {
                            newName = newName + specialSuffixes[i];
                            break;
                        }
                    }
                } else {
                    return generateVariableName(++suffix, newName, names);
                }
            }
        } else {
            newName = newName + suffix;
        }
        // if still already available, try a random letter
        while (names.contains(newName)) {
            newName = generateName(++suffix, names);
        }
        return newName;
    }

    /**
     * Checks if the provided identifier is valid as per the ballerina specification.
     *
     * @param identifier Identifier to be checked for validity
     * @return True, if the identifier is valid as per the ballerina specification
     */
    public static boolean isValidIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }

        return identifier.matches(PatternConstants.IDENTIFIER_PATTERN);
    }

    /**
     * Returns module prefix and process imports required.
     *
     * @param importsAcceptor import acceptor
     * @param currentModuleId current module id
     * @param moduleID        module id
     * @param context         {@link DocumentServiceContext}
     * @return module prefix
     */
    public static String getModulePrefix(ImportsAcceptor importsAcceptor, ModuleID currentModuleId,
                                         ModuleID moduleID, DocumentServiceContext context) {
        String pkgPrefix = "";
        if (!moduleID.equals(currentModuleId)) {
            boolean preDeclaredLangLib = moduleID.orgName().equals(BALLERINA_ORG_NAME) &&
                    PRE_DECLARED_LANG_LIBS.contains(moduleID.moduleName());
            String moduleName = escapeModuleName(moduleID.orgName() + "/" + moduleID.moduleName());
            String[] moduleParts = moduleName.split("/");
            String orgName = moduleParts[0];
            String alias = moduleParts[1];

            pkgPrefix = alias.replaceAll(".*\\.", "") + ":";
            pkgPrefix = (!preDeclaredLangLib && BALLERINA_KEYWORDS.contains(pkgPrefix)) ? "'" + pkgPrefix : pkgPrefix;

            // See if an alias (ex: import project.module1 as mod1) is used
            List<ImportDeclarationNode> existingModuleImports = context.currentDocImports().stream()
                    .filter(importDeclarationNode ->
                            CodeActionModuleId.from(importDeclarationNode).moduleName().equals(moduleID.moduleName()))
                    .collect(Collectors.toList());

            if (existingModuleImports.size() == 1) {
                ImportDeclarationNode importDeclarationNode = existingModuleImports.get(0);
                if (importDeclarationNode.prefix().isPresent()) {
                    pkgPrefix = importDeclarationNode.prefix().get().prefix().text() + ":";
                }
            }

            if (importsAcceptor != null && !preDeclaredLangLib) {
                importsAcceptor.getAcceptor().accept(orgName, alias);
            }
        }
        return pkgPrefix;
    }

    public static String escapeModuleName(String qualifiedModuleName) {
        String[] moduleNameParts = qualifiedModuleName.split("/");
        if (moduleNameParts.length > 1) {
            String orgName = moduleNameParts[0];
            String alias = moduleNameParts[1];
            String[] aliasParts = moduleNameParts[1].split("\\.");
            boolean preDeclaredLangLib = BALLERINA_ORG_NAME.equals(orgName) && PRE_DECLARED_LANG_LIBS.contains(alias);
            if (aliasParts.length > 1) {
                String aliasLastPart = aliasParts[aliasParts.length - 1];
                if (CommonUtil.BALLERINA_KEYWORDS.contains(aliasLastPart) && !preDeclaredLangLib) {
                    aliasLastPart = "'" + aliasLastPart;
                }
                String aliasPart = Arrays.stream(aliasParts, 0, aliasParts.length - 1).collect(Collectors.joining());
                alias = aliasPart + "." + aliasLastPart;
            } else {
                if (CommonUtil.BALLERINA_KEYWORDS.contains(alias) && !preDeclaredLangLib) {
                    alias = "'" + alias;
                }
            }
            return orgName + "/" + alias;
        }
        return qualifiedModuleName;
    }

    /**
     * Node comparator to compare the nodes by position.
     */
    public static class BLangNodeComparator implements Comparator<BLangNode> {
        /**
         * {@inheritDoc}
         */
        @Override
        public int compare(BLangNode node1, BLangNode node2) {
            // TODO: Fix?
            Location node1Loc = node1.getPosition();
            Location node2Loc = node2.getPosition();
            if (node1Loc == null || node2Loc == null) {
                return -1;
            }
            return node1Loc.lineRange().startLine().line() - node2Loc.lineRange().startLine().line();
        }
    }

    /**
     * Get the path from given string URI.
     *
     * @param uri file uri
     * @return {@link Optional} Path from the URI
     */
    public static Optional<Path> getPathFromURI(String uri) {
        try {
            return Optional.of(Paths.get(new URL(uri).toURI()));
        } catch (URISyntaxException | MalformedURLException e) {
            // ignore
        }
        return Optional.empty();
    }

    /**
     * Check whether the file is a cached file entry.
     *
     * @param fileUri file URI to evaluate
     * @return whether the file is a cached entry or not
     */
    public static boolean isCachedExternalSource(String fileUri) {
        try {
            Path path = Paths.get(new URI(fileUri));
            return path.toAbsolutePath().toString().startsWith(LS_STDLIB_CACHE_DIR.toAbsolutePath().toString());
        } catch (URISyntaxException e) {
            return false;
        }
    }

    /**
     * Find node of this range.
     *
     * @param range      {@link Range}
     * @param syntaxTree {@link SyntaxTree}
     * @return {@link NonTerminalNode}
     */
    public static NonTerminalNode findNode(Range range, SyntaxTree syntaxTree) {
        TextDocument textDocument = syntaxTree.textDocument();
        Position rangeStart = range.getStart();
        Position rangeEnd = range.getEnd();
        int start = textDocument.textPositionFrom(LinePosition.from(rangeStart.getLine(), rangeStart.getCharacter()));
        int end = textDocument.textPositionFrom(LinePosition.from(rangeEnd.getLine(), rangeEnd.getCharacter()));
        return ((ModulePartNode) syntaxTree.rootNode()).findNode(TextRange.from(start, end - start), true);
    }

    /**
     * Find node of this symbol.
     *
     * @param symbol     {@link Symbol}
     * @param syntaxTree {@link SyntaxTree}
     * @return {@link NonTerminalNode}
     */
    public static NonTerminalNode findNode(Symbol symbol, SyntaxTree syntaxTree) {
        if (symbol.getLocation().isEmpty()) {
            return null;
        }

        TextDocument textDocument = syntaxTree.textDocument();
        LineRange symbolRange = symbol.getLocation().get().lineRange();
        int start = textDocument.textPositionFrom(symbolRange.startLine());
        int len = symbolRange.endLine().offset() - symbolRange.startLine().offset();
        return ((ModulePartNode) syntaxTree.rootNode()).findNode(TextRange.from(start, len), true);
    }

    public static boolean isWithinLineRange(Position pos, LineRange lineRange) {
        int sLine = lineRange.startLine().line();
        int sCol = lineRange.startLine().offset();
        int eLine = lineRange.endLine().line();
        int eCol = lineRange.endLine().offset();
        return ((sLine == eLine && pos.getLine() == sLine) &&
                (pos.getCharacter() >= sCol && pos.getCharacter() <= eCol)
        ) || ((sLine != eLine) && (pos.getLine() > sLine && pos.getLine() < eLine ||
                pos.getLine() == eLine && pos.getCharacter() <= eCol ||
                pos.getLine() == sLine && pos.getCharacter() >= sCol
        ));
    }

    /**
     * Returns whether the position is within the range.
     *
     * @param pos   position
     * @param range range
     * @return True if within range, False otherwise
     */
    public static boolean isWithinRange(Position pos, Range range) {
        int sLine = range.getStart().getLine();
        int sCol = range.getStart().getCharacter();
        int eLine = range.getEnd().getLine();
        int eCol = range.getEnd().getCharacter();
        return ((sLine == eLine && pos.getLine() == sLine) &&
                (pos.getCharacter() >= sCol && pos.getCharacter() <= eCol)
        ) || ((sLine != eLine) && (pos.getLine() > sLine && pos.getLine() < eLine ||
                pos.getLine() == eLine && pos.getCharacter() <= eCol ||
                pos.getLine() == sLine && pos.getCharacter() >= sCol
        ));
    }

    /**
     * Get the raw type of the type descriptor. If the type descriptor is a type reference then return the associated
     * type descriptor.
     *
     * @param typeDescriptor type descriptor to evaluate
     * @return {@link TypeSymbol} extracted type descriptor
     */
    public static TypeSymbol getRawType(TypeSymbol typeDescriptor) {
        if (typeDescriptor.typeKind() == TypeDescKind.INTERSECTION) {
            return getRawType(((IntersectionTypeSymbol) typeDescriptor).effectiveTypeDescriptor());
        }
        return typeDescriptor.typeKind() == TypeDescKind.TYPE_REFERENCE
                ? ((TypeReferenceTypeSymbol) typeDescriptor).typeDescriptor() : typeDescriptor;
    }

    /**
     * Get the completion item label for a given package.
     *
     * @param pkg {@link Package} package instance to evaluate
     * @return {@link String} label computed
     */
    public static String getPackageLabel(Package pkg) {
        String orgName = "";
        if (pkg.packageOrg().value() != null && !pkg.packageOrg().value().equals(Names.ANON_ORG.getValue())) {
            orgName = pkg.packageOrg().value() + "/";
        }

        return orgName + pkg.packageName().value();
    }

    /**
     * Whether the package is already imported in the current document.
     *
     * @param context completion context
     * @param pkg     Package to be evaluated against
     * @return {@link Optional}
     */
    public static Optional<ImportDeclarationNode> matchingImportedModule(CompletionContext context, Package pkg) {
        String name = pkg.packageName().value();
        String orgName = pkg.packageOrg().value();
        List<ImportDeclarationNode> currentDocImports = context.currentDocImports();
        return currentDocImports.stream()
                .filter(importPkg -> importPkg.orgName().isPresent()
                        && importPkg.orgName().get().orgName().text().equals(orgName)
                        && CommonUtil.getPackageNameComponentsCombined(importPkg).equals(name))
                .findFirst();
    }

    /**
     * Whether the package is already imported in the current document.
     *
     * @param context service operation context
     * @param orgName organization name
     * @param modName module name
     * @return {@link Optional}
     */
    public static Optional<ImportDeclarationNode> matchingImportedModule(DocumentServiceContext context, String orgName,
                                                                         String modName) {
        List<ImportDeclarationNode> currentDocImports = context.currentDocImports();
        return currentDocImports.stream()
                .filter(importPkg -> (importPkg.orgName().isEmpty()
                        || importPkg.orgName().get().orgName().text().equals(orgName))
                        && CommonUtil.getPackageNameComponentsCombined(importPkg).equals(modName))
                .findFirst();
    }

    public static boolean isPreDeclaredLangLib(Package pkg) {
        return "ballerina".equals(pkg.packageOrg().value())
                && CommonUtil.PRE_DECLARED_LANG_LIBS.contains(pkg.packageName().value());
    }

    public static String getModifiedTypeName(DocumentServiceContext context, TypeSymbol typeSymbol) {
        Pattern pattern = Pattern.compile("([\\w_.]*)/([\\w._]*):([\\w.-]*)");
        String typeSignature = typeSymbol.signature();
        Matcher matcher = pattern.matcher(typeSignature);
        while (matcher.find()) {
            String orgName = matcher.group(1);
            String moduleName = matcher.group(2);
            String matchedString = matcher.group();
            String modulePrefix = getModulePrefix(context, orgName, moduleName);
            String replaceText = modulePrefix.isEmpty() ? matchedString + Names.VERSION_SEPARATOR : matchedString;
            typeSignature = typeSignature.replace(replaceText, modulePrefix);
        }

        return typeSignature;
    }

    public static String getModulePrefix(DocumentServiceContext context, String orgName, String modName) {
        Project project = context.workspace().project(context.filePath()).orElseThrow();
        String currentProjectOrg = project.currentPackage().packageOrg().value();
        boolean isCurrentOrg = currentProjectOrg.equals(orgName);
        Optional<Module> currentModule = context.currentModule();
        String evalOrgName = isCurrentOrg ? "" : orgName;
        Optional<ImportDeclarationNode> matchedImport = matchingImportedModule(context, evalOrgName, modName);

        if (currentModule.isPresent() && modName.equals(getQualifiedModuleName(currentModule.get()))) {
            // If the module name is same as the current module, then return empty
            return "";
        }
        if (matchedImport.isPresent()) {
            Optional<ImportPrefixNode> prefix = matchedImport.get().prefix();
            if (prefix.isPresent()) {
                return prefix.get().prefix().text();
            }
            SeparatedNodeList<IdentifierToken> moduleComponents = matchedImport.get().moduleName();
            return moduleComponents.get(moduleComponents.size() - 1).text();
        }

        String[] modNameComponents = modName.split("\\.");
        return modNameComponents[modNameComponents.length - 1];
    }
    /**
     * Get the validated symbol name against the visible symbols.
     * This method can be used to auto generate the symbol names without conflicting with the existing symbol names
     *
     * @param context completion context
     * @param symbolName raw symbol name to modify with the numbered suffix
     * @return {@link String} modified symbol name
     */
    public static String getValidatedSymbolName(PositionedOperationContext context, String symbolName) {
        List<Symbol> symbols = context.visibleSymbols(context.getCursorPosition());
        List<Integer> variableNumbers = symbols.parallelStream().map(symbol -> {
            if (symbol.getName().isEmpty()) {
                return -2;
            }
            String sName = symbol.getName().get();
            if (sName.equals(symbolName)) {
                return 0;
            }
            String modifiedName = sName.replaceFirst(symbolName, "");

            if (!modifiedName.isEmpty() && modifiedName.chars().allMatch(Character::isDigit)) {
                return Integer.parseInt(modifiedName);
            }

            return -3;
        }).filter(integer -> integer >= 0).sorted().collect(Collectors.toList());

        for (int i = 0; i < variableNumbers.size(); i++) {
            Integer intVal = variableNumbers.get(i);
            if (i == variableNumbers.size() - 1 || (intVal + 1) != variableNumbers.get(i + 1)) {
                return symbolName + (intVal + 1);
            }
        }

        return symbolName;
    }

    private static String getQualifiedModuleName(Module module) {
        if (module.isDefaultModule()) {
            return module.moduleName().packageName().value();
        }
        return module.moduleName().packageName().value() + Names.DOT.getValue() + module.moduleName().moduleNamePart();
    }

    private static List<String> getBallerinaKeywords() {
        // NOTE: This is a temporary fix to retrieve lexer defined keywords until we comeup with a proper api.
        // Related discussion can be found in https://github.com/ballerina-platform/ballerina-lang/discussions/28827
        try {
            Class<?> aClass = Class.forName("io.ballerina.compiler.internal.parser.LexerTerminals");
            return Arrays.stream(aClass.getDeclaredFields())
                    .filter(field -> field.getModifiers() == (Modifier.PUBLIC | Modifier.STATIC | Modifier.FINAL)
                            && (field.getType() == String.class))
                    .map(field -> {
                        try {
                            return field.get(null).toString();
                        } catch (IllegalAccessException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        } catch (ClassNotFoundException e) {
            return Collections.emptyList();
        }
    }
}
