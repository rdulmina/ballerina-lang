/*
 *  Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.ballerinalang.compiler.semantics.analyzer;

import io.ballerina.tools.diagnostics.Location;
import org.ballerinalang.model.Name;
import org.ballerinalang.util.diagnostic.DiagnosticErrorCode;
import org.wso2.ballerinalang.compiler.diagnostic.BLangDiagnosticLog;
import org.wso2.ballerinalang.compiler.semantics.model.Scope;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolEnv;
import org.wso2.ballerinalang.compiler.semantics.model.SymbolTable;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BAttachedFunction;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BErrorTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BInvokableSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BObjectTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BRecordTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BResourceFunction;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BTypeSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.BVarSymbol;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.SymTag;
import org.wso2.ballerinalang.compiler.semantics.model.symbols.Symbols;
import org.wso2.ballerinalang.compiler.semantics.model.types.BAnyType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BAnydataType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BArrayType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BErrorType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BField;
import org.wso2.ballerinalang.compiler.semantics.model.types.BInvokableType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BMapType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BObjectType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BReadonlyType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BRecordType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BStreamType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BTableType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BTupleType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BTypedescType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BUnionType;
import org.wso2.ballerinalang.compiler.semantics.model.types.BXMLType;
import org.wso2.ballerinalang.compiler.util.CompilerContext;
import org.wso2.ballerinalang.compiler.util.Names;
import org.wso2.ballerinalang.compiler.util.TypeTags;
import org.wso2.ballerinalang.util.Flags;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

import static org.ballerinalang.model.symbols.SymbolOrigin.VIRTUAL;

/**
 * This class consists of utility methods which operate on TypeParams (Parametric types).
 *
 * @since JB 1.0.0
 */
public class TypeParamAnalyzer {

    // How @typeParam works in 2019R2 spec.
    //
    // e.g. lang.array module.
    //
    // @typeParam
    // type Type1 any|error;
    //
    // public function getFirstAndSize(Type1[] array) returns [Type1,int] {
    //  return [array[0], array.length()];
    // }

    private static final CompilerContext.Key<TypeParamAnalyzer> TYPE_PARAM_ANALYZER_KEY =
            new CompilerContext.Key<>();

    private SymbolTable symTable;
    private Types types;
    private Names names;
    private BLangDiagnosticLog dlog;

    public static TypeParamAnalyzer getInstance(CompilerContext context) {

        TypeParamAnalyzer types = context.get(TYPE_PARAM_ANALYZER_KEY);
        if (types == null) {
            types = new TypeParamAnalyzer(context);
        }

        return types;
    }

    private TypeParamAnalyzer(CompilerContext context) {

        context.put(TYPE_PARAM_ANALYZER_KEY, this);

        this.symTable = SymbolTable.getInstance(context);
        this.types = Types.getInstance(context);
        this.names = Names.getInstance(context);
        this.dlog = BLangDiagnosticLog.getInstance(context);
    }

    static boolean isTypeParam(BType expType) {

        return Symbols.isFlagOn(expType.flags, Flags.TYPE_PARAM)
                || (expType.tsymbol != null && Symbols.isFlagOn(expType.tsymbol.flags, Flags.TYPE_PARAM));
    }

    public static boolean containsTypeParam(BType type) {

        return containsTypeParam(type, new HashSet<>());
    }

    void checkForTypeParamsInArg(Location loc, BType actualType, SymbolEnv env, BType expType) {

        // Not a langlib module invocation
        if (notRequireTypeParams(env)) {
            return;
        }

        FindTypeParamResult findTypeParamResult = new FindTypeParamResult();
        findTypeParam(loc, expType, actualType, env, new HashSet<>(), findTypeParamResult);
    }

    boolean notRequireTypeParams(SymbolEnv env) {

        return env.typeParamsEntries == null;
    }

    BType getReturnTypeParams(SymbolEnv env, BType expType) {

        if (notRequireTypeParams(env) || env.typeParamsEntries.isEmpty()) {
            return expType;
        }
        return getMatchingBoundType(expType, env);
    }

    public BType getNominalType(BType type, Name name, long flag) {
        // Only type params has nominal behaviour for now.
        if (name == Names.EMPTY) {
            return type;
        }
        return createBuiltInType(type, name, flag);
    }

    BType createTypeParam(BType type, Name name) {

        var flag = type.flags | Flags.TYPE_PARAM;
        return createBuiltInType(type, name, flag);
    }

