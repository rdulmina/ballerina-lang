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
import io.ballerina.compiler.api.symbols.ClassSymbol;
import io.ballerina.compiler.api.symbols.FieldSymbol;
import io.ballerina.compiler.api.symbols.ModuleSymbol;
import io.ballerina.compiler.api.symbols.RecordTypeSymbol;
import io.ballerina.compiler.api.symbols.Symbol;
import io.ballerina.compiler.api.symbols.TypeDefinitionSymbol;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeReferenceTypeSymbol;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.compiler.api.symbols.UnionTypeSymbol;
import io.ballerina.compiler.syntax.tree.ImportDeclarationNode;
import io.ballerina.compiler.syntax.tree.ModulePartNode;
import io.ballerina.compiler.syntax.tree.NonTerminalNode;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import io.ballerina.compiler.syntax.tree.Token;
import io.ballerina.projects.Package;
import io.ballerina.tools.diagnostics.Location;
import io.ballerina.tools.text.LinePosition;
import io.ballerina.tools.text.LineRange;
import io.ballerina.tools.text.TextDocument;
import io.ballerina.tools.text.TextRange;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.SystemUtils;
import org.ballerinalang.langserver.common.ImportsAcceptor;
import org.ballerinalang.langserver.commons.BallerinaCompletionContext;
import org.ballerinalang.langserver.commons.DocumentServiceContext;
import org.ballerinalang.langserver.commons.PositionedOperationContext;
import org.ballerinalang.langserver.commons.completion.LSCompletionItem;
import org.ballerinalang.langserver.completions.FieldCompletionItem;
import org.ballerinalang.langserver.completions.StaticCompletionItem;
import org.ballerinalang.langserver.completions.util.ItemResolverConstants;
import org.ballerinalang.langserver.completions.util.Priority;
import org.ballerinalang.model.elements.Flag;
import org.ballerinalang.model.tree.TopLevelNode;
import org.ballerinalang.model.tree.statements.StatementNode;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.InsertTextFormat;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.wso2.ballerinalang.compiler.diagnostic.BLangDiagnosticLocation;
import org.wso2.ballerinalang.compiler.semantics.model.Scope;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolEnv;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolTable;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BAnnotationSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BOperatorSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BPackageSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.types.BFutureType;
import org.wso2.ballerinalang.compiler.tree.BLangNode;
import org.wso2.ballerinalang.compiler.tree.BLangPackage;
import org.wso2.ballerinalang.compiler.tree.BLangTypeDefinition;
import org.wso2.ballerinalang.compiler.tree.expressions.BLangInvocation;
import org.wso2.ballerinalang.compiler.tree.statements.BLangSimpleVariableDef;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.Names;

