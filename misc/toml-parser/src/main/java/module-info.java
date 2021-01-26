module io.ballerina.toml {
    requires io.ballerina.tools.api;
    requires com.google.gson;
    requires org.apache.commons.text;

    exports io.ballerina.toml.syntax.tree;
    exports io.ballerina.toml.semantic;
    exports io.ballerina.toml.semantic.ast;
    exports io.ballerina.toml.api;
    exports io.ballerina.toml.validator;
    exports io.ballerina.toml.validator.schema;
    exports io.ballerina.toml.semantic.diagnostics;

    opens io.ballerina.toml.validator;
}