    BType getMatchingBoundType(BType expType, SymbolEnv env) {

        return getMatchingBoundType(expType, env, new HashSet<>());
    }

    // Private methods.

    private static boolean containsTypeParam(BType type, HashSet<BType> resolvedTypes) {
        if (resolvedTypes.contains(type)) {
            return false;
        }
        resolvedTypes.add(type);
        if (isTypeParam(type)) {
            return true;
        }
        switch (type.tag) {
            case TypeTags.ARRAY:
                return containsTypeParam(((BArrayType) type).eType, resolvedTypes);
            case TypeTags.TUPLE:
                BTupleType bTupleType = (BTupleType) type;
                for (BType member : bTupleType.tupleTypes) {
                    if (containsTypeParam(member, resolvedTypes)) {
                        return true;
                    }
                }
                return false;
            case TypeTags.MAP:
                return containsTypeParam(((BMapType) type).constraint, resolvedTypes);
            case TypeTags.STREAM:
                 return containsTypeParam(((BStreamType) type).constraint, resolvedTypes);
            case TypeTags.TABLE:
                return (containsTypeParam(((BTableType) type).constraint, resolvedTypes) ||
                        ((BTableType) type).keyTypeConstraint != null
                                && containsTypeParam(((BTableType) type).keyTypeConstraint, resolvedTypes));
            case TypeTags.RECORD:
                BRecordType recordType = (BRecordType) type;
                for (BField field : recordType.fields.values()) {
                    BType bFieldType = field.getType();
                    if (containsTypeParam(bFieldType, resolvedTypes)) {
                        return true;
                    }
                }
                return false;
            case TypeTags.INVOKABLE:
                BInvokableType invokableType = (BInvokableType) type;
                for (BType paramType : invokableType.paramTypes) {
                    if (containsTypeParam(paramType, resolvedTypes)) {
                        return true;
                    }
                }
                return containsTypeParam(invokableType.retType, resolvedTypes);
            case TypeTags.OBJECT:
                BObjectType objectType = (BObjectType) type;
                for (BField field : objectType.fields.values()) {
                    BType bFieldType = field.getType();
                    if (containsTypeParam(bFieldType, resolvedTypes)) {
                        return true;
                    }
                }
                BObjectTypeSymbol objectTypeSymbol = (BObjectTypeSymbol) objectType.tsymbol;
                for (BAttachedFunction fuc : objectTypeSymbol.attachedFuncs) {
                    if (containsTypeParam(fuc.type, resolvedTypes)) {
                        return true;
                    }
                }
                return false;
            case TypeTags.UNION:
                BUnionType unionType = (BUnionType) type;
                for (BType bType : unionType.getMemberTypes()) {
                    if (containsTypeParam(bType, resolvedTypes)) {
                        return true;
                    }
                }
                return false;
            case TypeTags.ERROR:
                BErrorType errorType = (BErrorType) type;
                return containsTypeParam(errorType.detailType, resolvedTypes);
            case TypeTags.TYPEDESC:
                return containsTypeParam(((BTypedescType) type).constraint, resolvedTypes);
            default:
                return false;
        }
    }

    private BType createBuiltInType(BType type, Name name, long flags) {
        // Handle built-in types.
        switch (type.tag) {
            case TypeTags.INT:
            case TypeTags.BYTE:
            case TypeTags.FLOAT:
            case TypeTags.DECIMAL:
            case TypeTags.STRING:
            case TypeTags.BOOLEAN:
                return new BType(type.tag, null, name, flags);
            case TypeTags.ANY:
                return new BAnyType(type.tag, null, name, flags);
            case TypeTags.ANYDATA:
                BAnydataType anydataType = new BAnydataType((BUnionType) type);
                anydataType.name = name;
                anydataType.flags |= flags;
                return anydataType;
            case TypeTags.READONLY:
                return new BReadonlyType(type.tag, null, name, flags);
        }
        // For others, we will use TSymbol.
        return type;
    }

    private void findTypeParam(Location loc, BType expType, BType actualType, SymbolEnv env,
                               HashSet<BType> resolvedTypes, FindTypeParamResult result) {
        findTypeParam(loc, expType, actualType, env, resolvedTypes, result, false);
    }

