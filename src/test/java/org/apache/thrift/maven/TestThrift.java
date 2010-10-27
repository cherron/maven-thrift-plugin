package org.apache.thrift.maven;

import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TestThrift {

    private static final String DEFAULT_THRIFT_PATH = "/usr/local/bin/thrift";
    private File testRootDir;
    private File idlDir;
    private File sharedJavaPackageDir;
    private File tutorialJavaPackageDir;
    private Thrift.Builder builder;

    @Before
    public void setup() throws Exception {
        final File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        testRootDir = new File(tmpDir, "thrift-test");

        if (testRootDir.exists()) {
            FileUtils.cleanDirectory(testRootDir);
        } else {
            assertTrue("Failed to create output directory for test: " + testRootDir.getPath(), testRootDir.mkdir());
        }

        File testResourceDir = new File("src/test/resources");
        assertTrue("Unable to find test resources", testRootDir.exists());

        idlDir = new File(testResourceDir, "idl");

        //Derived from the package declaration within IDL file: shared.thrift
        sharedJavaPackageDir = new File(testRootDir, "shared");
        //Derived from the package declaration within IDL file: tutorial.thrift
        tutorialJavaPackageDir = new File(testRootDir, "tutorial");        

        File thriftExecutable = new File(DEFAULT_THRIFT_PATH);
        if (thriftExecutable.exists()) {
            builder = new Thrift.Builder(DEFAULT_THRIFT_PATH, testRootDir);
        } else {
            builder = new Thrift.Builder("thrift", testRootDir);
        }
        builder
            .setGenerator("java")
            .addThriftPathElement(idlDir);
    }

    @Test
    public void testThriftCompile() throws Exception {
        executeThriftCompile();
    }

    @Test
    public void testThriftCompileWithGeneratorOption() throws Exception {
        builder.setGenerator("java:private-members,hashcode");
        executeThriftCompile();
    }

    private void executeThriftCompile() throws CommandLineException {
        final File thriftFile = new File(idlDir, "shared.thrift");

        builder.addThriftFile(thriftFile);

        final Thrift thrift = builder.build();

        assertTrue("File not found: shared.thrift", thriftFile.exists());
        assertFalse("Generated 'shared' package directory should not yet exist",
                    sharedJavaPackageDir.exists());

        // execute the compile
        final int result = thrift.compile();
        if (result != 0) {
            System.out.println("thrift failed output: " + thrift.getOutput());
            System.out.println("thrift failed error: " + thrift.getError());
            System.out.println("thrift command: [" + thrift.toString() + "]");
        }
        assertEquals(0, result);

        assertTrue("Generated java code doesn't exist",
                   new File(sharedJavaPackageDir, "SharedService.java").exists());
    }

    @Test
    public void testThriftMultipleFileCompile() throws Exception {
        final File sharedThrift = new File(idlDir, "shared.thrift");
        final File tutorialThrift = new File(idlDir, "tutorial.thrift");

        builder.addThriftFile(sharedThrift);
        builder.addThriftFile(tutorialThrift);

        final Thrift thrift = builder.build();

        assertTrue("File not found: shared.thrift", sharedThrift.exists());
        assertFalse("Generated 'shared' package directory should not yet exist",
                    sharedJavaPackageDir.exists());
        assertFalse(
                "Generated 'tutorial' package directory should not yet exist",
                tutorialJavaPackageDir.exists());

        // execute the compile
        final int result = thrift.compile();
        assertEquals(0, result);

        assertTrue("Generated 'shared' package directory not found",
                    sharedJavaPackageDir.exists());
        assertTrue("Generated 'tutorial' package directory not found",
                    tutorialJavaPackageDir.exists());
        assertTrue("Generated java code doesn't exist",
                   new File(sharedJavaPackageDir,
                            "SharedService.java").exists());
        assertTrue("generated java code doesn't exist",
                   new File(tutorialJavaPackageDir,
                            "InvalidOperation.java").exists());
    }

    @Test
    public void testBadCompile() throws Exception {
        final File thriftFile = new File(testRootDir, "missing.thrift");
        builder.addThriftPathElement(testRootDir);

        // Hacking around checks in addThrift file.
        assertTrue(thriftFile.createNewFile());
        builder.addThriftFile(thriftFile);
        assertTrue(thriftFile.delete());

        final Thrift thrift = builder.build();

        assertTrue(!thriftFile.exists());
        int testDirFileCount = 0;
        for (File file : testRootDir.listFiles()) {
            testDirFileCount++;
        }
        assertTrue(
                "Expected no files within testRootDir prior to compile attempt",
                testDirFileCount == 0);

        // execute the compile
        final int result = thrift.compile();
        assertEquals(1, result);

        testDirFileCount = 0;
        for (File file : testRootDir.listFiles()) {
            testDirFileCount++;
        }
        assertTrue(
                "Expected no files within testRootDir after failed compile attempt",
                testDirFileCount == 0);
    }

    @Test
    public void testFileInPathPreCondition() throws Exception {
        final File thriftFile = new File(testRootDir, "missing.thrift");

        // Hacking around checks in addThrift file.
        assertTrue(thriftFile.createNewFile());
        try {
            builder.addThriftFile(thriftFile);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
        }
    }

    @After
    public void cleanup() throws Exception {
        if (testRootDir.exists()) {
            FileUtils.cleanDirectory(testRootDir);
            assertTrue("Failed to delete output directory for test: " + testRootDir.getPath(), testRootDir.delete());
        }
    }
}
