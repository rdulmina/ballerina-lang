/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package io.ballerina.projects;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * {@code Module} represents a Ballerina module.
 *
 * @since 2.0.0
 */
public class Module {
    private final ModuleContext moduleContext;
    private final Package packageInstance;
    private final Map<DocumentId, Document> srcDocs;
    private final Map<DocumentId, Document> testSrcDocs;
    private final Function<DocumentId, Document> populateDocumentFunc;

    private Optional<ModuleMd> moduleMd = null;

    Module(ModuleContext moduleContext, Package packageInstance) {
        this.moduleContext = moduleContext;
        this.packageInstance = packageInstance;

        this.srcDocs = new ConcurrentHashMap<>();
        this.testSrcDocs = new ConcurrentHashMap<>();
        this.populateDocumentFunc = documentId -> new Document(
                this.moduleContext.documentContext(documentId), this);
    }

    static Module from(ModuleContext moduleContext, Package packageInstance) {
        return new Module(moduleContext, packageInstance);
    }

    public Package packageInstance() {
        return this.packageInstance;
    }

    public ModuleId moduleId() {
        return this.moduleContext.moduleId();
    }

    public ModuleName moduleName() {
        return this.moduleContext.moduleName();
    }

    public ModuleDescriptor descriptor() {
        return moduleContext.descriptor();
    }

    public Collection<DocumentId> documentIds() {
        return this.moduleContext.srcDocumentIds();
    }

    public Collection<DocumentId> testDocumentIds() {
        return this.moduleContext.testSrcDocumentIds();
    }

    public Document document(DocumentId documentId) {
        // TODO Should we throw an error if the moduleId is not present
        if (documentIds().contains(documentId)) {
            return this.srcDocs.computeIfAbsent(documentId, this.populateDocumentFunc);
        } else {
            return this.testSrcDocs.computeIfAbsent(documentId, this.populateDocumentFunc);
        }
    }

    public ModuleCompilation getCompilation() {
        return this.packageInstance.packageContext().getModuleCompilation(this.moduleContext);
    }

    public Collection<ModuleDependency> moduleDependencies() {
        return moduleContext.dependencies();
    }

    public boolean isDefaultModule() {
        return moduleContext.isDefaultModule();
    }

    public Project project() {
        return this.moduleContext.project();
    }

    /** Returns an instance of the Module.Modifier.
     *
     * @return module modifier
     */
    public Modifier modify() {
        return new Modifier(this);
    }

    ModuleContext moduleContext() {
        return moduleContext;
    }

    public Optional<ModuleMd> moduleMd() {
        if (null == this.moduleMd) {
            this.moduleMd = this.moduleContext.moduleMdContext().map(c ->
                    ModuleMd.from(c, this)
            );
        }
        return this.moduleMd;
    }

    private static class DocumentIterable implements Iterable {
        private final Collection<Document> documentList;

        public DocumentIterable(Collection<Document> documentList) {
            this.documentList = documentList;
        }

        @Override
        public Iterator<Document> iterator() {
            return this.documentList.iterator();
        }
    
        @Override
        public Spliterator spliterator() {
            return this.documentList.spliterator();
        }
    }

    /**
     * Inner class that handles module modifications.
     */
    public static class Modifier {
        private ModuleId moduleId;
        private ModuleDescriptor moduleDescriptor;
        private Map<DocumentId, DocumentContext> srcDocContextMap;
        private Map<DocumentId, DocumentContext> testDocContextMap;
        private boolean isDefaultModule;
        private final List<ModuleDescriptor> dependencies;
        private Package packageInstance;
        private Project project;
        private MdDocumentContext moduleMdContext;


        private Modifier(Module oldModule) {
            moduleId = oldModule.moduleId();
            moduleDescriptor = oldModule.descriptor();
            srcDocContextMap = copySrcDocs(oldModule);
            testDocContextMap = copyTestDocs(oldModule);
            isDefaultModule = oldModule.isDefaultModule();
            dependencies = oldModule.moduleContext().moduleDescDependencies();
            packageInstance = oldModule.packageInstance;
            project = oldModule.project();
            moduleMdContext = oldModule.moduleContext.moduleMdContext().orElse(null);
        }

        Modifier updateDocument(DocumentContext newDocContext) {
            if (this.srcDocContextMap.containsKey(newDocContext.documentId())) {
                this.srcDocContextMap.put(newDocContext.documentId(), newDocContext);
            } else {
                this.testDocContextMap.put(newDocContext.documentId(), newDocContext);
            }
            return this;
        }

