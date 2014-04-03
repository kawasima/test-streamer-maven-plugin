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

import com.ning.http.client.*;
import com.ning.http.client.providers.netty.NettyAsyncHttpProviderConfig;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;

/**
 * Submit test to TestStreamer.
 *
 * @author kawasima
 */
@Mojo(name = "submit", defaultPhase = LifecyclePhase.TEST, threadSafe = true,
        requiresDependencyResolution = ResolutionScope.TEST)
public class SubmitMojo extends AbstractMojo {
    @Component
    protected MavenProject project;

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

    /**
     * Set this to "true" to ignore a failure during testing. Its use is NOT RECOMMENDED, but quite convenient on
     * occasion.
     */
    @Parameter( property = "maven.test.failure.ignore", defaultValue = "false" )
    protected boolean testFailureIgnore;

    /**
     * Set this to "true" to cause a failure if there are no tests to run. Defaults to "false".
     */
    @Parameter( property = "failIfNoTests" )
    protected Boolean failIfNoTests;

    /** Location of the file. */
    @Parameter(property = "project.build.outputDirectory", required = true)
    protected File outputDirectory;

    /** Whether all tests are completed. */
    @Parameter(defaultValue = "true")
    protected Boolean sync;

    /** Timeout seconds for waiting to report */
    @Parameter(defaultValue = "60")
    protected long timeoutForReporting;

    /** TestStreamer URL */
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

        if (reportsDirectory != null && !reportsDirectory.exists()) {
            if (!reportsDirectory.mkdirs())
                throw new MojoExecutionException("Can't make reports directory. :" + reportsDirectory);
        }

        if (includes == null || includes.isEmpty()) {
            // Set default include patterns.
            includes = Arrays.asList("**/Test*.java", "**/*Test.java", "**/*TestCase.java");
        }

        try {
            String testShotId = UUID.randomUUID().toString();

            if (submitTests(testShotId) && sync) {
                SummaryReport summary = fetchReport(testShotId);
                getLog().info(summary.toString());
                if (summary.isErrorFree())
                    return;

                String msg = "There are test failures.\n\nPlease refer to " + reportsDirectory
                        + " for the individual test results.";
                if (testFailureIgnore) {
                    getLog().error(msg);
                } else {
                    if (summary.getErrors() == 0) {
                        throw new MojoExecutionException(msg);
                    } else {
                        throw new MojoFailureException(msg);
                    }

                }
            }
        } catch (Exception ex) {
            if (ex instanceof MojoExecutionException) {
                throw (MojoExecutionException) ex;
            } else {
                throw new MojoExecutionException(ex.getMessage(), ex);
            }
        }
    }

    private AsyncHttpClient makeClient() {
        NettyAsyncHttpProviderConfig providerConfig = new NettyAsyncHttpProviderConfig();
        providerConfig.addProperty(NettyAsyncHttpProviderConfig.HTTP_CLIENT_CODEC_MAX_HEADER_SIZE, 8192 * 16);
        AsyncHttpClientConfig config = new AsyncHttpClientConfig.Builder()
                .setAsyncHttpClientProviderConfig(providerConfig)
                .build();
        return new AsyncHttpClient(config);
    }

    protected boolean submitTests(String testShotId) throws Exception {
        AsyncHttpClient client = makeClient();
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

        parameters.put("shotId", Arrays.asList(testShotId));

        String url = testStreamerUrl + "/test-shots";
        Future<Integer> f = client
                .preparePost(url)
                .addHeader("accept", "application/json")
                .setParameters(parameters)
                .execute(new AsyncCompletionHandler<Integer>() {
                    @Override
                    public Integer onCompleted(Response response) throws Exception {
                        return response.getStatusCode();
                    }
                });

        Integer status = f.get(5000, TimeUnit.MILLISECONDS);
        if (status == 201) {
            getLog().info("Successful for submitting tests.");
            return true;
        } else if (status == 404) {
            getLog().info("No tests.");
            return false;
        } else {
            throw new MojoExecutionException("Not success");
        }
    }

    protected SummaryReport fetchReport(String testShotId) throws Exception{
        AsyncHttpClient client = makeClient();
        final ExecutorService reportExecutor = Executors.newSingleThreadExecutor();

        while (true) {
            String url = testStreamerUrl + "/test-shots/" + testShotId + "/report";
            Future<SummaryReport> f = client
                    .prepareGet(url)
                    .addHeader("Accept", "text/xml")
                    .execute(new AsyncHandler<SummaryReport>() {
                        final PipedOutputStream pout = new PipedOutputStream();
                        final PipedInputStream  pin = new PipedInputStream(pout);
                        private Throwable t;
                        private Integer statusCode;
                        private Future<SummaryReport> writeTaskResponse = null;

                        public void onThrowable(Throwable t) {
                            t.printStackTrace();
                            this.t = t;
                        }

                        public STATE onStatusReceived(HttpResponseStatus responseStatus) throws Exception {
                            statusCode = responseStatus.getStatusCode();
                            getLog().debug("Status Code=" + statusCode);

                            if (statusCode == 200) {
                                writeTaskResponse = reportExecutor.submit(
                                        new ReportWriteTask(pin, reportsDirectory));
                            }
                            return STATE.CONTINUE;
                        }

                        public STATE onHeadersReceived(HttpResponseHeaders headers) throws Exception {
                            for (Map.Entry hdr : headers.getHeaders().entrySet()) {
                                getLog().debug(hdr.getKey() + ":" + hdr.getValue());
                            }
                            return STATE.CONTINUE;
                        }

                        public STATE onBodyPartReceived(HttpResponseBodyPart bodyPart) throws Exception {
                            if (statusCode == 200) {
                                bodyPart.writeTo(pout);
                                pout.flush();
                            }
                            return STATE.CONTINUE;
                        }

                        public SummaryReport onCompleted() throws Exception {
                            if (t != null)
                                throw (Exception) t;

                            if (pout != null)
                                pout.close();

                            if (statusCode == 503)
                                return null; // Server Timeout

                            try {
                                if (writeTaskResponse == null || (writeTaskResponse.get()) == null) {
                                    throw new IOException("Can't write reports to files.");
                                } else {
                                    return writeTaskResponse.get();
                                }
                            } finally {
                                if (pin != null)
                                    pin.close();
                            }
                        }
                    });
            try {
                SummaryReport summary = f.get(timeoutForReporting, TimeUnit.SECONDS);
                if (summary != null) {
                    return summary;
                } else {
                    getLog().debug("Server timeout.");
                    throw new TimeoutException();
                }
            } catch(TimeoutException ex) {
                // retry
                getLog().debug("Timeout at waiting for reports.");
            }

        }
    }

    @SuppressWarnings("UnusedDeclaration")
    public MavenProject getProject() {
        return project;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setProject(MavenProject project) {
        this.project = project;
    }

    protected List<URL> getClasspaths() {
        List<Artifact> artifacts = project.getTestArtifacts();
        List<URL> urls = new ArrayList<URL>(artifacts.size());

        for (Artifact artifact : artifacts) {
            File file = artifact.getFile();
            try {
                urls.add(file.toURI().toURL());
            } catch (MalformedURLException ignore) {
                getLog().warn("Can't resolve file to url." + file);
            }
        }
        return urls;
    }
}