import java.io.File;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

    public static final String LINE_SEPARATOR_SPLIT = "\\r?\\n";

    public static final Pattern MD_NEW_LINE_PATTERN = Pattern.compile("\\s\\s\\r\\n?|\\s\\s\\n|\\r\\n?|\\n");

    public static final String BALLERINA_HOME;

    public static final String BALLERINA_CMD;

    public static final String MARKDOWN_MARKUP_KIND = "markdown";

    public static final String BALLERINA_ORG_NAME = "ballerina";

    public static final String BALLERINAX_ORG_NAME = "ballerinax";

    public static final String SDK_VERSION = System.getProperty("ballerina.version");

    private static final String BUILT_IN_PACKAGE_PREFIX = "lang.annotations";

    public static final Path LS_STDLIB_CACHE_DIR = TEMP_DIR.resolve("ls_stdlib_cache").resolve(SDK_VERSION);

    public static final Path LS_CONNECTOR_CACHE_DIR = TEMP_DIR.resolve("ls_connector_cache").resolve(SDK_VERSION);

    public static final List<String> PRE_DECLARED_LANG_LIBS = Arrays.asList("lang.boolean", "lang.decimal",
            "lang.error", "lang.float", "lang.future", "lang.int", "lang.map", "lang.object", "lang.stream",
            "lang.string", "lang.table", "lang.typedesc", "lang.xml", "lang.annotations");

    static {
        BALLERINA_HOME = System.getProperty("ballerina.home");
        BALLERINA_CMD = BALLERINA_HOME + File.separator + "bin" + File.separator + "ballerina" +
                (SystemUtils.IS_OS_WINDOWS ? ".bat" : "");
    }

    private CommonUtil() {
    }

    /**
     * Convert the diagnostic position to a zero based positioning diagnostic position.
     *
     * @param diagnosticLocation - diagnostic position to be cloned
     * @return {@link Location} converted diagnostic position
     */
    public static Location toZeroBasedPosition(Location diagnosticLocation) {
        int startLine = diagnosticLocation.lineRange().startLine().line() - 1;
        int endLine = diagnosticLocation.lineRange().endLine().line() - 1;
        int startColumn = diagnosticLocation.lineRange().startLine().offset() - 1;
        int endColumn = diagnosticLocation.lineRange().endLine().offset() - 1;
        return new BLangDiagnosticLocation(diagnosticLocation.lineRange().filePath(),
                startLine, endLine, startColumn, endColumn);
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
     * Here we do not check whether the package is not already imported. Particular check should be done before usage
     *
     * @param orgName package org name
     * @param pkgName package name
     * @param context Language server context
     * @return {@link List}     List of Text Edits to apply
     */
    public static List<TextEdit> getAutoImportTextEdits(String orgName, String pkgName,
                                                        DocumentServiceContext context) {
        List<ImportDeclarationNode> currentDocImports = context.currentDocImports();
        Position start = new Position(0, 0);
        if (currentDocImports != null && !currentDocImports.isEmpty()) {
            ImportDeclarationNode last = CommonUtil.getLastItem(currentDocImports);
            int endLine = last.lineRange().endLine().line();
            start = new Position(endLine, 0);
        }
        String pkgNameComponent;
        // Check for the lang lib module insert text
        if ("ballerina".equals(orgName) && pkgName.startsWith("lang.")) {
            pkgNameComponent = pkgName.replace(".", ".'");
        } else {
            pkgNameComponent = pkgName;
        }
        String importStatement = ItemResolverConstants.IMPORT + " "
                + orgName + SLASH_KEYWORD_KEY + pkgNameComponent + SEMI_COLON_SYMBOL_KEY
                + CommonUtil.LINE_SEPARATOR;
        return Collections.singletonList(new TextEdit(new Range(start, start), importStatement));
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

        TypeDescKind typeKind = getRawType(bType).typeKind();
        switch (typeKind) {
            case FLOAT:
                typeString = Float.toString(0);
                break;
            case BOOLEAN:
                typeString = Boolean.toString(false);
                break;
            case ARRAY:
            case RECORD:
            case MAP:
                typeString = "{}";
                break;
            case OBJECT:
                typeString = "new()";
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
     * @param fields  List of field descriptors
     * @return {@link List}     List of completion items for the struct fields
     */
    public static List<LSCompletionItem> getRecordFieldCompletionItems(BallerinaCompletionContext context,
                                                                       List<FieldSymbol> fields) {
        List<LSCompletionItem> completionItems = new ArrayList<>();
        fields.forEach(field -> {
            String insertText = getRecordFieldCompletionInsertText(field, 0);
            CompletionItem fieldItem = new CompletionItem();
            fieldItem.setInsertText(insertText);
            fieldItem.setInsertTextFormat(InsertTextFormat.Snippet);
            fieldItem.setLabel(field.name());
            fieldItem.setDetail(ItemResolverConstants.FIELD_TYPE);
            fieldItem.setKind(CompletionItemKind.Field);
            fieldItem.setSortText(Priority.PRIORITY120.toString());
            completionItems.add(new FieldCompletionItem(context, field, fieldItem));
        });

        return completionItems;
    }

    /**
     * Get the completion item to fill all the struct fields.
     *
     * @param context Language Server Operation Context
     * @param fields  List of fields
     * @return {@link LSCompletionItem}   Completion Item to fill all the options
     */
    public static LSCompletionItem getFillAllStructFieldsItem(BallerinaCompletionContext context,
                                                              List<FieldSymbol> fields) {
        List<String> fieldEntries = new ArrayList<>();

        for (FieldSymbol fieldSymbol : fields) {
            String defaultFieldEntry = fieldSymbol.name()
                    + PKG_DELIMITER_KEYWORD + " " + getDefaultValueForType(fieldSymbol.typeDescriptor());
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
     * Get the Symbol Name.
     *
     * @param bSymbol BSymbol to evaluate
     * @return captured symbol name
     */
    public static String getSymbolName(BSymbol bSymbol) {
        String nameValue = bSymbol.name.getValue();
        String[] split = nameValue.split("\\.");
        return split[split.length - 1];
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
            if (typeDefinitionSymbol.name().equals(typeName)) {
                return Optional.of(typeDefinitionSymbol.typeDescriptor());
            }
        }

        for (ClassSymbol clazz : moduleSymbol.classes()) {
            if (clazz.name().equals(typeName)) {
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
            if (symbol.kind() == MODULE && symbol.name().equals(alias)) {
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
     * Get the Source's owner BLang package, this can be either the parent package or the testable BLang package.
     *
     * @param relativePath Relative source path
     * @param parentPkg    parent package
     * @return {@link BLangPackage} Resolved BLangPackage
     */
    public static BLangPackage getSourceOwnerBLangPackage(String relativePath, BLangPackage parentPkg) {
        return isTestSource(relativePath) ? parentPkg.getTestablePkg() : parentPkg;
    }

    public static boolean isInvalidSymbol(BSymbol symbol) {
        return ("_".equals(symbol.name.getValue())
                || symbol instanceof BAnnotationSymbol
                || symbol instanceof BOperatorSymbol
                || symbolContainsInvalidChars(symbol));
    }

    /**
     * Check whether the given node is a worker derivative node.
     *
     * @param node Node to be evaluated
     * @return {@link Boolean}  whether a worker derivative
     */
    public static boolean isWorkerDereivative(StatementNode node) {
        return (node instanceof BLangSimpleVariableDef)
                && ((BLangSimpleVariableDef) node).var.expr != null
                && ((BLangSimpleVariableDef) node).var.expr.type instanceof BFutureType
                && ((BFutureType) ((BLangSimpleVariableDef) node).var.expr.type).workerDerivative;
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
    public static List<FieldSymbol> getMandatoryRecordFields(RecordTypeSymbol recordType) {
        return recordType.fieldDescriptors().stream()
                .filter(field -> !field.hasDefaultValue() && !field.isOptional())
                .collect(Collectors.toList());
    }

    /**
     * Get the completion item insert text for a BField.
     *
     * @param bField BField to evaluate
     * @return {@link String} Insert text
     */
    public static String getRecordFieldCompletionInsertText(FieldSymbol bField, int tabOffset) {
        TypeSymbol fieldType = CommonUtil.getRawType(bField.typeDescriptor());
        StringBuilder insertText = new StringBuilder(bField.name() + ": ");
        if (fieldType.typeKind() == TypeDescKind.RECORD) {
            List<FieldSymbol> requiredFields = getMandatoryRecordFields((RecordTypeSymbol) fieldType);
            if (requiredFields.isEmpty()) {
                insertText.append("{").append("${1}}");
                return insertText.toString();
            }
            insertText.append("{").append(LINE_SEPARATOR);
            List<String> requiredFieldInsertTexts = new ArrayList<>();
            for (FieldSymbol field : requiredFields) {
                String fieldText = String.join("", Collections.nCopies(tabOffset + 1, "\t")) +
                        getRecordFieldCompletionInsertText(field, tabOffset + 1);
                requiredFieldInsertTexts.add(fieldText);
            }
            insertText.append(String.join("," + CommonUtil.LINE_SEPARATOR, requiredFieldInsertTexts));
            insertText.append(LINE_SEPARATOR)
                    .append(String.join("", Collections.nCopies(tabOffset, "\t")))
                    .append("}");
        } else if (fieldType.typeKind() == TypeDescKind.ARRAY) {
            insertText.append("[").append("${1}").append("]");
        } else if (fieldType.typeKind().isStringType()) {
            insertText.append("\"").append("${1}").append("\"");
        } else {
            insertText.append("${1:").append(getDefaultValueForType(bField.typeDescriptor())).append("}");
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
        if (symbol != null) {
            // Start naming with symbol-name
            return generateVariableName(1, symbol.name(), names);
        } else if (typeSymbol != null) {
            // If symbol is null, try typeSymbol
            String name;
            if (typeSymbol.typeKind() == TypeDescKind.TYPE_REFERENCE && !typeSymbol.name().startsWith("$")) {
                name = typeSymbol.name();
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
        return moduleID.orgName().equals("ballerina")
                && moduleID.moduleName().startsWith("lang.");
    }

    private static String generateVariableName(int suffix, String name, Set<String> names) {
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

    public static BLangPackage getPackageNode(BLangNode bLangNode) {
        BLangNode parent = bLangNode.parent;
        if (parent != null) {
            return (parent instanceof BLangPackage) ? (BLangPackage) parent : getPackageNode(parent);
        }
        return null;
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
            String moduleName = escapeModuleName(context, moduleID.orgName() + "/" + moduleID.moduleName());
            String[] moduleParts = moduleName.split("/");
            String orgName = moduleParts[0];
            String alias = moduleParts[1];
            pkgPrefix = alias.replaceAll(".*\\.", "") + ":";
            pkgPrefix = (preDeclaredLangLib) ? "'" + pkgPrefix : pkgPrefix;
            if (importsAcceptor != null && !preDeclaredLangLib) {
                importsAcceptor.getAcceptor().accept(orgName, alias);
            }
        }
        return pkgPrefix;
    }

    public static String escapeModuleName(DocumentServiceContext context, String fullPackageNameAlias) {
        Set<String> names = new HashSet<>();
        Predicate<Scope.ScopeEntry> nonPkgNames = scopeEntry -> !(scopeEntry.symbol instanceof BPackageSymbol);
        try {
            // TODO: Fix this, need an API to fetch all reserved keywords
//            names = CommonUtil.getAllNameEntries(context.get(DocumentServiceKeys.COMPILER_CONTEXT_KEY), nonPkgNames);
        } catch (Exception e) {
            // ignore
        }

        String[] moduleNameParts = fullPackageNameAlias.split("/");
        String moduleName = moduleNameParts[0];
        if (moduleNameParts.length > 1) {
            String alias = moduleNameParts[1];
            String[] aliasParts = moduleNameParts[1].split("\\.");
            if (aliasParts.length > 1) {
                String aliasPart1 = aliasParts[0];
                String aliasPart2 = aliasParts[1];
                if (names.contains(aliasPart2)) {
                    aliasPart2 = "'" + aliasPart2;
                }
                alias = aliasPart1 + "." + aliasPart2;
            } else {
                if (names.contains(alias)) {
                    alias = "'" + alias;
                }
            }
            moduleName = moduleName + "/" + alias;
        }
        return moduleName;
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
     * Get all available name entries.
     *
     * @param visibleSymbols list of visible symbols
     * @return set of strings
     */
    public static Set<String> getAllNameEntries(List<Symbol> visibleSymbols) {
        return visibleSymbols.stream().map(Symbol::name).collect(Collectors.toSet());
    }

    /**
     * Get all available name entries.
     *
     * @param context {@link CompilerContext}
     * @return set of strings
     */
    public static Set<String> getAllNameEntries(CompilerContext context, Predicate<Scope.ScopeEntry> predicate) {
        Set<String> strings = new HashSet<>();
        SymbolTable symbolTable = SymbolTable.getInstance(context);
        Map<BPackageSymbol, SymbolEnv> pkgEnvMap = symbolTable.pkgEnvMap;
        pkgEnvMap.values().forEach(env -> env.scope.entries.forEach((key, value) -> {
            if (predicate != null) {
                if (predicate.test(value)) {
                    strings.add(key.value);
                }
            } else {
                strings.add(key.value);
            }
        }));
        return strings;
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
        return ((ModulePartNode) syntaxTree.rootNode()).findNode(TextRange.from(start, end - start));
    }

    /**
     * Find node of this symbol.
     *
     * @param symbol     {@link Symbol}
     * @param syntaxTree {@link SyntaxTree}
     * @return {@link NonTerminalNode}
     */
    public static NonTerminalNode findNode(Symbol symbol, SyntaxTree syntaxTree) {
        TextDocument textDocument = syntaxTree.textDocument();
        LineRange symbolRange = symbol.location().lineRange();
        int start = textDocument.textPositionFrom(symbolRange.startLine());
        int len = symbolRange.endLine().offset() - symbolRange.startLine().offset();
        return ((ModulePartNode) syntaxTree.rootNode()).findNode(TextRange.from(start, len));
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
}