    private void findTypeParam(Location loc, BType expType, BType actualType, SymbolEnv env,
                               HashSet<BType> resolvedTypes, FindTypeParamResult result, boolean checkContravariance) {

        if (resolvedTypes.contains(expType)) {
            return;
        }
        resolvedTypes.add(expType);
        // Finding TypePram and its bound type require, both has to be same structure.
        if (isTypeParam(expType)) {
            updateTypeParamAndBoundType(loc, env, expType, actualType);

            // If type param discovered before, now type check with actual type. It has to be matched.

            if (checkContravariance) {
                types.checkType(loc, getMatchingBoundType(expType, env, new HashSet<>()), actualType,
                                DiagnosticErrorCode.INCOMPATIBLE_TYPES);
            } else {
                types.checkType(loc, actualType, getMatchingBoundType(expType, env, new HashSet<>()),
                                DiagnosticErrorCode.INCOMPATIBLE_TYPES);
            }
            return;
        }
        // Bound type is a structure. Visit recursively to find bound type.
        switch (expType.tag) {
            case TypeTags.XML:
                if (!TypeTags.isXMLTypeTag(actualType.tag)) {
                    return;
                }
                switch (actualType.tag) {
                    case TypeTags.XML:
                        BType constraint = ((BXMLType) actualType).constraint;
                        while (constraint.tag == TypeTags.XML) {
                            constraint = ((BXMLType) constraint).constraint;
                        }
                        findTypeParam(loc, ((BXMLType) expType).constraint, constraint, env,
                                resolvedTypes, result);
                        return;
                    case TypeTags.XML_TEXT:
                        findTypeParam(loc, ((BXMLType) expType).constraint, actualType, env,
                                resolvedTypes, result);
                        return;
                    case TypeTags.UNION:
                        findTypeParamInUnion(loc, ((BXMLType) expType).constraint, (BUnionType) actualType, env,
                                resolvedTypes, result);
                        return;
                    default:
                        return;
                }
            case TypeTags.ARRAY:
                if (actualType.tag == TypeTags.ARRAY) {
                    findTypeParam(loc, ((BArrayType) expType).eType, ((BArrayType) actualType).eType, env,
                                  resolvedTypes, result);
                }
                if (actualType.tag == TypeTags.TUPLE) {
                    findTypeParamInTupleForArray(loc, (BArrayType) expType, (BTupleType) actualType, env, resolvedTypes,
                                                 result);
                }
                if (actualType.tag == TypeTags.UNION) {
                    findTypeParamInUnion(loc, ((BArrayType) expType).eType, (BUnionType) actualType, env, resolvedTypes,
                                                 result);
                }
                return;
            case TypeTags.MAP:
                if (actualType.tag == TypeTags.MAP) {
                    findTypeParam(loc, ((BMapType) expType).constraint, ((BMapType) actualType).constraint, env,
                                  resolvedTypes, result);
                }
                if (actualType.tag == TypeTags.RECORD) {
                    findTypeParamInMapForRecord(loc, (BMapType) expType, (BRecordType) actualType, env, resolvedTypes,
                                                result);
                }
                if (actualType.tag == TypeTags.UNION) {
                    findTypeParamInUnion(loc, ((BMapType) expType).constraint, (BUnionType) actualType, env,
                                         resolvedTypes, result);
                }
                return;
            case TypeTags.STREAM:
                if (actualType.tag == TypeTags.STREAM) {
                    findTypeParamInStream(loc, ((BStreamType) expType), ((BStreamType) actualType), env, resolvedTypes,
                                          result);
                }
                if (actualType.tag == TypeTags.UNION) {
                    findTypeParamInStreamForUnion(loc, ((BStreamType) expType), ((BUnionType) actualType), env,
                            resolvedTypes, result);
                }
                return;
            case TypeTags.TABLE:
                if (actualType.tag == TypeTags.TABLE) {
                    findTypeParamInTable(loc, ((BTableType) expType), ((BTableType) actualType), env, resolvedTypes,
                            result);
                }
                return;
            case TypeTags.TUPLE:
                if (actualType.tag == TypeTags.TUPLE) {
                    findTypeParamInTuple(loc, (BTupleType) expType, (BTupleType) actualType, env, resolvedTypes,
                                         result);
                }
                if (actualType.tag == TypeTags.UNION) {
                    findTypeParamInUnion(loc, expType, (BUnionType) actualType, env, resolvedTypes, result);
                }
                return;
            case TypeTags.RECORD:
                if (actualType.tag == TypeTags.RECORD) {
                    findTypeParamInRecord(loc, (BRecordType) expType, (BRecordType) actualType, env, resolvedTypes,
                                          result);
                }
                if (actualType.tag == TypeTags.UNION) {
                    findTypeParamInUnion(loc, expType, (BUnionType) actualType, env, resolvedTypes, result);
                }
                return;
            case TypeTags.INVOKABLE:
                if (actualType.tag == TypeTags.INVOKABLE) {
                    findTypeParamInInvokableType(loc, (BInvokableType) expType, (BInvokableType) actualType, env,
                                                 resolvedTypes, result);
                }
                return;
            case TypeTags.OBJECT:
                if (actualType.tag == TypeTags.OBJECT) {
                    findTypeParamInObject(loc, (BObjectType) expType, (BObjectType) actualType, env, resolvedTypes,
                                          result);
                }
                return;
            case TypeTags.UNION:
                if (actualType.tag == TypeTags.UNION) {
                    findTypeParamInUnion(loc, (BUnionType) expType, (BUnionType) actualType, env, resolvedTypes,
                                         result);
                }
                return;
            case TypeTags.ERROR:
                if (actualType.tag == TypeTags.ERROR) {
                    findTypeParamInError(loc, (BErrorType) expType, (BErrorType) actualType, env, resolvedTypes,
                                         result);
                }
                if (actualType.tag == TypeTags.UNION && types.isSubTypeOfBaseType(actualType, TypeTags.ERROR)) {
                    findTypeParamInError(loc, (BErrorType) expType, symTable.errorType, env, resolvedTypes, result);
                }
                return;
            case TypeTags.TYPEDESC:
                if (actualType.tag == TypeTags.TYPEDESC) {
                    findTypeParam(loc, ((BTypedescType) expType).constraint, ((BTypedescType) actualType).constraint,
                                  env, resolvedTypes, result);
                }
        }
    }

