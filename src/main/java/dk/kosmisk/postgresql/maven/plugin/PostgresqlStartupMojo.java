package dk.kosmisk.postgresql.maven.plugin;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;

import static dk.kosmisk.postgresql.maven.plugin.PostgresqlAbstractMojo.DATABASES_STOP_COMMANDS;

/**
 * Mojo for starting an PostgreSQL instance before integration test
 *
 * @author Source (source (at) kosmisk.dk)
 */
@Mojo(threadSafe = true, name = "startup", defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST, requiresProject = false)
public class PostgresqlStartupMojo extends PostgresqlAbstractMojo {


    /**
     * Where to place the logfile default is name of database directory + .log
     */
    @Parameter
    private File logfile;

    /**
     * User to own database
     */
    @Parameter(defaultValue = "${user.name}")
    protected String user;

    /**
     * Password for database owner
     */
    @Parameter(defaultValue = "${user.name}")
    protected String password;

    /**
     * Scripts to be loaded to setup database
     */
    @Parameter
    protected List<File> scripts;

    /**
     * Map of parameters to be set in postgresql.conf
     */
    @Parameter
    protected Map<String, String> settings;

    /**
     * PostgreSQL binary package group
     */
    @Parameter(defaultValue = "dk.kosmisk")
    protected String groupId;

    /**
     * PostgreSQL binary package artifact
     */
    @Parameter(defaultValue = "postgresql-binary")
    protected String artifactId;

    /**
     * PostgreSQL binary package version
     */
    @Parameter(defaultValue = "LATEST", required = true)
    protected String version;

    /**
     * Unpack even if a version has already been unpacked
     */
    @Parameter(defaultValue = "false")
    protected boolean overwrite;