        /**
         * Creates a copy of the existing module and adds a new source document to the new module.
         *
         * @param documentConfig configurations to create the document
         * @return an instance of the Module.Modifier
         */
        public Modifier addDocument(DocumentConfig documentConfig) {
            DocumentContext newDocumentContext = DocumentContext.from(documentConfig);
            this.srcDocContextMap.put(newDocumentContext.documentId(), newDocumentContext);
            return this;
        }

        /**
         * Creates a copy of the existing module and adds a new test document to the new module.
         *
         * @param documentConfig configurations to create the document
         * @return an instance of the Module.Modifier
         */
        public Modifier addTestDocument(DocumentConfig documentConfig) {
            DocumentContext newDocumentContext = DocumentContext.from(documentConfig);
            this.testDocContextMap.put(newDocumentContext.documentId(), newDocumentContext);
            return this;
        }

        /**
         * Creates a copy of the existing module and removes the specified document from the new module.
         *
         * @param documentId documentId of the document to remove
         * @return an instance of the Module.Modifier
         */
        public Modifier removeDocument(DocumentId documentId) {

            if (this.srcDocContextMap.containsKey(documentId)) {
                srcDocContextMap.remove(documentId);
            } else {
                testDocContextMap.remove(documentId);
            }
            return this;
        }

        /**
         * Creates a copy of the existing module and removes the Module.md from the new module.
         *
         * @return an instance of the Module.Modifier
         */
        public Modifier removeModuleMd() {
            moduleMdContext = null;
            return this;
        }

        /**
         * Returns the updated module created by a document add/remove/update operation.
         *
         * @return the updated module
         */
        public Module apply() {
            return createNewModule(this.srcDocContextMap, this.testDocContextMap);
        }

        private Map<DocumentId, DocumentContext> copySrcDocs(Module oldModule) {
            Map<DocumentId, DocumentContext> srcDocContextMap = new HashMap<>();
            for (DocumentId documentId : oldModule.moduleContext.srcDocumentIds()) {
                srcDocContextMap.put(documentId, oldModule.moduleContext.documentContext(documentId));
            }
            return srcDocContextMap;
        }

        private Map<DocumentId, DocumentContext> copyTestDocs(Module oldModule) {
            Map<DocumentId, DocumentContext> testDocContextMap = new HashMap<>();
            for (DocumentId documentId : oldModule.moduleContext.testSrcDocumentIds()) {
                testDocContextMap.put(documentId, oldModule.moduleContext.documentContext(documentId));
            }
            return testDocContextMap;
        }

        private Module createNewModule(Map<DocumentId, DocumentContext> srcDocContextMap, Map<DocumentId,
                DocumentContext> testDocContextMap) {
            Set<ModuleContext> moduleContextSet = new HashSet<>();
            ModuleContext newModuleContext = new ModuleContext(this.project,
                    this.moduleId, this.moduleDescriptor, this.isDefaultModule, srcDocContextMap,
                    testDocContextMap, this.moduleMdContext, this.dependencies);
            moduleContextSet.add(newModuleContext);

            // add dependant modules including transitives
            Collection<ModuleId> dependants = getAllDependants(this.moduleId);
            for (ModuleId dependantId : dependants) {
                if (dependantId.equals(this.moduleId)) {
                    continue;
                }
                Modifier module = this.packageInstance.module(dependantId).modify();
                moduleContextSet.add(new ModuleContext(this.project,
                        dependantId, module.moduleDescriptor, module.isDefaultModule, module.srcDocContextMap,
                        module.testDocContextMap, module.moduleMdContext, module.dependencies));
            }

            Package newPackage = this.packageInstance.modify().updateModules(moduleContextSet).apply();
            return newPackage.module(this.moduleId);
        }

        Modifier updateModuleMd(MdDocumentContext moduleMd) {
            this.moduleMdContext = moduleMd;
            return this;
        }

        private Collection<ModuleId> getAllDependants(ModuleId updatedModuleId) {
            packageInstance.getResolution(); // this will build the dependency graph if it is not built yet
            return getAllDependants(updatedModuleId, new HashSet<>(), new HashSet<>());
        }

        private Collection<ModuleId> getAllDependants(
                ModuleId updatedModuleId, HashSet<ModuleId> visited, HashSet<ModuleId> dependants) {
            if (!visited.contains(updatedModuleId)) {
                visited.add(updatedModuleId);
                Collection<ModuleId> directDependents = this.project.currentPackage()
                        .moduleDependencyGraph().getDirectDependents(updatedModuleId);
                if (directDependents.size() > 0) {
                    dependants.addAll(directDependents);
                    for (ModuleId directDependent : directDependents) {
                        getAllDependants(directDependent, visited, dependants);
                    }

                }
            }

            return dependants;
        }
    }
}
