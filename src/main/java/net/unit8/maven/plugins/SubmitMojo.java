package net.unit8.maven.plugins;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClientConfig;
import com.ning.http.client.Response;
import com.ning.http.client.providers.netty.NettyAsyncHttpProviderConfig;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Submit test to TestStreamer.
 *
 * @author kawasima
 */
@Mojo(name = "submit", defaultPhase = LifecyclePhase.TEST, threadSafe = true,
        requiresDependencyResolution = ResolutionScope.TEST)
public class SubmitMojo extends AbstractMojo {
    @Parameter(defaultValue = "${project.build.directory}/surefire-reports")
    protected File reportsDirectory;

    @Parameter
    protected List<String> includes;

    @Parameter(property = "project.build.testSourceDirectory", required = true)
    protected File testSourceDirectory;

    @Parameter(property = "project.build.testOutputDirectory")
    protected File testClassesDirectory;

    /** Set this to "true" to bypass unit tests entirely. */
    @Parameter(property = "maven.test.skip", defaultValue = "false")
    protected boolean skip;

    /** Location of the file. */
    @Parameter(property = "project.build.outputDirectory", required = true)
    protected File outputDirectory;

    @Parameter(required = true)
    protected String testStreamerUrl;

    /**
     *
     * @throws MojoExecutionException
     */
    public void execute()
        throws MojoExecutionException {

        if (skip) {
            getLog().info("Tests are skipped.");
            return;
        }

        if (includes == null || includes.isEmpty()) {
            // Set default include patterns.
            includes = Arrays.asList("**/Test*.java", "**/*Test.java", "**/*TestCase.java");
        }

        try {
            NettyAsyncHttpProviderConfig providerConfig = new NettyAsyncHttpProviderConfig();
            providerConfig.addProperty(NettyAsyncHttpProviderConfig.HTTP_CLIENT_CODEC_MAX_HEADER_SIZE, 8192 * 4);
            AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder()
                    .setAsyncHttpClientProviderConfig(providerConfig)
                    .build();
            AsyncHttpClient client = new AsyncHttpClient(config);
            Map<String, Collection<String>> parameters = new HashMap<String, Collection<String>>();
            parameters.put("include", includes);
            List<URL> classpaths = getClasspaths();
            if (testClassesDirectory != null)
                classpaths.add(0, testClassesDirectory.toURI().toURL());

            if (outputDirectory != null)
                classpaths.add(0, outputDirectory.toURI().toURL());
            Collection<String> cp = new ArrayList<String>(classpaths.size());
            for (URL u : classpaths)
                cp.add(u.toString());

            parameters.put("cp", cp);

            Future<Integer> f = client.preparePost(testStreamerUrl)
                    .setParameters(parameters)
                    .execute(new AsyncCompletionHandler<Integer>() {
                        @Override
                        public Integer onCompleted(Response response) throws Exception {
                            return response.getStatusCode();
                        }
                    });

            Integer status = f.get(5000, TimeUnit.MILLISECONDS);
            if (status == HttpResponseStatus.CREATED.getCode()) {
                getLog().info("Successful for submitting tests.");
            } else if (status == HttpResponseStatus.NOT_FOUND.getCode()) {
                getLog().info("No tests.");
            } else {
                throw new MojoExecutionException("Not success");
            }
        } catch (Exception ex) {
            if (ex instanceof MojoExecutionException) {
                throw (MojoExecutionException) ex;
            } else {
                throw new MojoExecutionException(ex.getMessage(), ex);
            }
        }
    }

    protected List<URL> getClasspaths() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl instanceof URLClassLoader) {
            return new ArrayList<URL>(
                    Arrays.asList(((URLClassLoader) cl).getURLs()));
        } else {
            return Collections.EMPTY_LIST;
        }
    }
}