    // GREATLY INSPIRED BY: https://gist.github.com/vincent-zurczak/282775f56d27e12a70d3
    @Component
    private RepositorySystem repoSystem;

    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true, required = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true)
    private List<RemoteRepository> repositories;

    private Log log;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip()) {
            return;
        }

        if (user.equals("postgres")) {
            throw new MojoFailureException("PostgreSQL user name cannot be 'postgres'. This is reserved for administrative purposes");
        }

        log = getLog();
        try {
            unpackArtifact();

            Path dataDir = dbPath().resolve(name);
            deleteFileOrFolder(dataDir);
            mkdirs(dbPath().toFile());

            log.info("Starting up database: " + name);
            log.info("- using port: " + resolvePort());
            log.info("- using datadir: " + dataDir);

            List<String> prepareCommand = makeCommand("prepare");

            int prepareExitCode = processBuilder(prepareCommand)
                    .start()
                    .waitFor();
            if (prepareExitCode != 0) {
                throw new MojoExecutionException("Cannot prepare database. exit code is: " + prepareExitCode);
            }

            if (settings != null) {
                File configFile = dbPath().resolve(name).resolve("postgresql.conf").toFile();
                processConfig(configFile, settings);
            }

            List<String> startCommand = makeCommand("start");
            if (scripts != null) {
                scripts.stream()
                        .map(File::getAbsolutePath)
                        .forEach(startCommand::add);
            }

            int startExitCode = processBuilder(startCommand)
                    .start()
                    .waitFor();
            if (startExitCode != 0) {
                throw new MojoExecutionException("Cannot start database. exit code is: " + startExitCode);
            }

            DATABASES_STOP_COMMANDS.put(name, processBuilder(makeCommand("stop")));

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                ProcessBuilder stopProcess = DATABASES_STOP_COMMANDS.remove(name);

                if (stopProcess != null) {
                    log.info("Stopping database " + name);
                    try {
                        stopProcess.start();
                    } catch (IOException ex) {
                        log.error("Cannot stop database: " + name, ex);
                    }
                }
            }));
        } catch (IOException | ArtifactResolutionException | InterruptedException ex) {
            throw new MojoFailureException("Cannot start PostgreSQL Database", ex);
        }
    }

    static void processConfig(File configFile, Map<String, String> settings) throws IOException {
        String config = FileUtils.readFileToString(configFile, StandardCharsets.UTF_8);
        for (Map.Entry<String, String> setting : settings.entrySet()) {
            StringBuilder newConfig = new StringBuilder();

            String key = setting.getKey();
            String value = setting.getValue();
            Matcher matcher = Pattern.compile("^[# ]*(" + Pattern.quote(key) + "\\s*=(?s:(?:.*?)))(\n?)(:?^(?=\\S)|\\Z)", Pattern.MULTILINE)
                    .matcher(config);
            int pos = 0;
            while (matcher.find()) {
                newConfig.append(config.substring(pos, matcher.start()))
                        .append("#")
                        .append(matcher.group(1).replace("\n", "\n#"))
                        .append("\n");
                if (pos == 0) {
                    newConfig.append(key).append(" = ").append(value).append("\n");
                }
                pos = matcher.end();
            }
            newConfig.append(config.substring(pos));
            if (pos == 0) {
                newConfig.append(key).append(" = ").append(value).append("\n");
            }
            config = newConfig.toString();

        }
        FileUtils.writeStringToFile(configFile, config, StandardCharsets.UTF_8);
    }

    /**
     * Path of scripts folder
     *
     * @return Path object
     */
    private Path scriptPath() {
        return folder.toPath().resolve("binary").toAbsolutePath();
    }

    /**
     * Path of database folder
     *
     * @return Path object
     */
    private Path dbPath() {
        return folder.toPath().resolve("db").toAbsolutePath();
    }

    /**
     * Logfile position
     *
     * @return File object
     */
    private File logFile() {
        if (logfile == null) {
            logfile = dbPath().resolve(name + ".log").toFile();
        }
        return logfile;
    }

    /**
     * Construct a command with all the common arguments
     *
     * @param script basename of command
     * @return command arguments list
     */
    private List<String> makeCommand(String script) {
        ArrayList<String> command = new ArrayList<>();
        command.add(scriptPath().resolve(script + scriptExtension()).toString());
        return command;
    }

    /**
     * Construct a process with environment containing PLUGIN_* variables
     *
     * @param command command list
     * @return process builder ready to start
     */
    private ProcessBuilder processBuilder(List<String> command) throws MojoExecutionException {
        final File processLog = folder.toPath().resolve("system.log").toAbsolutePath().toFile();
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(scriptPath().toFile())
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(processLog));
        Map<String, String> env = builder.environment();
        env.put("PLUGIN_INSTALL_DIR", scriptPath().toString());
        env.put("PLUGIN_INSTALL_DIR_SQL", sqlQuote(scriptPath().toString()));
        env.put("PLUGIN_PORT", String.valueOf(resolvePort()));
        env.put("PLUGIN_PORT_SQL", sqlQuote(String.valueOf(resolvePort())));
        env.put("PLUGIN_USER", user);
        env.put("PLUGIN_USER_SQL", sqlQuote(user));
        env.put("PLUGIN_PASSWORD", password);
        env.put("PLUGIN_PASSWORD_SQL", sqlQuote(password));
        env.put("PLUGIN_DATABASE_NAME", name);
        env.put("PLUGIN_DATABASE_NAME_SQL", sqlQuote(name));
        env.put("PLUGIN_DATA_DIR", dbPath().resolve(name).toString());
        env.put("PLUGIN_DATA_DIR_SQL", sqlQuote(dbPath().resolve(name).toString()));
        env.put("PLUGIN_LOG_FILE", logFile().toString());
        env.put("PLUGIN_LOG_FILE_SQL", sqlQuote(logFile().toString()));
        return builder;
    }

    private static String sqlQuote(String s) {
        return "'" + s.replace("'", "''") + "'";
    }

    /**
     * Unpack an artifact if none exists or overwrite is defined
     *
     * @throws ArtifactResolutionException Cannot find artifact
     * @throws IOException                 if uinpacking is impossible
     * @throws MojoFailureException        if an internal error has happened
     */
    private void unpackArtifact() throws ArtifactResolutionException, IOException, MojoFailureException {
        File file = resolveArtifact();
        String key = file.getAbsolutePath() + " -> " + scriptPath().toAbsolutePath();
        if (ARTIFACT_UNPACKED.add(key) &&
            ( scriptPath().toFile().mkdirs() || overwrite )) {
            log.info("Unpacking postgres-binary");
            deleteFileOrFolder(scriptPath());
            if (!scriptPath().toFile().mkdirs()) {
                log.debug("Made binary directory");
            }
            unzip(file, scriptPath());
        } else {
            log.info("Reusing unpacked postgres-binary");
        }
    }

    /**
     * Resolve the artifact ti a file location
     *
     * @return file location of artifact
     * @throws ArtifactResolutionException Cannot find artifact
     * @throws MojoFailureException        if an internal error has happened
     */
    private File resolveArtifact() throws ArtifactResolutionException, MojoFailureException {
        DefaultArtifact artifact = new DefaultArtifact(groupId, artifactId, classifier(), "zip", version);
        ArtifactRequest request = new ArtifactRequest()
                .setRepositories(repositories)
                .setArtifact(artifact);
        ArtifactResult result = repoSystem.resolveArtifact(repoSession, request);
        File file = result.getArtifact().getFile();
        if (file == null || !file.exists()) {
            throw new MojoFailureException("Cannot resolve artifact: " + artifact);
        }
        return file;
    }

    /**
     * Unzip a file into a path
     *
     * @param zipfile    zip file location
     * @param targetPath destination path
     * @throws FileNotFoundException If file cannot be opened
     * @throws IOException           if unpacking fails
     */
    @SuppressWarnings("PMD.AvoidUsingOctalValues")
    private void unzip(File zipfile, Path targetPath) throws FileNotFoundException, IOException {
        byte[] buffer = new byte[1024];
        try (ZipFile zf = new ZipFile(zipfile)) {
            Enumeration<ZipArchiveEntry> entries = zf.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                String fileName = entry.getName();
                File targetFile = targetPath.resolve(fileName).toAbsolutePath().toFile();
                if (entry.isDirectory()) {
                    mkdirs(targetFile);
                } else if (entry.isUnixSymlink()) {
                    try (InputStream is = zf.getInputStream(entry)) {
                        int len = is.read(buffer);
                        if (len > 0) {
                            String symlink = new String(buffer, 0, len, StandardCharsets.UTF_8);
                            Files.createSymbolicLink(targetFile.toPath(), new File(symlink).toPath());
                        } else {
                            throw new IllegalStateException("Cannot read symlink content");
                        }
                    }
                } else {
                    File parentFile = targetFile.getParentFile();
                    mkdirs(parentFile);
                    try (InputStream is = zf.getInputStream(entry) ;
                         FileOutputStream fos = new FileOutputStream(targetFile)) {
                        for (;;) {
                            int len = is.read(buffer);
                            if (len > 0) {
                                fos.write(buffer, 0, len);
                            } else {
                                break;
                            }
                        }
                    }
                    if (( entry.getUnixMode() & 0111 ) != 0) {
                        targetFile.setExecutable(true);
                    }
                }
            }
        }
    }

    private void mkdirs(File directory) throws IOException {
        if (!directory.isDirectory()) {
            boolean madeDir = directory.mkdirs();
            if (!madeDir) {
                throw new IOException("Could not make directory: " + directory);
            }
        }
    }

    /**
     * "rm -rf" implementation
     * https://stackoverflow.com/questions/3775694/deleting-folder-from-java
     *
     * @param path what to remove
     * @throws IOException if files cannot be removed
     */
    private void deleteFileOrFolder(final Path path) throws IOException {
        if (!path.toFile().exists()) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
                       @Override
                       public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs)
                               throws IOException {
                           Files.delete(file);
                           return FileVisitResult.CONTINUE;
                       }

                       @Override
                       public FileVisitResult visitFileFailed(final Path file, final IOException e) {
                           return handleException(e);
                       }

                       private FileVisitResult handleException(final IOException e) {
                           log.error("Error deleting tree", e);
                           return FileVisitResult.TERMINATE;
                       }

                       @Override
                       public FileVisitResult postVisitDirectory(final Path dir, final IOException e)
                               throws IOException {
                           if (e != null) {
                               return handleException(e);
                           }
                           Files.delete(dir);
                           return FileVisitResult.CONTINUE;
                       }
                   });
    }
}
