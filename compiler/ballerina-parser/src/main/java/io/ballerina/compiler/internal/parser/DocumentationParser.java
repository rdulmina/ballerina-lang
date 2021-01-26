/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.ballerina.compiler.internal.parser;

import io.ballerina.compiler.internal.diagnostics.DiagnosticWarningCode;
import io.ballerina.compiler.internal.parser.tree.STNode;
import io.ballerina.compiler.internal.parser.tree.STNodeFactory;
import io.ballerina.compiler.internal.parser.tree.STToken;
import io.ballerina.compiler.syntax.tree.SyntaxKind;

import java.util.ArrayList;
import java.util.List;

/**
 * A Documentation parser for ballerina.
 *
 * @since 2.0.0
 */
public class DocumentationParser extends AbstractParser {

    /* Ballerina flavored markdown (BFM) is supported by the documentation.
     * There is no error handler attached to this parser.
     * In case of an error, simply missing token will be inserted.
     */

    protected DocumentationParser(AbstractTokenReader tokenReader) {
        super(tokenReader);
    }

    @Override
    public STNode parse() {
        return parseDocumentationLines();
    }

    /**
     * Parse documentation lines.
     * <p>
     * <code>
     * DocumentationLine :=
     *          ( DocumentationLine
     *          | ReferenceDocumentationLine
     *          | DeprecationDocumentationLine
     *          | ParameterDocumentationLine
     *          | ReturnParameterDocumentationLine
     *          | DocumentationCodeLine
     *          | InvalidDocumentationLine )
     * </code>
     * <p>
     * Refer {@link DocumentationLexer}
     *
     * @return Parsed node
     */
    private STNode parseDocumentationLines() {
        List<STNode> docLines = new ArrayList<>();
        STToken nextToken = peek();
        while (nextToken.kind == SyntaxKind.HASH_TOKEN) {
            docLines.add(parseSingleDocumentationLine());
            nextToken = peek();
        }

        return STNodeFactory.createNodeList(docLines);
    }

    /**
     * Parse a single documentation line.
     *
     * @return Parsed node
     */
    private STNode parseSingleDocumentationLine() {
        STNode hashToken = consume();
        STToken nextToken = peek();
        if (nextToken.kind == SyntaxKind.PLUS_TOKEN) {
            return parseParameterDocumentationLine(hashToken);
        } else if (nextToken.kind == SyntaxKind.DEPRECATION_LITERAL) {
            return parseDeprecationDocumentationLine(hashToken);
        }
        return parseDocumentationLine(hashToken);
    }

    /**
     * Parse deprecation documentation line.
     *
     * @param hashToken Hash token at the beginning of the line
     * @return Parsed node
     */
    private STNode parseDeprecationDocumentationLine(STNode hashToken) {
        STNode deprecationLiteral = consume();

        List<STNode> docElements = parseDocumentationElements();
        docElements.add(0, deprecationLiteral);

        STNode docElementList = STNodeFactory.createNodeList(docElements);
        return createMarkdownDeprecationDocumentationLineNode(hashToken, docElementList);
    }

    /**
     * Parse documentation line, reference documentation line and code documentation line.
     *
     * @param hashToken Hash token at the beginning of the line
     * @return Parsed node
     */
    private STNode parseDocumentationLine(STNode hashToken) {
        List<STNode> docElements = parseDocumentationElements();
        STNode docElementList = STNodeFactory.createNodeList(docElements);

        switch (docElements.size()) {
            case 0:
                // When documentation line is only a `#` token
                return createMarkdownDocumentationLineNode(hashToken, docElementList);
            case 1:
                STNode docElement = docElements.get(0);
                if (docElement.kind == SyntaxKind.DOCUMENTATION_DESCRIPTION) {
                    return createMarkdownDocumentationLineNode(hashToken, docElementList);
                } else if (docElement.kind == SyntaxKind.CODE_DESCRIPTION) {
                    return createMarkdownCodeDocumentationLineNode(hashToken, docElementList);
                }
                // Else fall through
            default:
                return createMarkdownReferenceDocumentationLineNode(hashToken, docElementList);
        }
    }

