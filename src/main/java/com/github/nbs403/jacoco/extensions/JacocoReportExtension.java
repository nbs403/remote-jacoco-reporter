package com.github.nbs403.jacoco.extensions;

import com.github.nbs403.jacoco.utils.Constants;
import com.github.nbs403.jacoco.annotations.JacocoReport;
import org.apache.log4j.Logger;
import org.jacoco.core.analysis.Analyzer;
import org.jacoco.core.analysis.CoverageBuilder;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.data.ExecutionData;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.ExecutionDataWriter;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.tools.ExecDumpClient;
import org.jacoco.core.tools.ExecFileLoader;
import org.jacoco.report.DirectorySourceFileLocator;
import org.jacoco.report.FileMultiReportOutput;
import org.jacoco.report.IReportVisitor;
import org.jacoco.report.html.HTMLFormatter;
import org.junit.jupiter.api.extension.AfterTestExecutionCallback;
import org.junit.jupiter.api.extension.BeforeTestExecutionCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.platform.commons.util.AnnotationUtils;
import org.junit.platform.commons.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;


import static org.junit.platform.commons.util.AnnotationUtils.isAnnotated;

public class JacocoReportExtension implements BeforeTestExecutionCallback, AfterTestExecutionCallback {

    @SuppressWarnings("unused")
    private static final ExtensionContext.Namespace NAMESPACE = ExtensionContext.Namespace
            .create("com", "github", "nbs403", "jacoco", "coverage", JacocoReportExtension.class.getSimpleName());
    private static final String TESTSFOLDER = "tests";
    private static final String SCENARIOSFOLDER = "scenarios";
    private static final String MERGEDEXECFILENAME = "merged.exec";
    private static final int DEFAULTPORT = 6400;
    private static final Logger LOG = Logger.getLogger(String.valueOf(JacocoReportExtension.class));

    private static Integer port;
    private static String host;

    final File reportDirectory = new File("build", "coveragereport");
    final File testsReportDirectory = new File(reportDirectory, TESTSFOLDER);
    final File scenariosReportDirectory = new File(reportDirectory, SCENARIOSFOLDER);
    final File sourcesDirectory;
    final File classesDirectory;

    public JacocoReportExtension() {
        host = getEnvOrSystemProperty(Constants.JACOCO_HOST);
        Objects.requireNonNull(host, "Jacoco host -JACOCO_HOST- cannot be null");
        final String envPort = getEnvOrSystemProperty(Constants.JACOCO_PORT);
        port = envPort == null ? DEFAULTPORT : Integer.parseInt(envPort);
        sourcesDirectory = new File(Optional.ofNullable(getEnvOrSystemProperty(Constants.JACOCO_SOURCES_DIR))
                                            .orElse("."));
        classesDirectory = new File(Optional.ofNullable(getEnvOrSystemProperty(Constants.JACOCO_CLASSES_DIR))
                                            .orElse("."));
        System.setProperty(Constants.JACOCO_PORT, String.valueOf(port));
        System.setProperty(Constants.JACOCO_CLASSES_DIR, classesDirectory.getAbsolutePath());
        System.setProperty(Constants.JACOCO_SOURCES_DIR, sourcesDirectory.getAbsolutePath());
    }

    /**
     * Simple helper method to check the passed key in environment variables first, then system properties
     *
     * @param key - key to get value for
     * @return environment value - if exists - or System.getProperty value if exists, or null
     */
    @SuppressWarnings({"unused"})
    private static String getEnvOrSystemProperty(final String key) {
        //First check environment variables if set
        if (System.getenv(key) != null) {
            return System.getenv(key);
        }
        if (System.getProperty(key) != null) {
            return System.getProperty(key);
        }
        return null;
    }

    /**
     * Configures execution data dump client with default settings
     *
     * @return ExecDumpClient
     */
    @SuppressWarnings("MagicNumber")
    private static ExecDumpClient getExecDumpClient() {
        final ExecDumpClient client = new ExecDumpClient();
        client.setDump(true);
        client.setReset(true);
        client.setRetryCount(3);
        client.setRetryDelay(3000L);
        return client;
    }

