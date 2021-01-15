/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
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
package io.ballerina.compiler.api.impl.symbols;

import io.ballerina.compiler.api.ModuleID;
import io.ballerina.compiler.api.impl.LangLibrary;
import io.ballerina.compiler.api.symbols.FunctionSymbol;
import io.ballerina.compiler.api.symbols.ParameterSymbol;
import io.ballerina.compiler.api.symbols.SymbolKind;
import io.ballerina.compiler.api.symbols.TypeDescKind;
import io.ballerina.compiler.api.symbols.TypeSymbol;
import io.ballerina.tools.diagnostics.Location;
import org.wso2.ballerinalang.compiler.semantics.analyzer.Types;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.util.CompilerContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Ballerina Type Descriptor.
 *
 * @since 2.0.0
 */
public abstract class AbstractTypeSymbol implements TypeSymbol {

    protected final CompilerContext context;
    protected List<FunctionSymbol> langLibFunctions;

    private final TypeDescKind typeDescKind;
    private final ModuleID moduleID;
    private final BType bType;

    public AbstractTypeSymbol(CompilerContext context, TypeDescKind typeDescKind, ModuleID moduleID, BType bType) {
        this.context = context;
        this.typeDescKind = typeDescKind;
        this.moduleID = moduleID;
        this.bType = bType;
    }

    @Override
    public TypeDescKind typeKind() {
        return typeDescKind;
    }

    @Override
    public ModuleID moduleID() {
        return moduleID;
    }

    @Override
    public abstract String signature();

    @Override
    public String name() {
        return "";
    }

    @Override
    public SymbolKind kind() {
        return SymbolKind.TYPE;
    }

    @Override
    public Location location() {
        return null;
    }

    @Override
    public List<FunctionSymbol> langLibMethods() {
        if (this.langLibFunctions == null) {
            LangLibrary langLibrary = LangLibrary.getInstance(this.context);
            List<FunctionSymbol> functions = langLibrary.getMethods(this.typeKind());
            this.langLibFunctions = filterLangLibMethods(functions, this.getBType());
        }

        return this.langLibFunctions;
    }

    @Override
    public boolean assignableTo(TypeSymbol targetType) {
        Types types = Types.getInstance(this.context);
        return types.isAssignable(this.bType, getTargetBType(targetType));
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (!(obj instanceof TypeSymbol)) {
            return false;
        }

        Types types = Types.getInstance(this.context);
        return types.isSameType(this.bType, ((AbstractTypeSymbol) obj).getBType());
    }

    @Override
    public int hashCode() {
        return this.bType.hashCode();
    }

    /**
     * Get the BType.
     *
     * @return {@link BType} associated with the type desc
     */
    public BType getBType() {
        return bType;
    }

    protected List<FunctionSymbol> filterLangLibMethods(List<FunctionSymbol> functions, BType internalType) {
        Types types = Types.getInstance(this.context);
        List<FunctionSymbol> filteredFunctions = new ArrayList<>();

        for (FunctionSymbol function : functions) {
            ParameterSymbol firstParam = function.typeDescriptor().parameters().get(0);
            BType firstParamType = ((AbstractTypeSymbol) firstParam.typeDescriptor()).getBType();

            if (types.isAssignable(internalType, firstParamType)) {
                filteredFunctions.add(function);
            }
        }

        return filteredFunctions;
    }

    // Private util methods

    private BType getTargetBType(TypeSymbol typeSymbol) {
        if (typeSymbol.kind() == SymbolKind.TYPE) {
            return ((AbstractTypeSymbol) typeSymbol).getBType();
        }

        return ((BallerinaClassSymbol) typeSymbol).getBType();
    }
}