    private List<STNode> parseDocumentationElements() {
        List<STNode> docElements = new ArrayList<>();
        STNode docElement;
        SyntaxKind nextTokenKind = peek().kind;
        while (!isEndOfIntermediateDocumentation(nextTokenKind)) {
            if (nextTokenKind == SyntaxKind.DOCUMENTATION_DESCRIPTION || nextTokenKind == SyntaxKind.CODE_DESCRIPTION) {
                docElement = consume();
            } else {
                docElement = parseDocumentationReference();
            }
            docElements.add(docElement);
            nextTokenKind = peek().kind;
        }
        return docElements;
    }

    private STNode parseDocumentationReference() {
        STNode referenceType = STNodeFactory.createEmptyNode();
        if (isDocumentReferenceType(peek().kind)) {
            referenceType = consume();
        }

        STNode startBacktick = parseBacktickToken();
        STNode backtickContent = parseBacktickContent(referenceType);
        STNode endBacktick = parseBacktickToken();
        return STNodeFactory.createDocumentationReferenceNode(referenceType, startBacktick, backtickContent,
                endBacktick);
    }

    /**
     * Represents the current position with respect to the head in a token-sequence-search.
     */
    private static class Lookahead {
        private int offset = 1;
    }

    /**
     * Genre of the reference that precedes the backtick block.
     */
    private enum ReferenceGenre {
        NO_KEY, SPECIAL_KEY, FUNCTION_KEY
    }

    /**
     * Look ahead and see if upcoming token sequence is valid.
     *
     * @param refGenre Genre of the backtick block reference
     * @return <code>true</code> if content is valid<code>false</code> otherwise.
     */
    private boolean isValidBacktickContentSequence(ReferenceGenre refGenre) {
        boolean hasMatch;
        Lookahead lookahead = new Lookahead();
        switch (refGenre) {
            case SPECIAL_KEY:
                // Look for x, m:x match
                hasMatch = hasQualifiedIdentifier(lookahead);
                break;
            case FUNCTION_KEY:
                // Look for x, m:x, x(), m:x(), T.y(), m:T.y() match
                hasMatch = hasBacktickExpr(lookahead, true);
                break;
            case NO_KEY:
                // Look for x(), m:x(), T.y(), m:T.y() match
                hasMatch = hasBacktickExpr(lookahead, false);
                break;
            default:
                throw new IllegalStateException("Unsupported backtick reference genre");
        }

        return hasMatch && peek(lookahead.offset).kind == SyntaxKind.BACKTICK_TOKEN;
    }

    private boolean hasBacktickExpr(Lookahead lookahead, boolean isFunctionKey) {
        if (!hasQualifiedIdentifier(lookahead)) {
            return false;
        }

        STToken nextToken = peek(lookahead.offset);
        if (nextToken.kind == SyntaxKind.OPEN_PAREN_TOKEN) {
            return hasFuncSignature(lookahead);
        } else if (nextToken.kind == SyntaxKind.DOT_TOKEN) {
            lookahead.offset++;
            if (!hasIdentifier(lookahead)) {
                return false;
            }
            return hasFuncSignature(lookahead);
        }

        return isFunctionKey;
    }

    private boolean hasFuncSignature(Lookahead lookahead) {
        if (!hasOpenParenthesis(lookahead)) {
            return false;
        }
        return hasCloseParenthesis(lookahead);
    }

    private boolean hasOpenParenthesis(Lookahead lookahead) {
        STToken nextToken = peek(lookahead.offset);
        if (nextToken.kind == SyntaxKind.OPEN_PAREN_TOKEN) {
            lookahead.offset++;
            return true;
        } else {
            return false;
        }
    }

    private boolean hasCloseParenthesis(Lookahead lookahead) {
        STToken nextToken = peek(lookahead.offset);
        if (nextToken.kind == SyntaxKind.CLOSE_PAREN_TOKEN) {
            lookahead.offset++;
            return true;
        } else {
            return false;
        }
    }

    private boolean hasQualifiedIdentifier(Lookahead lookahead) {
        if (!hasIdentifier(lookahead)) {
            return false;
        }

        STToken nextToken = peek(lookahead.offset);
        if (nextToken.kind == SyntaxKind.COLON_TOKEN) {
            lookahead.offset++;
            return hasIdentifier(lookahead);
        }

        return true;
    }