    /**
     * Performs checks to attempt collecting coverage data or not
     * Are environment variables set for instrumented deployment connection host and port - default 6400
     * Is JacocoReport tag set?
     *
     * @param context - ExtensionContext to inspect and determine is dump should be executed based
     *                on test annotation
     * @return boolean true if should execute dump task
     */
    private static boolean shouldAttemptDump(final ExtensionContext context) {
        if (StringUtils.isNotBlank(host)) {
            //First check class annotation for JacocoReport. It can apply to all tests in the class
            final boolean classAnnotated = AnnotationUtils.isAnnotated(context.getTestClass(), JacocoReport.class);
            final boolean methodAnnotated = isAnnotated(context.getElement(), JacocoReport.class);
            return !classAnnotated && !methodAnnotated;
        }
        return true;
    }

    /**
     * Reads scenario name from JacocoReport tag either from the test if present or from the class.
     * Either test method or test class can be annotated with JacocoReport
     *
     * @param context    - context will the JacocoReport tag
     * @return scenario name from JacocoReport tag
     */
    @SuppressWarnings("OptionalGetWithoutIsPresent")
    private static String getScenarioName(final ExtensionContext context) {
        //We know that we have the JacocoReport tag, check if scenario is specified
        //First check the JacocoReport method annotation. It may override class annotation
        if (isAnnotated(context.getElement(), JacocoReport.class)) {
            return context.getElement().get().getAnnotation(JacocoReport.class).scenario();
        }
        if (isAnnotated(context.getTestClass(), JacocoReport.class)) {
            return context.getTestClass().get().getAnnotation(JacocoReport.class).scenario();
        }

        return "";
    }

    /**
     * Executes a dump task with reset execution data on server before each test enabled for coverage
     *
     * @param context        - context will the JacocoReport tag
     * @throws IOException   - related to connection to jacoco agent host
     */
    @Override
    public void beforeTestExecution(final ExtensionContext context) throws IOException {
        if (shouldAttemptDump(context)) {
            return;
        }
        getExecDumpClient().dump(host, port);
    }

    /**
     * This is the callback that does it all: collect execution data and generate reports for tests and scenarios
     * If it is a scenario, create merged.exec which holds aggregate data of all previous tests in the scenario
     *
     * @param context        - context will the JacocoReport tag
     * @throws IOException related to connection to jacoco agent host
     */
    @Override
    public void afterTestExecution(final ExtensionContext context) throws IOException {
        if (shouldAttemptDump(context)) {
            return;
        }

        final String title;
        final File thisTestExecFile;
        final ExecFileLoader execFileLoader = getExecDumpClient().dump(host, port);

        final String scenarioName = getScenarioName(context);
        final File reportDir;
        //Not a scenario, just save the exec file
        if (StringUtils.isBlank(scenarioName)) {
            title = context.getDisplayName();
            reportDir = new File(testsReportDirectory, title);
            thisTestExecFile = new File(reportDir, title + ".exec");
            execFileLoader.save(thisTestExecFile, true); //append can be false too, this is a new file
            final IBundleCoverage bundleCoverage = analyzeStructure(execFileLoader.getExecutionDataStore(), title);
            createReport(bundleCoverage, execFileLoader.getSessionInfoStore(), execFileLoader.getExecutionDataStore(),
                         reportDir);
        } else {
            //If merged.exec file exist, write a new one
            title = scenarioName;
            reportDir = new File(scenariosReportDirectory, title);
            final File mergedExecFile = new File(reportDir, MERGEDEXECFILENAME);
            if (!mergedExecFile.exists()) {
                //First run for this scenario, create a merged.exec
                execFileLoader.save(mergedExecFile, true);
                final IBundleCoverage bundleCoverage = analyzeStructure(execFileLoader.getExecutionDataStore(), title);
                createReport(bundleCoverage, execFileLoader.getSessionInfoStore(), execFileLoader.getExecutionDataStore(),
                             reportDir);
            } else {
                //Not first run for this scenario, keep merged file and create new one for this run
                thisTestExecFile = new File(reportDir, context.getDisplayName() + ".exec");
                execFileLoader.save(thisTestExecFile, true);
                //Here, we need to merge exec files
                final ExecFileLoader mergedExecFileLoader = new ExecFileLoader();
                mergedExecFileLoader.load(mergedExecFile);
                final ExecutionDataStore mergedDataStore =
                        merge(mergedExecFileLoader.getExecutionDataStore(), execFileLoader.getExecutionDataStore());
                //Merging session info is not really needed, it just had time stamp for when the session started
                final SessionInfoStore mergedSessionInfoStore =
                        merge(mergedExecFileLoader.getSessionInfoStore(), execFileLoader.getSessionInfoStore());
                save(mergedExecFile, mergedSessionInfoStore, mergedDataStore);
                final IBundleCoverage bundleCoverage = analyzeStructure(mergedDataStore, title);
                createReport(bundleCoverage, mergedSessionInfoStore, mergedDataStore, reportDir);

            }
        }
    }