    private void updateTypeParamAndBoundType(Location location, SymbolEnv env, BType typeParamType,
                                             BType boundType) {

        for (SymbolEnv.TypeParamEntry entry : env.typeParamsEntries) {
            if (isSameTypeSymbolNameAndPkg(entry.typeParam.tsymbol, typeParamType.tsymbol)) {
                return;
            }
        }
        if (boundType == symTable.noType) {
            dlog.error(location, DiagnosticErrorCode.CANNOT_INFER_TYPE);
            return;
        }
        env.typeParamsEntries.add(new SymbolEnv.TypeParamEntry(typeParamType, boundType));
    }

    private boolean isSameTypeSymbolNameAndPkg(BTypeSymbol source, BTypeSymbol target) {
        if (source == null || target == null) {
            return false;
        }

        if (source.name.getValue().isEmpty()) {
            return false;
        }

        return source.pkgID.equals(target.pkgID) && source.name.equals(target.name);
    }

    private void findTypeParamInTuple(Location loc, BTupleType expType, BTupleType actualType,
                                      SymbolEnv env, HashSet<BType> resolvedTypes, FindTypeParamResult result) {

        for (int i = 0; i < expType.tupleTypes.size() && i < actualType.tupleTypes.size(); i++) {
            findTypeParam(loc, expType.tupleTypes.get(i), actualType.tupleTypes.get(i), env, resolvedTypes, result);
        }
    }

    private void findTypeParamInStream(Location loc, BStreamType expType, BStreamType actualType,
                                       SymbolEnv env, HashSet<BType> resolvedTypes, FindTypeParamResult result) {
        findTypeParam(loc, expType.constraint, actualType.constraint, env, resolvedTypes, result);
        findTypeParam(loc, expType.error, (actualType.error != null) ? actualType.error : symTable.nilType, env,
                      resolvedTypes, result);
    }

    private void findTypeParamInStreamForUnion(Location loc, BStreamType expType, BUnionType actualType,
                                       SymbolEnv env, HashSet<BType> resolvedTypes, FindTypeParamResult result) {
        LinkedHashSet<BType> constraints = new LinkedHashSet<>();
        LinkedHashSet<BType> errors = new LinkedHashSet<>();
        for (BType type : actualType.getMemberTypes()) {
            if (type.tag == TypeTags.STREAM) {
                constraints.add(((BStreamType) type).constraint);
                if (((BStreamType) type).error != null) {
                    errors.add(((BStreamType) type).error);
                }
            }
        }

        BUnionType cUnionType = BUnionType.create(null, constraints);
        findTypeParam(loc, expType.constraint, cUnionType, env, resolvedTypes, result);
        if (!errors.isEmpty()) {
            BUnionType eUnionType = BUnionType.create(null, errors);
            findTypeParam(loc, expType.error, eUnionType, env, resolvedTypes, result);
        } else {
            findTypeParam(loc, expType.error, symTable.nilType, env, resolvedTypes, result);
        }
    }

