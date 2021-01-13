/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.ballerinalang.docgen.generator.model;

import io.ballerina.compiler.api.SemanticModel;
import io.ballerina.compiler.syntax.tree.SyntaxTree;
import org.apache.commons.lang3.StringUtils;
import org.ballerinalang.docgen.docs.utils.BallerinaDocUtils;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Heading;
import org.commonmark.node.Node;
import org.commonmark.node.Paragraph;
import org.commonmark.node.Text;
import org.commonmark.renderer.text.TextContentRenderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Package documentation.
 */
public class ModuleDoc {
    public final String description;
    public final String summary;
    public final Map<String, SyntaxTree> syntaxTreeMap;
    public final SemanticModel semanticModel;
    public final List<Path> resources;

    /**
     * Constructor.
     *
     * @param descriptionPath description of the package in markdown format.
     * @param resources       resources of this package.
     * @param syntaxTreeMap a hash map of bal file names and syntax trees.
     * @param semanticModel the semantic model
     * @throws IOException on error.
     */
    public ModuleDoc(Path descriptionPath, List<Path> resources, Map<String, SyntaxTree> syntaxTreeMap,
                     SemanticModel semanticModel) throws IOException {
        this.description = getDescription(descriptionPath);
        this.summary = getSummary(descriptionPath);
        this.resources = resources;
        this.syntaxTreeMap = syntaxTreeMap;
        this.semanticModel = semanticModel;
    }

    private String getDescription(Path descriptionPath) throws IOException {
        if (descriptionPath != null) {
            return new String(Files.readAllBytes(descriptionPath), "UTF-8");
        }
        return null;
    }

    private String getSummary(Path descriptionPath) throws IOException {
        if (descriptionPath != null) {
            String mdContent = new String(Files.readAllBytes(descriptionPath), "UTF-8");
            Node document = BallerinaDocUtils.parseMD(mdContent);
            SummaryVisitor summaryVisitor = new SummaryVisitor();
            document.accept(summaryVisitor);
            return summaryVisitor.getSummary();
        }
        return null;
    }

    /**
     * Used to get a summary of a module or a package.
     */
    public static class SummaryVisitor extends AbstractVisitor {
        protected Node summary;
        @Override
        public void visit(Heading heading) {
            if (heading.getFirstChild() instanceof Text
                    && (StringUtils.equalsIgnoreCase(((Text) heading.getFirstChild()).getLiteral(), "module overview")
                    || StringUtils.equalsIgnoreCase(((Text) heading.getFirstChild()).getLiteral(), "package overview"))
                    && heading.getNext() instanceof Paragraph
            ) {
                summary = heading.getNext();
            }
        }

        public String getSummary() {
            if (summary != null) {
                return TextContentRenderer.builder().build().render(summary);
            }
            return "";
        }
    }

}