    private boolean hasIdentifier(Lookahead lookahead) {
        STToken  nextToken = peek(lookahead.offset);
        if (nextToken.kind == SyntaxKind.IDENTIFIER_TOKEN) {
            lookahead.offset++;
            return true;
        }
        return false;
    }

    private boolean isDocumentReferenceType(SyntaxKind kind) {
        switch (kind) {
            case TYPE_DOC_REFERENCE_TOKEN:
            case SERVICE_DOC_REFERENCE_TOKEN:
            case VARIABLE_DOC_REFERENCE_TOKEN:
            case VAR_DOC_REFERENCE_TOKEN:
            case ANNOTATION_DOC_REFERENCE_TOKEN:
            case MODULE_DOC_REFERENCE_TOKEN:
            case FUNCTION_DOC_REFERENCE_TOKEN:
            case PARAMETER_DOC_REFERENCE_TOKEN:
            case CONST_DOC_REFERENCE_TOKEN:
                return true;
            default:
                return false;
        }
    }

    /**
     * Parse parameter documentation line and return parameter documentation line.
     *
     * @param hashToken Hash token at the beginning of the line
     * @return Parsed node
     */
    private STNode parseParameterDocumentationLine(STNode hashToken) {
        STNode plusToken = consume();
        STNode parameterName = parseParameterName();
        STNode dashToken = parseMinusToken();

        List<STNode> docElements = parseDocumentationElements();
        STNode docElementList = STNodeFactory.createNodeList(docElements);

        SyntaxKind kind;
        if (parameterName.kind == SyntaxKind.RETURN_KEYWORD) {
            kind = SyntaxKind.MARKDOWN_RETURN_PARAMETER_DOCUMENTATION_LINE;
        } else {
            kind = SyntaxKind.MARKDOWN_PARAMETER_DOCUMENTATION_LINE;
        }

        return STNodeFactory.createMarkdownParameterDocumentationLineNode(kind, hashToken, plusToken, parameterName,
                dashToken, docElementList);
    }

    private boolean isEndOfIntermediateDocumentation(SyntaxKind kind) {
        switch (kind) {
            case DOCUMENTATION_DESCRIPTION:
            case PLUS_TOKEN:
            case PARAMETER_NAME:
            case MINUS_TOKEN:
            case BACKTICK_TOKEN:
            case BACKTICK_CONTENT:
            case RETURN_KEYWORD:
            case DEPRECATION_LITERAL:
            case CODE_DESCRIPTION:
                return false;
            default:
                return !isDocumentReferenceType(kind);
        }
    }

    /**
     * Parse parameter name token.
     *
     * @return Parsed node
     */
    private STNode parseParameterName() {
        SyntaxKind tokenKind = peek().kind;
        if (tokenKind == SyntaxKind.PARAMETER_NAME || tokenKind == SyntaxKind.RETURN_KEYWORD) {
            return consume();
        } else {
            return STNodeFactory.createMissingToken(SyntaxKind.PARAMETER_NAME);
        }
    }

    /**
     * Parse minus token.
     *
     * @return Parsed node
     */
    private STNode parseMinusToken() {
        STToken token = peek();
        if (token.kind == SyntaxKind.MINUS_TOKEN) {
            return consume();
        } else {
            return STNodeFactory.createMissingToken(SyntaxKind.MINUS_TOKEN);
        }
    }

    /**
     * Parse back-tick token.
     *
     * @return Parsed node
     */
    private STNode parseBacktickToken() {
        STToken token = peek();
        if (token.kind == SyntaxKind.BACKTICK_TOKEN) {
            return consume();
        } else {
            return STNodeFactory.createMissingToken(SyntaxKind.BACKTICK_TOKEN);
        }
    }

