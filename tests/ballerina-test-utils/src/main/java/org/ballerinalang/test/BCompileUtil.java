/*
 * Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.test;

import io.ballerina.projects.JBallerinaBackend;
import io.ballerina.projects.JdkVersion;
import io.ballerina.projects.Package;
import io.ballerina.projects.PackageCompilation;
import io.ballerina.projects.Project;
import io.ballerina.projects.ProjectEnvironmentBuilder;
import io.ballerina.projects.directory.ProjectLoader;
import io.ballerina.projects.directory.SingleFileProject;
import io.ballerina.projects.environment.EnvironmentBuilder;
import io.ballerina.projects.repos.FileSystemCache;
import io.ballerina.projects.util.ProjectUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static io.ballerina.projects.util.ProjectConstants.DIST_CACHE_DIRECTORY;

/**
 * Helper to drive test source compilation.
 *
 * @since 2.0.0
 */
public class BCompileUtil {

    private static final Path testSourcesDirectory = Paths.get("src/test/resources").toAbsolutePath().normalize();
    private static final Path testBuildDirectory = Paths.get("build").toAbsolutePath().normalize();
    public static final String TEST_COMPILATION_CACHE_DIR_NAME = "test_compilation_cache";

    public static Project loadProject(String sourceFilePath) {
        Path sourcePath = Paths.get(sourceFilePath);
        String sourceFileName = sourcePath.getFileName().toString();
        Path sourceRoot = testSourcesDirectory.resolve(sourcePath.getParent());

        Path projectPath = Paths.get(sourceRoot.toString(), sourceFileName);
        return ProjectLoader.loadProject(projectPath);
    }

    public static CompileResult compile(String sourceFilePath) {
        Project project = loadProject(sourceFilePath);

        Package currentPackage = project.currentPackage();
        PackageCompilation packageCompilation = currentPackage.getCompilation();
        JBallerinaBackend jBallerinaBackend = JBallerinaBackend.from(packageCompilation, JdkVersion.JAVA_11);
        if (jBallerinaBackend.diagnosticResult().hasErrors()) {
            return new CompileResult(currentPackage, jBallerinaBackend);
        }

        CompileResult compileResult = new CompileResult(currentPackage, jBallerinaBackend);
        invokeModuleInit(compileResult);
        return compileResult;
    }

    public static Project getProject(String sourceFilePath) {
        Path sourcePath = Paths.get(sourceFilePath);
        String sourceFileName = sourcePath.getFileName().toString();
        Path sourceRoot = testSourcesDirectory.resolve(sourcePath.getParent());

        Path projectPath = Paths.get(sourceRoot.toString(), sourceFileName);
        return ProjectLoader.loadProject(projectPath);
    }

    public static CompileResult compileAndCacheBalo(String sourceFilePath) {
        Path sourcePath = Paths.get(sourceFilePath);
        String sourceFileName = sourcePath.getFileName().toString();
        Path sourceRoot = testSourcesDirectory.resolve(sourcePath.getParent());

        Path projectPath = Paths.get(sourceRoot.toString(), sourceFileName);
        Project project = ProjectLoader.loadProject(projectPath, projectEnvironmentBuilder());

        if (isSingleFileProject(project)) {
            throw new RuntimeException("single file project is given for compilation at " + project.sourceRoot());
        }

        Package currentPackage = project.currentPackage();
        PackageCompilation packageCompilation = currentPackage.getCompilation();
        JBallerinaBackend jBallerinaBackend = JBallerinaBackend.from(packageCompilation, JdkVersion.JAVA_11);
        if (jBallerinaBackend.diagnosticResult().hasErrors()) {
            return new CompileResult(currentPackage, jBallerinaBackend);
        }

        Path baloCachePath = baloCachePath(currentPackage);
        jBallerinaBackend.emit(JBallerinaBackend.OutputType.BALO, baloCachePath);

        CompileResult compileResult = new CompileResult(currentPackage, jBallerinaBackend);
        invokeModuleInit(compileResult);
        return compileResult;
    }

    private static void invokeModuleInit(CompileResult compileResult) {
        if (compileResult.getDiagnostics().length > 0) {
            return;
        }

        try {
            BRunUtil.runInit(compileResult);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("error while invoking init method of " + compileResult.projectSourceRoot(), e);
        }
    }

    private static boolean isSingleFileProject(Project project) {
        return project instanceof SingleFileProject;
    }

    private static ProjectEnvironmentBuilder projectEnvironmentBuilder() {
        ProjectEnvironmentBuilder environmentBuilder = ProjectEnvironmentBuilder.getBuilder(
                EnvironmentBuilder.buildDefault());
        return environmentBuilder.addCompilationCacheFactory(TestCompilationCache::from);
    }

    private static Path baloCachePath(Package pkg) {
        try {
            Path distributionCache = testBuildDirectory.resolve(DIST_CACHE_DIRECTORY);
            Path balos = distributionCache.resolve("balo")
                    .resolve(pkg.packageOrg().toString())
                    .resolve(pkg.packageName().value())
                    .resolve(pkg.packageVersion().value().toString());
            Files.createDirectories(balos);

            String baloName = ProjectUtils.getBaloName(pkg.manifest());
            return balos.resolve(baloName);
        } catch (IOException e) {
            throw new RuntimeException("error while creating the balo distribution cache directory at " +
                    testBuildDirectory, e);
        }
    }

    /**
     * Compilation cache that dumps bir and jars inside the build directory.
     */
    private static class TestCompilationCache extends FileSystemCache {

        private TestCompilationCache(Project project, Path cacheDirPath) {
            super(project, cacheDirPath);
        }

        private static TestCompilationCache from(Project project) {
            Path testCompilationCachePath = testBuildDirectory.resolve(TEST_COMPILATION_CACHE_DIR_NAME).
                    resolve(UUID.randomUUID().toString());
            return new TestCompilationCache(project, testCompilationCachePath);
        }
    }
}