    /**
     * Generates an HTML report from the execution data in the specified directory
     *
     * @param bundleCoverage      - report formatter visitor
     * @param sessionInfoStore    - session infos store
     * @param executionDataStore  - execution data content
     * @param reportBaseDirectory - base directory where the report will be generated
     * @throws IOException if errors during saving report locally
     */
    public void createReport(final IBundleCoverage bundleCoverage, final SessionInfoStore sessionInfoStore, final ExecutionDataStore executionDataStore,
                             final File reportBaseDirectory) throws IOException {

        final HTMLFormatter htmlFormatter = new HTMLFormatter();
        final IReportVisitor visitor = htmlFormatter.createVisitor(new FileMultiReportOutput(reportBaseDirectory));
        // Initialize the report with all of the execution and session
        visitor.visitInfo(sessionInfoStore.getInfos(), executionDataStore.getContents());
        // Call visitGroup if you need groups in your report.
        visitor.visitBundle(bundleCoverage,
                            new DirectorySourceFileLocator(sourcesDirectory, "utf-8", 4));
        // Write all out
        visitor.visitEnd();
    }

    /**
     * Performs structure analysis
     *
     * @param executionDataStore - execution data content
     * @param title              - title of the coverage report
     * @return  coverage analysis structure
     * @throws IOException if any exception thrown loading execution data store
     */
    public IBundleCoverage analyzeStructure(final ExecutionDataStore executionDataStore, final String title) throws IOException {
        final CoverageBuilder coverageBuilder = new CoverageBuilder();
        final Analyzer analyzer = new Analyzer(executionDataStore, coverageBuilder);
        analyzer.analyzeAll(classesDirectory);
        return coverageBuilder.getBundle(title);
    }

    /**
     * Writes execution data and session info to .exec file
     *
     * @param outputFile         - .exec file
     * @param sessionInfoStore   - session data
     * @param executionDataStore - execution dump data
     * @throws IOException if any exception thrown loading execution data store
     */
    public void save(final File outputFile, final SessionInfoStore sessionInfoStore, final ExecutionDataStore executionDataStore) throws IOException {
        try (final FileOutputStream outputStream = new FileOutputStream(outputFile.getAbsolutePath())) {
            final ExecutionDataWriter dataWriter = new ExecutionDataWriter(outputStream);
            executionDataStore.accept(dataWriter);
            sessionInfoStore.accept(dataWriter);
            dataWriter.flush();
        }
    }

    /**
     * Merges two execution data stores
     *
     * @param target - target of merge
     * @param other  - other source of merge
     * @return       - merged execution data store
     */
    public ExecutionDataStore merge(final ExecutionDataStore target, final ExecutionDataStore other) {
        for (final ExecutionData targetExecutionData : target.getContents()) {
            final ExecutionData otherExecutionData = other.get(targetExecutionData.getId());
            if (otherExecutionData == null) {
                LOG.info("Execution data was null. This is expected for auto-generated and excluded code");
            } else {
                targetExecutionData.merge(otherExecutionData);
            }
        }
        return target;
    }

    /**
     * Loads .exec file to an ExecutionDataStore
     *
     * @param file          - execution data dump
     * @return              - local data dump file
     * @throws IOException  - if error loading local file
     */
    public ExecutionDataStore load(final File file) throws IOException {
        final ExecFileLoader loader = new ExecFileLoader();
        loader.load(file);
        return loader.getExecutionDataStore();
    }

    /**
     * Merges session info from other to target session info store
     *
     * @param targetSessionInfoStore - target of the merge
     * @param other                  - other source to merge from
     * @return merged session info
     */
    public SessionInfoStore merge(final SessionInfoStore targetSessionInfoStore, final SessionInfoStore other) {
        targetSessionInfoStore.getInfos().addAll(other.getInfos());
        return targetSessionInfoStore;
    }
}