    private void findTypeParamInTable(Location loc, BTableType expType, BTableType actualType,
                                      SymbolEnv env, HashSet<BType> resolvedTypes, FindTypeParamResult result) {
        findTypeParam(loc, expType.constraint, actualType.constraint, env, resolvedTypes, result);
        if (expType.keyTypeConstraint != null) {
            if (actualType.keyTypeConstraint != null) {
                findTypeParam(loc, expType.keyTypeConstraint, actualType.keyTypeConstraint, env, resolvedTypes, result);
            } else if (actualType.fieldNameList != null) {
                List<BType> memberTypes = new ArrayList<>();
                actualType.fieldNameList.forEach(field -> memberTypes
                        .add(types.getTableConstraintField(actualType.constraint, field).type));
                if (memberTypes.size() == 1) {
                    findTypeParam(loc, expType.keyTypeConstraint, memberTypes.get(0), env, resolvedTypes, result);
                } else {
                    BTupleType tupleType = new BTupleType(memberTypes);
                    findTypeParam(loc, expType.keyTypeConstraint, tupleType, env, resolvedTypes, result);
                }
            }
        }
    }

    private void findTypeParamInTupleForArray(Location loc, BArrayType expType, BTupleType actualType,
                                              SymbolEnv env, HashSet<BType> resolvedTypes, FindTypeParamResult result) {
        LinkedHashSet<BType> tupleTypes = new LinkedHashSet<>(actualType.tupleTypes);
        if (actualType.restType != null) {
            tupleTypes.add(actualType.restType);
        }
        BUnionType tupleElementType = BUnionType.create(null, tupleTypes);
        findTypeParam(loc, expType.eType, tupleElementType, env, resolvedTypes, result);
    }

    private void findTypeParamInUnion(Location loc, BType expType, BUnionType actualType,
                                      SymbolEnv env, HashSet<BType> resolvedTypes, FindTypeParamResult result) {
        LinkedHashSet<BType> members = new LinkedHashSet<>();
        for (BType type : actualType.getMemberTypes()) {
            if (type.tag == TypeTags.ARRAY) {
                members.add(((BArrayType) type).eType);
            }
            if (type.tag == TypeTags.MAP) {
                members.add(((BMapType) type).constraint);
            }
            if (TypeTags.isXMLTypeTag(type.tag)) {
                if (type.tag == TypeTags.XML) {
                    members.add(((BXMLType) type).constraint);
                }
                members.add(type);
            }
            if (type.tag == TypeTags.RECORD) {
                for (BField field : ((BRecordType) type).fields.values()) {
                    members.add(field.type);
                }
            }
            if (type.tag == TypeTags.TUPLE) {
                members.addAll(((BTupleType) type).getTupleTypes());
            }
        }
        BUnionType tupleElementType = BUnionType.create(null, members);
        findTypeParam(loc, expType, tupleElementType, env, resolvedTypes, result);
    }


    private void findTypeParamInRecord(Location loc, BRecordType expType, BRecordType actualType,
                                       SymbolEnv env, HashSet<BType> resolvedTypes, FindTypeParamResult result) {

        for (BField exField : expType.fields.values()) {
            if (actualType.fields.containsKey(exField.name.value)) {
                findTypeParam(loc, exField.type, actualType.fields.get(exField.name.value).type, env, resolvedTypes,
                              result);
            }
        }
    }

    private void findTypeParamInMapForRecord(Location loc, BMapType expType, BRecordType actualType,
                                             SymbolEnv env, HashSet<BType> resolvedTypes, FindTypeParamResult result) {
        LinkedHashSet<BType> fields = actualType.fields.values().stream().map(f -> f.type)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        LinkedHashSet<BType> reducedTypeSet;
        BType commonFieldType;

        if (actualType.restFieldType != symTable.noType) {
            reducedTypeSet = new LinkedHashSet<>();
            for (BType fType : fields) {
                if (!types.isAssignable(fType, actualType.restFieldType)) {
                    reducedTypeSet.add(fType);
                }
            }
            reducedTypeSet.add(actualType.restFieldType);
        } else {
            // TODO: 7/16/19 Handle cases where there may be multiple field types which are assignable to another
            //  field type: https://github.com/ballerina-platform/ballerina-lang/issues/16824
            reducedTypeSet = fields;
        }

        if (reducedTypeSet.size() == 1) {
            commonFieldType = reducedTypeSet.iterator().next();
        } else {
            commonFieldType = BUnionType.create(null, reducedTypeSet);
        }

        findTypeParam(loc, expType.constraint, commonFieldType, env, resolvedTypes, result);
    }