    /**
     * Parse back-tick content.
     *
     * @param referenceType Node that precedes the backtick block
     * @return Parsed node
     */
    private STNode parseBacktickContent(STNode referenceType) {
        ReferenceGenre referenceGenre = getReferenceGenre(referenceType);
        if (isValidBacktickContentSequence(referenceGenre)) {
            return parseBacktickContent();
        }

        STNode contentToken = combineAndCreateBacktickContentToken();
        if (referenceGenre != ReferenceGenre.NO_KEY) {
            // Log warning for backtick block with a reference type, but content is invalid.
            contentToken = SyntaxErrors.addDiagnostic(contentToken,
                    DiagnosticWarningCode.WARNING_INVALID_DOCUMENTATION_IDENTIFIER, ((STToken) contentToken).text());
        }

        return contentToken;
    }

    /**
     * Get the genre of the reference type.
     *
     * @param referenceType Node that precedes the backtick block
     * @return Enum representing the genre
     */
    private ReferenceGenre getReferenceGenre(STNode referenceType) {
        if (referenceType == null) {
            return ReferenceGenre.NO_KEY;
        }

        if (referenceType.kind == SyntaxKind.FUNCTION_DOC_REFERENCE_TOKEN) {
            return ReferenceGenre.FUNCTION_KEY;
        }

        return ReferenceGenre.SPECIAL_KEY;
    }

    private STNode combineAndCreateBacktickContentToken() {
        if (!isBacktickExprToken(peek().kind)) {
            return STNodeFactory.createMissingToken(SyntaxKind.BACKTICK_CONTENT);
        }

        StringBuilder backtickContent = new StringBuilder();
        STToken token;
        while (isBacktickExprToken(peek(2).kind)) {
            token = consume();
            backtickContent.append(token.toString());
        }
        token = consume();
        backtickContent.append(token.text());

        // We do not capture leading minutiae in DOCUMENTATION_BACKTICK_EXPR lexer mode.
        // Therefore, set only the trailing minutiae
        STNode leadingMinutiae = STNodeFactory.createEmptyNodeList();
        STNode trailingMinutiae = token.trailingMinutiae();
        return STNodeFactory.createLiteralValueToken(SyntaxKind.BACKTICK_CONTENT, backtickContent.toString(),
                leadingMinutiae, trailingMinutiae);
    }

    private boolean isBacktickExprToken(SyntaxKind kind) {
        switch (kind) {
            case DOT_TOKEN:
            case COLON_TOKEN:
            case OPEN_PAREN_TOKEN:
            case CLOSE_PAREN_TOKEN:
            case IDENTIFIER_TOKEN:
            case BACKTICK_CONTENT:
                return true;
            default:
                return false;
        }
    }

    private STNode parseBacktickContent() {
        STToken token = peek();
        if (token.kind == SyntaxKind.IDENTIFIER_TOKEN) {
            STNode identifier = consume();
            return parseBacktickExpr(identifier);
        }
        return parseBacktickContentToken();
    }

    /**
     * Parse back-tick content token.
     *
     * @return Parsed node
     */
    private STNode parseBacktickContentToken() {
        STToken token = peek();
        if (token.kind == SyntaxKind.BACKTICK_CONTENT) {
            return consume();
        } else {
            return STNodeFactory.createMissingToken(SyntaxKind.BACKTICK_CONTENT);
        }
    }

    /**
     * Parse back-tick expr.
     *
     * @param identifier Initial identifier
     * @return Function call, method call or name reference node
     */
    private STNode parseBacktickExpr(STNode identifier) {
        STNode referenceName = parseQualifiedIdentifier(identifier);

        STToken nextToken = peek();
        switch (nextToken.kind) {
            case BACKTICK_TOKEN:
                return referenceName;
            case DOT_TOKEN:
                STNode dotToken = consume();
                return parseMethodCall(referenceName, dotToken);
            case OPEN_PAREN_TOKEN:
                return parseFuncCall(referenceName);
            default:
                // Since we have validated the token sequence beforehand, code should not reach here.
                throw new IllegalStateException("Unsupported token kind");
        }
    }

    /**
     * Parse qualified name reference or simple name reference.
     *
     * @param identifier Initial identifier
     * @return Parsed node
     */
    private STNode parseQualifiedIdentifier(STNode identifier) {
        STToken nextToken = peek();
        if (nextToken.kind == SyntaxKind.COLON_TOKEN) {
            STNode colon = consume();
            return parseQualifiedIdentifier(identifier, colon);
        }
        return STNodeFactory.createSimpleNameReferenceNode(identifier);
    }

