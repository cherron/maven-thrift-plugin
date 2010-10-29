package org.apache.thrift.maven;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newLinkedList;
import static com.google.common.collect.Sets.newHashSet;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * This class represents an invokable configuration of the {@code thrift}
 * compiler. The actual executable is invoked using the plexus
 * {@link Commandline}.
 *
 * @author gak@google.com (Gregory Kick)
 */
final class Thrift {

    final static String GENERATED_DIR_PREFIX = "gen-";

    private final String executable;
    private final String generator;
    private final ImmutableSet<File> thriftPathElements;
    private final ImmutableSet<File> thriftFiles;
    private final File outputDirectory;
    private final File workingDirectory;    
    private final CommandLineUtils.StringStreamConsumer output;
    private final CommandLineUtils.StringStreamConsumer error;

    /**
     * Constructs a new instance. This should only be used by the {@link Builder}.
     *
     * @param executable      The path to the {@code thrift} executable.
     * @param generator       The value for the {@code --gen} option.
     * @param thriftPath      The directories in which to search for imports.
     * @param thriftFiles     The thrift source files to compile.
     * @param outputDirectory The directory into which source files will be
     *                        generated.
     */
    private Thrift(
            String executable, String generator, ImmutableSet<File> thriftPath,
            ImmutableSet<File> thriftFiles, File outputDirectory) {
        this.executable = checkNotNull(executable, "executable");
        this.generator = checkNotNull(generator, "generator");
        this.thriftPathElements = checkNotNull(thriftPath, "thriftPath");
        this.thriftFiles = checkNotNull(thriftFiles, "thriftFiles");
        this.outputDirectory = checkNotNull(outputDirectory, "outputDirectory");
        this.error = new CommandLineUtils.StringStreamConsumer();
        this.output = new CommandLineUtils.StringStreamConsumer();
        final File tmpDir = new File(System.getProperty("java.io.tmpdir"));
        workingDirectory =
                new File(tmpDir, "thrift-work-dir_" + UUID.randomUUID());
        //workingDirectory.deleteOnExit();
        if (!workingDirectory.mkdir()) {
            throw new RuntimeException("Failed to create working directory: " +
                    workingDirectory.getPath());
        }        
    }

    /**
     * Invokes the {@code thrift} compiler using the configuration specified at
     * construction.
     *
     * @return The exit status of {@code thrift}.
     * @throws CommandLineException
     */
    public int compile() throws CommandLineException {

        for (File thriftFile : thriftFiles) {
            Commandline cl = new Commandline(executable);
            cl.addArguments(buildThriftCommand(thriftFile).toArray(new String[]{}));
            final int result = CommandLineUtils.executeCommandLine(cl, null, output, error);

            if (result != 0) {
                return result;
            }
        }

        moveGeneratedFiles();

        return 0;
    }

    /**
     * Creates the command line arguments.
     * <p/>
     * This method has been made visible for testing only.
     *
     * @param thriftFile
     * @return A list consisting of the executable followed by any arguments.
     */
    ImmutableList<String> buildThriftCommand(final File thriftFile) {
        final List<String> command = newLinkedList();
        // add the executable
        for (File thriftPathElement : thriftPathElements) {
            command.add("-I");
            command.add(thriftPathElement.toString());
        }
        command.add("-o");
        //Output to workingDirectory first, copy to outputDirectory afterwards
        command.add(workingDirectory.toString());
        command.add("--gen");
        command.add(generator);
        command.add(thriftFile.toString());
        return ImmutableList.copyOf(command);
    }

    /**
     * Moves thrift-generated source from the temporary working directory to the
     * specified output directory. In doing so, the thrift "gen-*" directory is
     * omitted. Supports targeting an existing source tree, in case (for example)
     * the generated source is being managed in VCS.
     */
    @SuppressWarnings("unchecked")
    private void moveGeneratedFiles() {
        File genDir = locateGenDir(workingDirectory);
        try {
            //List all generated files, relative to the gen-* directory
            final List<File> generatedFiles =
                    FileUtils.getFiles(genDir, "**", null, false);
            for (File generatedFile : generatedFiles) {
                if (!generatedFile.isDirectory()) {
                    final File targetLocation =
                            new File(outputDirectory, generatedFile.getPath());
                    final File generatedFileAbsolute =
                            new File(genDir, generatedFile.getPath());
                    FileUtils.copyFile(generatedFileAbsolute, targetLocation);
                }
            }
            FileUtils.deleteDirectory(workingDirectory);
        } catch (Throwable t) {
            throw new RuntimeException(
                    "Failed to move file(s) to output directory", t);
        }
    }



    /**
     * Locates a thrift-generated parent directory containing the generated
     * source for a given execution of the thrift compiler. This implementation
     * assumes that only a single directory will appear beneath {@link
     * #workingDirectory}, and its name will be prefixed with {@link
     * #GENERATED_DIR_PREFIX}.
     *
     * @param workingDirectory The working directory beneath which the generated
     *                         source parent directory is sought.
     *
     * @return The generated source parent directory.
     */
    private File locateGenDir(File workingDirectory) {
        File result = null;
        int numMatches = 0;
        for (File candidate : workingDirectory.listFiles()) {
            if (candidate.isDirectory() &&
                    candidate.getName().startsWith(GENERATED_DIR_PREFIX)) {
                result = candidate;
                numMatches++;
            }
        }
        if (result == null) {
            throw new RuntimeException(
                    "Failed to find thrift-generated parent directory beneath workingDirectory: " +
                            workingDirectory.getPath());
        }
        if (numMatches > 1) {
            throw new RuntimeException(
                    "Encountered more than one directory matching pattern: [" +
                            GENERATED_DIR_PREFIX +
                            "*] beneath workingDirectory: " +
                            workingDirectory.getPath());
        }
        return result;
    }