    private void findTypeParamInInvokableType(Location loc, BInvokableType expType,
                                              BInvokableType actualType, SymbolEnv env, HashSet<BType> resolvedTypes,
                                              FindTypeParamResult result) {

        for (int i = 0; i < expType.paramTypes.size() && i < actualType.paramTypes.size(); i++) {
            findTypeParam(loc, expType.paramTypes.get(i), actualType.paramTypes.get(i), env, resolvedTypes, result,
                          true);
        }
        findTypeParam(loc, expType.retType, actualType.retType, env, resolvedTypes, result);
    }

    private void findTypeParamInObject(Location loc, BObjectType expType, BObjectType actualType,
                                       SymbolEnv env, HashSet<BType> resolvedTypes, FindTypeParamResult result) {

        // Not needed now.
        for (BField exField : expType.fields.values()) {
            if (actualType.fields.containsKey(exField.name.value)) {
                findTypeParam(loc, exField.type, actualType.fields.get(exField.name.value).type, env, resolvedTypes,
                              result);
            }
        }
        List<BAttachedFunction> expAttFunctions = ((BObjectTypeSymbol) expType.tsymbol).attachedFuncs;
        List<BAttachedFunction> actualAttFunctions = ((BObjectTypeSymbol) actualType.tsymbol).attachedFuncs;

        for (BAttachedFunction expFunc : expAttFunctions) {
            BInvokableType actFuncType = actualAttFunctions.stream()
                    .filter(actFunc -> actFunc.funcName.equals(expFunc.funcName))
                    .findFirst()
                    .map(actFunc -> actFunc.type).orElse(null);
            if (actFuncType == null) {
                continue;
            }
            findTypeParamInInvokableType(loc, expFunc.type, actFuncType, env, resolvedTypes, result);
        }
    }

    private void findTypeParamInUnion(Location loc, BUnionType expType, BUnionType actualType,
                                      SymbolEnv env, HashSet<BType> resolvedTypes, FindTypeParamResult result) {
        // Limitation : supports only optional types and depends to given order.
        if ((expType.getMemberTypes().size() != 2) || !expType.isNullable()
                || (actualType.getMemberTypes().size() != 2) || !actualType.isNullable()) {
            return;
        }
        BType exp = expType.getMemberTypes().stream()
                .filter(type -> type != symTable.nilType).findFirst().orElse(symTable.nilType);
        BType act = actualType.getMemberTypes().stream()
                .filter(type -> type != symTable.nilType).findFirst().orElse(symTable.nilType);
        findTypeParam(loc, exp, act, env, resolvedTypes, result);
    }

    private void findTypeParamInError(Location loc, BErrorType expType, BErrorType actualType,
                                      SymbolEnv env, HashSet<BType> resolvedTypes, FindTypeParamResult result) {

        if (expType == symTable.errorType) {
            return;
        }
        findTypeParam(loc, expType.detailType, actualType.detailType, env, resolvedTypes, result);
    }