    private STNode parseQualifiedIdentifier(STNode identifier, STNode colon) {
        STNode refName = parseIdentifier();
        return STNodeFactory.createQualifiedNameReferenceNode(identifier, colon, refName);
    }

    /**
     * Parse identifier token.
     *
     * @return Parsed node
     */
    private STNode parseIdentifier() {
        STToken token = peek();
        if (token.kind == SyntaxKind.IDENTIFIER_TOKEN) {
            return consume();
        } else {
            return STNodeFactory.createMissingToken(SyntaxKind.IDENTIFIER_TOKEN);
        }
    }

    /**
     * Parse function call expression.
     * <code>function-call-expr := function-reference ( )</code>
     *
     * @param referenceName Function name
     * @return Function call expression
     */
    private STNode parseFuncCall(STNode referenceName) {
        STNode openParen = parseOpenParenthesis();
        STNode args = STNodeFactory.createEmptyNodeList();
        STNode closeParen = parseCloseParenthesis();
        return STNodeFactory.createFunctionCallExpressionNode(referenceName, openParen, args, closeParen);
    }

    /**
     * Parse method call expression.
     * <code>method-call-expr := reference-name . method-name ( )</code>
     *
     * @param referenceName Reference name
     * @param dotToken Dot token
     * @return Method call expression
     */
    private STNode parseMethodCall(STNode referenceName, STNode dotToken) {
        STNode methodName = parseSimpleNameReference();
        STNode openParen = parseOpenParenthesis();
        STNode args = STNodeFactory.createEmptyNodeList();
        STNode closeParen = parseCloseParenthesis();
        return STNodeFactory.createMethodCallExpressionNode(referenceName, dotToken, methodName, openParen, args,
                closeParen);
    }

    /**
     * Parse simple name reference.
     *
     * @return Parsed node
     */
    private STNode parseSimpleNameReference() {
        STNode identifier = parseIdentifier();
        return STNodeFactory.createSimpleNameReferenceNode(identifier);
    }

    /**
     * Parse open parenthesis.
     *
     * @return Parsed node
     */
    private STNode parseOpenParenthesis() {
        STToken token = peek();
        if (token.kind == SyntaxKind.OPEN_PAREN_TOKEN) {
            return consume();
        } else {
            return STNodeFactory.createMissingToken(SyntaxKind.OPEN_PAREN_TOKEN);
        }
    }

    /**
     * Parse close parenthesis.
     *
     * @return Parsed node
     */
    private STNode parseCloseParenthesis() {
        STToken token = peek();
        if (token.kind == SyntaxKind.CLOSE_PAREN_TOKEN) {
            return consume();
        } else {
            return STNodeFactory.createMissingToken(SyntaxKind.CLOSE_PAREN_TOKEN);
        }
    }

    private STNode createMarkdownDocumentationLineNode(STNode hashToken, STNode documentationElements) {
        return STNodeFactory.createMarkdownDocumentationLineNode(SyntaxKind.MARKDOWN_DOCUMENTATION_LINE, hashToken,
                documentationElements);
    }

    private STNode createMarkdownCodeDocumentationLineNode(STNode hashToken, STNode documentationElements) {
        return STNodeFactory.createMarkdownDocumentationLineNode(SyntaxKind.MARKDOWN_CODE_DOCUMENTATION_LINE, hashToken,
                documentationElements);
    }

    private STNode createMarkdownDeprecationDocumentationLineNode(STNode hashToken, STNode documentationElements) {
        return STNodeFactory.createMarkdownDocumentationLineNode(SyntaxKind.MARKDOWN_DEPRECATION_DOCUMENTATION_LINE,
                hashToken, documentationElements);
    }

    private STNode createMarkdownReferenceDocumentationLineNode(STNode hashToken, STNode documentationElements) {
        return STNodeFactory.createMarkdownDocumentationLineNode(SyntaxKind.MARKDOWN_REFERENCE_DOCUMENTATION_LINE,
                hashToken, documentationElements);
    }
}