    /**
     * @return the output
     */
    public String getOutput() {
        return output.getOutput();
    }

    /**
     * @return the error
     */
    public String getError() {
        return error.getOutput();
    }

    /** {@inheritDoc} */
    public String toString() {
        StringBuilder result = new StringBuilder();
        Iterator<File> thriftFileIter = thriftFiles.iterator();
        while (thriftFileIter.hasNext()) {
            File thriftFile = thriftFileIter.next();
            Commandline cl = new Commandline(executable);
            cl.addArguments(buildThriftCommand(thriftFile).toArray(new String[]{}));
            result.append(cl.toString());
            if (thriftFileIter.hasNext()) {
                result.append('\n');
            }
        }
        return result.toString();
    }

    /**
     * This class builds {@link Thrift} instances.
     *
     * @author gak@google.com (Gregory Kick)
     */
    static final class Builder {
        private final String executable;
        private final File javaOutputDirectory;
        private Set<File> thriftPathElements;
        private Set<File> thriftFiles;
        private String generator;

        /**
         * Constructs a new builder. The two parameters are present as they are
         * required for all {@link Thrift} instances.
         *
         * @param executable          The path to the {@code thrift} executable.
         * @param javaOutputDirectory The directory into which the java source files
         *                            will be generated.
         * @throws NullPointerException     If either of the arguments are {@code null}.
         * @throws IllegalArgumentException If the {@code javaOutputDirectory} is
         *                                  not a directory.
         */
        public Builder(String executable, File javaOutputDirectory) {
            this.executable = checkNotNull(executable, "executable");
            this.javaOutputDirectory = checkNotNull(javaOutputDirectory);
            checkArgument(javaOutputDirectory.isDirectory());
            this.thriftFiles = newHashSet();
            this.thriftPathElements = newHashSet();
        }

        /**
         * Adds a thrift file to be compiled. Thrift files must be on the thriftpath
         * and this method will fail if a thrift file is added without first adding a
         * parent directory to the thriftpath.
         *
         * @param thriftFile
         * @return The builder.
         * @throws IllegalStateException If a thrift file is added without first
         *                               adding a parent directory to the thriftpath.
         * @throws NullPointerException  If {@code thriftFile} is {@code null}.
         */
        public Builder addThriftFile(File thriftFile) {
            checkNotNull(thriftFile);
            checkArgument(thriftFile.isFile());
            checkArgument(thriftFile.getName().endsWith(".thrift"));
            checkThriftFileIsInThriftPath(thriftFile);
            thriftFiles.add(thriftFile);
            return this;
        }

        /**
         * Adds the option string for the Thrift executable's {@code --gen} parameter.
         * 
         * @param generator
         * @return The builder
         * @throws NullPointerException If {@code generator} is {@code null}.
         */
        public Builder setGenerator(String generator) {
            checkNotNull(generator);
            this.generator = generator;
            return this;
        }

        private void checkThriftFileIsInThriftPath(File thriftFile) {
            assert thriftFile.isFile();
            checkState(checkThriftFileIsInThriftPathHelper(thriftFile.getParentFile()));
        }

        private boolean checkThriftFileIsInThriftPathHelper(File directory) {
            assert directory.isDirectory();
            if (thriftPathElements.contains(directory)) {
                return true;
            } else {
                final File parentDirectory = directory.getParentFile();
                return (parentDirectory == null) ? false
                    : checkThriftFileIsInThriftPathHelper(parentDirectory);
            }
        }

        /**
         * @see #addThriftFile(File)
         */
        public Builder addThriftFiles(Iterable<File> thriftFiles) {
            for (File thriftFile : thriftFiles) {
                addThriftFile(thriftFile);
            }
            return this;
        }

        /**
         * Adds the {@code thriftPathElement} to the thriftPath.
         *
         * @param thriftPathElement A directory to be searched for imported thrift message
         *                          buffer definitions.
         * @return The builder.
         * @throws NullPointerException     If {@code thriftPathElement} is {@code null}.
         * @throws IllegalArgumentException If {@code thriftPathElement} is not a
         *                                  directory.
         */
        public Builder addThriftPathElement(File thriftPathElement) {
            checkNotNull(thriftPathElement);
            checkArgument(thriftPathElement.isDirectory());
            thriftPathElements.add(thriftPathElement);
            return this;
        }

        /**
         * @see #addThriftPathElement(File)
         */
        public Builder addThriftPathElements(Iterable<File> thriftPathElements) {
            for (File thriftPathElement : thriftPathElements) {
                addThriftPathElement(thriftPathElement);
            }
            return this;
        }

        /**
         * @return A configured {@link Thrift} instance.
         * @throws IllegalStateException If no thrift files have been added.
         */
        public Thrift build() {
            checkState(!thriftFiles.isEmpty());
            return new Thrift(executable, generator, ImmutableSet.copyOf(thriftPathElements),
                ImmutableSet.copyOf(thriftFiles), javaOutputDirectory);
        }
    }
}
