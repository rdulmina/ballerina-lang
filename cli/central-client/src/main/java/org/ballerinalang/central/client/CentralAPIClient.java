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

package org.ballerinalang.central.client;

import com.google.gson.Gson;
import io.ballerina.projects.ProjectEnvironmentBuilder;
import io.ballerina.projects.balo.BaloProject;
import io.ballerina.projects.repos.TempDirCompilationCache;
import me.tongfei.progressbar.ProgressBar;
import me.tongfei.progressbar.ProgressBarStyle;
import org.ballerinalang.central.client.model.Error;
import org.ballerinalang.central.client.model.Package;
import org.ballerinalang.central.client.model.PackageSearchResult;
import org.ballerinalang.toml.model.Settings;
import org.wso2.ballerinalang.util.RepoUtils;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.stream.Collectors;

import static org.ballerinalang.central.client.CentralClientConstants.ACCEPT;
import static org.ballerinalang.central.client.CentralClientConstants.ACCEPT_ENCODING;
import static org.ballerinalang.central.client.CentralClientConstants.APPLICATION_OCTET_STREAM;
import static org.ballerinalang.central.client.CentralClientConstants.AUTHORIZATION;
import static org.ballerinalang.central.client.CentralClientConstants.BALLERINA_PLATFORM;
import static org.ballerinalang.central.client.CentralClientConstants.CONTENT_DISPOSITION;
import static org.ballerinalang.central.client.CentralClientConstants.CONTENT_TYPE;
import static org.ballerinalang.central.client.CentralClientConstants.IDENTITY;
import static org.ballerinalang.central.client.CentralClientConstants.LOCATION;
import static org.ballerinalang.central.client.CentralClientConstants.USER_AGENT;
import static org.ballerinalang.central.client.Utils.authenticate;
import static org.ballerinalang.central.client.Utils.convertToUrl;
import static org.ballerinalang.central.client.Utils.createBaloInHomeRepo;
import static org.ballerinalang.central.client.Utils.getBallerinaCentralCliTokenUrl;
import static org.ballerinalang.central.client.Utils.getStatusCode;
import static org.ballerinalang.central.client.Utils.getTotalFileSizeInKB;
import static org.ballerinalang.central.client.Utils.initializeProxy;
import static org.ballerinalang.central.client.Utils.initializeSsl;
import static org.ballerinalang.central.client.Utils.readSettings;
import static org.ballerinalang.central.client.Utils.setRequestMethod;
import static org.ballerinalang.tool.LauncherUtils.createLauncherException;
import static org.wso2.ballerinalang.compiler.util.ProjectDirConstants.SETTINGS_FILE_NAME;
import static org.wso2.ballerinalang.util.RepoUtils.getRemoteRepoURL;

/**
 * {@code CentralAPIClient} is a client for the Central API.
 *
 * @since 2.0.0
 */
public class CentralAPIClient {

    private Proxy proxy;
    protected Settings settings;
    private String baseUrl;
    protected PrintStream errStream;
    protected PrintStream outStream;
    private static final String PACKAGES = "packages";
    private static final String ERR_CANNOT_CONNECT = "error: could not connect to remote repository to find package: ";
    private static final String ERR_CANNOT_PUSH = "error: failed to push the package: ";

    public CentralAPIClient() {
        this.errStream = System.err;
        this.outStream = System.out;
        this.settings = readSettings();
        this.baseUrl = getRemoteRepoURL();
        this.proxy = initializeProxy(this.settings.getProxy());
    }