    private BType getMatchingBoundType(BType expType, SymbolEnv env, HashSet<BType> resolvedTypes) {
        if (isTypeParam(expType)) {
            for (SymbolEnv.TypeParamEntry typeParamEntry : env.typeParamsEntries) {
                if (typeParamEntry.typeParam == expType) {
                    return typeParamEntry.boundType;
                }
            }
            return symTable.noType;
        }

        if (resolvedTypes.contains(expType)) {
            return expType;
        }
        resolvedTypes.add(expType);

        switch (expType.tag) {
            case TypeTags.ARRAY:
                BType elementType = ((BArrayType) expType).eType;
                return new BArrayType(getMatchingBoundType(elementType, env, resolvedTypes));
            case TypeTags.MAP:
                BType constraint = ((BMapType) expType).constraint;
                return new BMapType(TypeTags.MAP, getMatchingBoundType(constraint, env, resolvedTypes),
                        symTable.mapType.tsymbol);
            case TypeTags.STREAM:
                BType streamConstraint = getMatchingBoundType(((BStreamType) expType).constraint, env, resolvedTypes);
                BType streamError = (((BStreamType) expType).error != null) ? getMatchingOptionalBoundType((BUnionType)
                        ((BStreamType) expType).error, env, resolvedTypes) : null;
                return new BStreamType(TypeTags.STREAM, streamConstraint, streamError, symTable.streamType.tsymbol);
            case TypeTags.TABLE:
                BType tableConstraint = getMatchingBoundType(((BTableType) expType).constraint, env, resolvedTypes);
                BTableType tableType = new BTableType(TypeTags.TABLE, tableConstraint, symTable.tableType.tsymbol);
                if (((BTableType) expType).keyTypeConstraint != null) {
                    BType keyTypeConstraint = getMatchingBoundType(((BTableType) expType).keyTypeConstraint, env,
                            resolvedTypes);
                    tableType.keyTypeConstraint = keyTypeConstraint;
                }
                return tableType;
            case TypeTags.TUPLE:
                return getMatchingTupleBoundType((BTupleType) expType, env, resolvedTypes);
            case TypeTags.RECORD:
                return getMatchingRecordBoundType((BRecordType) expType, env, resolvedTypes);
            case TypeTags.INVOKABLE:
                return getMatchingFunctionBoundType((BInvokableType) expType, env, resolvedTypes);
            case TypeTags.OBJECT:
                return getMatchingObjectBoundType((BObjectType) expType, env, resolvedTypes);
            case TypeTags.UNION:
                return getMatchingOptionalBoundType((BUnionType) expType, env, resolvedTypes);
            case TypeTags.ERROR:
                return getMatchingErrorBoundType((BErrorType) expType, env, resolvedTypes);
            case TypeTags.TYPEDESC:
                constraint = ((BTypedescType) expType).constraint;
                return new BTypedescType(getMatchingBoundType(constraint, env, resolvedTypes),
                        symTable.typeDesc.tsymbol);
            default:
                return expType;
        }
    }

    private BTupleType getMatchingTupleBoundType(BTupleType expType, SymbolEnv env, HashSet<BType> resolvedTypes) {

        List<BType> tupleTypes = new ArrayList<>();
        expType.tupleTypes.forEach(type -> tupleTypes.add(getMatchingBoundType(type, env, resolvedTypes)));
        return new BTupleType(tupleTypes);
    }

    private BRecordType getMatchingRecordBoundType(BRecordType expType, SymbolEnv env, HashSet<BType> resolvedTypes) {

        BRecordTypeSymbol expTSymbol = (BRecordTypeSymbol) expType.tsymbol;
        BRecordTypeSymbol recordSymbol = Symbols.createRecordSymbol(expTSymbol.flags, expTSymbol.name,
                                                                    expTSymbol.pkgID, null,
                                                                    expType.tsymbol.scope.owner, expTSymbol.pos,
                                                                    VIRTUAL);
        recordSymbol.scope = new Scope(recordSymbol);
        recordSymbol.initializerFunc = expTSymbol.initializerFunc;

        LinkedHashMap<String, BField> fields = new LinkedHashMap<>();
        for (BField expField : expType.fields.values()) {
            BField field = new BField(expField.name, expField.pos,
                                      new BVarSymbol(0, expField.name, env.enclPkg.packageID,
                                                     getMatchingBoundType(expField.type, env, resolvedTypes),
                                                     env.scope.owner, expField.pos, VIRTUAL));
            fields.put(field.name.value, field);
            recordSymbol.scope.define(expField.name, field.symbol);
        }

        BRecordType bRecordType = new BRecordType(recordSymbol);
        bRecordType.fields = fields;
        recordSymbol.type = bRecordType;

        if (expType.sealed) {
            bRecordType.sealed = true;
        }
        bRecordType.restFieldType = getMatchingBoundType(expType.restFieldType, env, resolvedTypes);

        return bRecordType;
    }

    private BInvokableType getMatchingFunctionBoundType(BInvokableType expType, SymbolEnv env,
                                                        HashSet<BType> resolvedTypes) {

        List<BType> paramTypes = expType.paramTypes.stream()
                .map(type -> getMatchingBoundType(type, env, resolvedTypes))
                .collect(Collectors.toList());
        BType restType = expType.restType;
        var flags = expType.flags;
        BInvokableType invokableType = new BInvokableType(paramTypes, restType,
                                                          getMatchingBoundType(expType.retType, env, resolvedTypes),
                                                          Symbols.createInvokableTypeSymbol(SymTag.FUNCTION_TYPE,
                                                                                            flags,
                                                                                            env.enclPkg.symbol.pkgID,
                                                                                            expType, env.scope.owner,
                                                                                            expType.tsymbol.pos,
                                                                                            VIRTUAL));

        if (Symbols.isFlagOn(flags, Flags.ISOLATED)) {
            invokableType.flags |= Flags.ISOLATED;
        }

        return invokableType;
    }

