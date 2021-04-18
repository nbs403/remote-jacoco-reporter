package com.github.nbs403.jacoco.extensions;

import com.github.nbs403.jacoco.utils.Constants;
import org.jacoco.core.analysis.IBundleCoverage;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.tools.ExecFileLoader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;

public class JacocoReportExtensionTest {

    //TODO: Add more tests to cover testing a sample app with @JacocoReport
    private static final String HELLO_CANDIDATE = "hello-candidate";
    private File tempDir;

    @AfterEach
    private void cleanup() {
        System.getProperties().remove(Constants.JACOCO_CLASSES_DIR);
        System.getProperties().remove(Constants.JACOCO_SOURCES_DIR);
    }

    @BeforeEach
    private void init() throws IOException, URISyntaxException {
        tempDir = Files.createTempDirectory("jacocotests").toFile();
        tempDir.deleteOnExit();

        //Unzip sample project. It has source, classes, and dump .exec files
        Utils.unzipFileToFolder(HELLO_CANDIDATE + ".zip", tempDir);
        System.setProperty(Constants.JACOCO_CLASSES_DIR,
                tempDir.toPath().resolve(HELLO_CANDIDATE + "/build/classes/java/main").toString());
        System.setProperty(Constants.JACOCO_SOURCES_DIR,
                tempDir.toPath().resolve(HELLO_CANDIDATE + "/src/main/java").toString());
        System.setProperty(Constants.JACOCO_HOST, "");
    }

    /**
     * This test will unzip the sample project, hello-candidate to tmp folder which should contain source code, compiled
     * classes and .exec files for unit and integration tests
     * It will replicate the behavior in JacocoReportExtension
     *
     * @throws IOException IOException
     */
    @SuppressWarnings("ResultOfMethodCallIgnored")
    //TODO: improve tests to build and generate .classes and .exec at run time
    @Test
    public void mergedUnitAndIntegrationTests() throws IOException {

        final JacocoReportExtension extension = new JacocoReportExtension();

        final File unitTestsExecFile = new File(tempDir.toPath().resolve(HELLO_CANDIDATE + "/build/jacoco/unitTest.exec").toUri());
        final ExecFileLoader unitTestsExecFileLoader = new ExecFileLoader();
        unitTestsExecFileLoader.load(unitTestsExecFile);

        final ExecFileLoader intTestsExecFileLoader = new ExecFileLoader();
        final File intTestExecFile = new File(tempDir.toPath().resolve(HELLO_CANDIDATE + "/build/jacoco/intTest" +
                ".exec").toUri());
        intTestsExecFileLoader.load(intTestExecFile);

        final ExecutionDataStore unitTestsDataStore = unitTestsExecFileLoader.getExecutionDataStore();
        final ExecutionDataStore intTestsDataStore = intTestsExecFileLoader.getExecutionDataStore();

        final SessionInfoStore unitTestSession = unitTestsExecFileLoader.getSessionInfoStore();
        final SessionInfoStore intTestSession = intTestsExecFileLoader.getSessionInfoStore();

        IBundleCoverage bundleCoverage = extension.analyzeStructure(unitTestsDataStore, "Unit Tests Coverage");
        extension.createReport(bundleCoverage, unitTestSession, unitTestsDataStore,
                new File(Paths.get(tempDir.getAbsolutePath(), "unittests").toString()));

        final int unitTestsLineCounter = bundleCoverage.getLineCounter().getCoveredCount();

        bundleCoverage = extension.analyzeStructure(intTestsDataStore, "Integration Tests Coverage");
        extension.createReport(bundleCoverage, intTestSession, intTestsDataStore,
                new File(Paths.get(tempDir.getAbsolutePath(), "inttests").toString()));
        final int intTestsLineCounter = bundleCoverage.getLineCounter().getCoveredCount();

        // Now that we have unit and integration tests .exec files
        // Prepare a new folder for merged.exec
        final File mergedDir = new File(tempDir.toPath().resolve("merged").toUri());
        mergedDir.mkdir();
        final File mergedExecFile = new File(mergedDir, "merged.exec");
        mergedDir.deleteOnExit();

        //Load one of the tests execution data - integrations tests - into new merged.exec
        final ExecFileLoader mergedExecFileLoader = new ExecFileLoader();
        mergedExecFileLoader.load(intTestExecFile);
        mergedExecFileLoader.save(mergedExecFile, true);

        final ExecutionDataStore mergedDataStore = extension.merge(mergedExecFileLoader.getExecutionDataStore(),
                unitTestsExecFileLoader.getExecutionDataStore());
        //Merging session info is not really needed, it just had time stamp for when the session started
        final SessionInfoStore mergedSessionInfoStore = extension.merge(mergedExecFileLoader.getSessionInfoStore(),
                unitTestsExecFileLoader.getSessionInfoStore());
        mergedExecFileLoader.save(mergedExecFile, true);
        // Load second exec file - unit tests
        mergedExecFileLoader.load(unitTestsExecFile);
        mergedExecFileLoader.save(mergedExecFile, true);

        bundleCoverage = extension.analyzeStructure(mergedDataStore, "Merged Tests Coverage");
        extension.createReport(bundleCoverage, mergedSessionInfoStore, mergedDataStore, new File(mergedDir.toString()));
        final int mergedLineCounter = bundleCoverage.getLineCounter().getCoveredCount();
        assertThat(String.format("Failed to verify total combined line counter: %s is greater than unit tests line counter: " +
                        "%s", mergedLineCounter, unitTestsLineCounter), mergedLineCounter,
                greaterThan(unitTestsLineCounter));
        assertThat(String.format("Failed to verify total combined line counter: %s is greater than integration tests line " +
                        "counter: %s", mergedLineCounter, intTestsLineCounter), mergedLineCounter,
                greaterThan(unitTestsLineCounter));
    }
}