    /**
     * Get package with version.
     *
     * @param orgNamePath     The organization name of the package. (required)
     * @param packageNamePath The name of the package. (required)
     * @param version         The version or version range of the module. (required)
     * @return PackageJsonSchema
     */
    public Package getPackage(String orgNamePath, String packageNamePath, String version, String supportedPlatform) {
        initializeSsl();
        String url = PACKAGES + "/" + orgNamePath + "/" + packageNamePath;
        // append version to url if available
        if (null != version && !version.isEmpty()) {
            url = url + "/" + version;
        }

        String pkg = orgNamePath + "/" + packageNamePath + ":" + version;
        HttpURLConnection conn = createHttpUrlConnection(url);
        conn.setInstanceFollowRedirects(false);
        setRequestMethod(conn, Utils.RequestMethod.GET);

        // set implementation version
        conn.setRequestProperty(BALLERINA_PLATFORM, supportedPlatform);

        // status code and meaning
        //// 302 - module found
        //// 404 - module not found
        //// 400 - bad request sent
        //// 500 - backend is broken
        try {
            int statusCode = getStatusCode(conn);
            if (statusCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), Charset.defaultCharset()))) {
                    return new Gson().fromJson(reader, Package.class);
                } catch (IOException e) {
                    throw ErrorUtil.createCentralClientException(e.getMessage());
                }
            } else if (statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), Charset.defaultCharset()))) {
                    Error errorJsonSchema = new Gson().fromJson(reader, Error.class);
                    if (errorJsonSchema.getMessage().contains("package not found for:")) {
                        throw new NoPackageException(errorJsonSchema.getMessage());
                    } else {
                        throw createLauncherException(
                                ERR_CANNOT_CONNECT + pkg + ". reason: " + errorJsonSchema.getMessage());
                    }
                } catch (IOException e) {
                    throw ErrorUtil.createCentralClientException(e.getMessage());
                }
            } else if (statusCode == HttpURLConnection.HTTP_BAD_REQUEST) {
                try (BufferedReader errorStream = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), Charset.defaultCharset()))) {
                    Error errorJsonSchema = new Gson().fromJson(errorStream, Error.class);

                    if (errorJsonSchema.getMessage() != null && !"".equals(errorJsonSchema.getMessage())) {
                        throw new CentralClientException(errorJsonSchema.getMessage());
                    } else {
                        throw createLauncherException(ERR_CANNOT_CONNECT + pkg + ". reason:" + errorStream.lines()
                                .collect(Collectors.joining("\n")));
                    }
                } catch (IOException e) {
                    throw ErrorUtil.createCentralClientException(e.getMessage());
                }
            } else {
                throw createLauncherException(ERR_CANNOT_CONNECT + pkg + ".");
            }
        } finally {
            conn.disconnect();
            Authenticator.setDefault(null);
        }
    }

    /**
     * Pushing a package to registry.
     */
    public void pushPackage(Path baloPath) {
        final int noOfBytes = 64;
        final int bufferSize = 1024 * noOfBytes;

        initializeSsl();
        HttpURLConnection conn = createHttpUrlConnection(PACKAGES);
        conn.setInstanceFollowRedirects(false);
        setRequestMethod(conn, Utils.RequestMethod.POST);

        // Load balo project
        ProjectEnvironmentBuilder defaultBuilder = ProjectEnvironmentBuilder.getDefaultBuilder();
        defaultBuilder.addCompilationCacheFactory(TempDirCompilationCache::from);
        BaloProject baloProject = BaloProject.loadProject(defaultBuilder, baloPath);
        String org = baloProject.currentPackage().manifest().org().toString();
        String name = baloProject.currentPackage().manifest().name().toString();
        String version = baloProject.currentPackage().manifest().version().toString();

        // Get access token
        String ballerinaCentralCliTokenUrl = getBallerinaCentralCliTokenUrl();
        Path ballerinaHomePath = RepoUtils.createAndGetHomeReposPath();
        Path settingsTomlFilePath = ballerinaHomePath.resolve(SETTINGS_FILE_NAME);
        String accessToken = authenticate(errStream, ballerinaCentralCliTokenUrl, this.settings, settingsTomlFilePath);

        // Set headers
        conn.setRequestProperty(AUTHORIZATION, "Bearer " + accessToken);
        conn.setRequestProperty(CONTENT_TYPE, APPLICATION_OCTET_STREAM);

        conn.setDoOutput(true);
        conn.setChunkedStreamingMode(bufferSize);

        try (DataOutputStream outputStream = new DataOutputStream(conn.getOutputStream())) {
            // Send balo content by 1 kb chunks
            byte[] buffer = new byte[bufferSize];
            int count;
            try (ProgressBar progressBar = new ProgressBar(
                    org + "/" + name + ":" + version + " [project repo -> central]", getTotalFileSizeInKB(baloPath),
                    1000, outStream, ProgressBarStyle.ASCII, " KB", 1);
                    FileInputStream fis = new FileInputStream(baloPath.toFile())) {
                while ((count = fis.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, count);
                    outputStream.flush();
                    progressBar.stepBy((long) noOfBytes);
                }
            }
        } catch (IOException e) {
            throw ErrorUtil
                    .createCentralClientException("error occurred while uploading balo to central: " + e.getMessage());
        }

        try {
            int statusCode = getStatusCode(conn);
            // 200 - Module pushed successfully
            // Other - Error occurred, json returned with the error message
            if (statusCode == HttpURLConnection.HTTP_NO_CONTENT) {
                outStream.println(org + "/" + name + ":" + version + " pushed to central successfully");
            } else if (statusCode == HttpURLConnection.HTTP_UNAUTHORIZED) {
                errStream.println("unauthorized access token for organization: " + org);
            } else if (statusCode == HttpURLConnection.HTTP_BAD_REQUEST) {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), Charset.defaultCharset()))) {
                    Error errorJsonSchema = new Gson().fromJson(reader, Error.class);

                    if (errorJsonSchema.getMessage() != null && !"".equals(errorJsonSchema.getMessage())) {
                        throw ErrorUtil.createCentralClientException(errorJsonSchema.getMessage());
                    } else {
                        throw ErrorUtil.createCentralClientException(
                                ERR_CANNOT_PUSH + "'" + org + "/" + name + ":" + version + "' reason:" + reader.lines()
                                        .collect(Collectors.joining("\n")));
                    }
                } catch (IOException e) {
                    throw ErrorUtil.createCentralClientException(
                            ERR_CANNOT_PUSH + "'" + org + "/" + name + ":" + version + "' to the remote repository '"
                                    + conn.getURL() + "'");
                }
            } else {
                throw ErrorUtil.createCentralClientException(
                        ERR_CANNOT_PUSH + "'" + org + "/" + name + ":" + version + "' to the remote repository '" + conn
                                .getURL() + "'");
            }
        } finally {
            conn.disconnect();
            Authenticator.setDefault(null);
        }
    }

    public void pullPackage(String org, String name, String version, Path packagePathInBaloCache,
            String supportedPlatform, boolean isBuild) {
        LogFormatter logFormatter = new LogFormatter();
        if (isBuild) {
            logFormatter = new BuildLogFormatter();
        }

        String url = PACKAGES + "/" + org + "/" + name;
        // append version to url if available
        if (null != version && !version.isEmpty()) {
            url += "/" + version;
        } else {
            url += "/*";
        }

        initializeSsl();
        HttpURLConnection conn = createHttpUrlConnection(url);
        conn.setInstanceFollowRedirects(false);
        setRequestMethod(conn, Utils.RequestMethod.GET);

        // Set headers
        conn.setRequestProperty(BALLERINA_PLATFORM, supportedPlatform);
        conn.setRequestProperty(ACCEPT_ENCODING, IDENTITY);
        conn.setRequestProperty(USER_AGENT, RepoUtils.getBallerinaVersion());
        conn.setRequestProperty(ACCEPT, APPLICATION_OCTET_STREAM);

        try {
            // 302   - Package is found
            // Other - Error occurred, json returned with the error message
            if (getStatusCode(conn) == HttpURLConnection.HTTP_MOVED_TEMP) {
                // get redirect url from "location" header field
                String newUrl = conn.getHeaderField(LOCATION);
                String contentDisposition = conn.getHeaderField(CONTENT_DISPOSITION);

                // create connection
                if (this.proxy == null) {
                    conn = (HttpURLConnection) convertToUrl(newUrl).openConnection();
                } else {
                    conn = (HttpURLConnection) convertToUrl(newUrl).openConnection(this.proxy);
                }

                conn.setRequestProperty(CONTENT_DISPOSITION, contentDisposition);

                boolean isNightlyBuild = RepoUtils.getBallerinaVersion().contains("SNAPSHOT");
                createBaloInHomeRepo(conn, packagePathInBaloCache, org + "/" + name, isNightlyBuild, newUrl,
                        contentDisposition, outStream, logFormatter);
            } else {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), Charset.defaultCharset()))) {
                    Error errorJsonSchema = new Gson().fromJson(reader, Error.class);
                    throw ErrorUtil.createCentralClientException(
                            logFormatter.formatLog("error: " + errorJsonSchema.getMessage()));
                } catch (IOException e) {
                    throw ErrorUtil.createCentralClientException(logFormatter.formatLog(
                            "failed to pull the package '" + org + "/" + name + "' from the remote repository '" + url
                                    + "'"));
                }
            }
        } catch (IOException e) {
            throw ErrorUtil.createCentralClientException(e.getMessage());
        } finally {
            conn.disconnect();
            Authenticator.setDefault(null);
        }
    }

    /**
     * Search packages in registry.
     */
    public PackageSearchResult searchPackage(String query) {
        initializeSsl();
        HttpURLConnection conn = createHttpUrlConnection(PACKAGES + "/?q=" + query);
        conn.setInstanceFollowRedirects(false);
        setRequestMethod(conn, Utils.RequestMethod.GET);

        // Handle response
        int statusCode = getStatusCode(conn);
        try {
            // 200 - modules found
            // Other - Error occurred, json returned with the error message
            if (statusCode == HttpURLConnection.HTTP_OK) {

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), Charset.defaultCharset()))) {
                    return new Gson().fromJson(reader, PackageSearchResult.class);
                } catch (IOException e) {
                    throw ErrorUtil.createCentralClientException(e.getMessage());
                }
            } else {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getErrorStream(), Charset.defaultCharset()))) {
                    Error errorJsonSchema = new Gson().fromJson(reader, Error.class);

                    if (errorJsonSchema.getMessage() != null && !"".equals(errorJsonSchema.getMessage())) {
                        throw ErrorUtil.createCentralClientException(errorJsonSchema.getMessage());
                    } else {
                        throw ErrorUtil.createCentralClientException(reader.lines().collect(Collectors.joining("\n")));
                    }
                } catch (IOException e) {
                    throw ErrorUtil.createCentralClientException(e.getMessage());
                }
            }
        } finally {
            conn.disconnect();
            Authenticator.setDefault(null);
        }
    }

    /**
     * Create http URL connection.
     *
     * @param paths resource paths
     * @return http URL connection
     */
    protected HttpURLConnection createHttpUrlConnection(String paths) {
        URL url = convertToUrl(this.baseUrl + "/" + paths);
        try {
            // set proxy if exists.
            if (this.proxy == null) {
                return (HttpURLConnection) url.openConnection();
            } else {
                return (HttpURLConnection) url.openConnection(this.proxy);
            }
        } catch (IOException e) {
            throw ErrorUtil
                    .createCentralClientException("Creating connection to '" + url + "' failed:" + e.getMessage());
        }
    }
}