    private BType getMatchingObjectBoundType(BObjectType expType, SymbolEnv env, HashSet<BType> resolvedTypes) {

        BObjectTypeSymbol actObjectSymbol = Symbols.createObjectSymbol(expType.tsymbol.flags,
                                                                       expType.tsymbol.name, expType.tsymbol.pkgID,
                                                                       null, expType.tsymbol.scope.owner,
                                                                       expType.tsymbol.pos, VIRTUAL);
        BObjectType objectType = new BObjectType(actObjectSymbol);
        actObjectSymbol.type = objectType;
        actObjectSymbol.scope = new Scope(actObjectSymbol);

        for (BField expField : expType.fields.values()) {
            BField field = new BField(expField.name, expField.pos,
                                      new BVarSymbol(expField.symbol.flags, expField.name, env.enclPkg.packageID,
                                                     getMatchingBoundType(expField.type, env, resolvedTypes),
                                                     env.scope.owner, expField.pos, VIRTUAL));
            objectType.fields.put(field.name.value, field);
            objectType.tsymbol.scope.define(expField.name, field.symbol);
        }

        for (BAttachedFunction expFunc : ((BObjectTypeSymbol) expType.tsymbol).attachedFuncs) {
            BInvokableType matchType = getMatchingFunctionBoundType(expFunc.type, env, resolvedTypes);
            BInvokableSymbol invokableSymbol = new BInvokableSymbol(expFunc.symbol.tag, expFunc.symbol.flags,
                                                                    expFunc.symbol.name, env.enclPkg.packageID,
                                                                    matchType, env.scope.owner, expFunc.pos, VIRTUAL);
            invokableSymbol.retType = invokableSymbol.getType().retType;
            matchType.tsymbol = Symbols.createTypeSymbol(SymTag.FUNCTION_TYPE, invokableSymbol.flags, Names.EMPTY,
                                                         env.enclPkg.symbol.pkgID, invokableSymbol.type,
                                                         env.scope.owner, invokableSymbol.pos, VIRTUAL);
            actObjectSymbol.attachedFuncs.add(duplicateAttachFunc(expFunc, matchType, invokableSymbol));
            String funcName = Symbols.getAttachedFuncSymbolName(actObjectSymbol.type.tsymbol.name.value,
                    expFunc.funcName.value);
            actObjectSymbol.scope.define(names.fromString(funcName), invokableSymbol);
        }

        return objectType;
    }

    private BAttachedFunction duplicateAttachFunc(BAttachedFunction expFunc, BInvokableType matchType,
                                                  BInvokableSymbol invokableSymbol) {
        if (expFunc instanceof BResourceFunction) {
            BResourceFunction resourceFunction = (BResourceFunction) expFunc;
            return new BResourceFunction(resourceFunction.funcName, invokableSymbol, matchType,
                    resourceFunction.resourcePath, resourceFunction.accessor, resourceFunction.pathParams,
                    resourceFunction.restPathParam, expFunc.pos);
        }
        return new BAttachedFunction(expFunc.funcName, invokableSymbol, matchType, expFunc.pos);
    }

    private BType getMatchingOptionalBoundType(BUnionType expType, SymbolEnv env, HashSet<BType> resolvedTypes) {
        LinkedHashSet<BType> members = new LinkedHashSet<>();
        expType.getMemberTypes()
                .forEach(type -> members.add(getMatchingBoundType(type, env, resolvedTypes)));
        return BUnionType.create(null, members);
    }

    private BType getMatchingErrorBoundType(BErrorType expType, SymbolEnv env, HashSet<BType> resolvedTypes) {

        if (expType == symTable.errorType) {
            return expType;
        }
        BType detailType = getMatchingBoundType(expType.detailType, env, resolvedTypes);
        BErrorTypeSymbol typeSymbol = new BErrorTypeSymbol(SymTag.ERROR,
                                                           symTable.errorType.tsymbol.flags,
                                                           symTable.errorType.tsymbol.name,
                                                           symTable.errorType.tsymbol.pkgID,
                                                           null, null, symTable.builtinPos, VIRTUAL);
        BErrorType errorType = new BErrorType(typeSymbol, detailType);
        typeSymbol.type = errorType;
        return errorType;
    }

    /**
     * Data holder for FindTypeParamResult.
     *
     * @since jb 1.0.0
     */
    private static class FindTypeParamResult {

        boolean found = false;
        boolean isNew = false;
    }
}
