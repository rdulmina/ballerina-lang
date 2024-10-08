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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.ballerinalang.debugger.test.utils;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

/**
 * Util class for file operations.
 */
public final class FileUtils {

    static final String URI_SCHEME_BALA = "bala";
    static final String URI_SCHEME_FILE = "file";
    static final String URI_SEPARATOR = "/";
    static final String FILE_SEPARATOR = File.separator;
    static final String FILE_SEPARATOR_REGEX = File.separatorChar == '\\' ? "\\\\" : File.separator;

    private FileUtils() {
    }

    /**
     * Recursively copy a directory from a source to destination.
     *
     * @param src  source path
     * @param dest destination path
     * @throws IOException thrown when as error occurs when copying from src to dest
     */
    public static void copyFolder(Path src, Path dest) throws IOException {
        try (Stream<Path> paths = Files.walk(src)) {
            paths.forEach(source -> copy(source, dest.resolve(src.relativize(source))));
        }
    }

    private static void copy(Path source, Path dest) {
        try {
            Files.copy(source, dest, REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    /**
     * Closes the given Closeable and swallows any IOException that may occur.
     *
     * @param c Closeable to close, can be null
     */
    public static void closeQuietly(final Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (final IOException ignored) {
            }
        }
    }
}
