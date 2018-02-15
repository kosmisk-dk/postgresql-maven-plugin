package dk.kosmisk.postgresql.maven.plugin;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.util.Properties;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Mojo for setting up system properties and allocating ports in none has been defined
 *
 * @author Source (source (at) kosmisk.dk)
 */
@Mojo(threadSafe = true, name = "setup", defaultPhase = LifecyclePhase.INITIALIZE, requiresProject = true)
public class PostgresqlSetupMojo extends PostgresqlAbstractMojo {

    private static final String DUMP_FOLDER_PROPERTY = "postgresql.dump.folder";
    private Log log;

    /**
     * Folder to contain COPY TO/FROM files when using the
     * dk.kosmisk:postgresql-test-datasource artifacts copy*(To/From)Disk
     */
    @Parameter(property = DUMP_FOLDER_PROPERTY)
    protected File dumpFolder;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip()) {
            return;
        }

        log = getLog();
        try {
            Properties properties = getProject().getProperties();
            if (port == null) {
                if (portProperty == null) {
                    portProperty = "postgresql." + name + ".port";
                }
                String oldPort = properties.getProperty(portProperty);
                if (oldPort == null) {
                    port = allocatePort();
                    properties.setProperty(portProperty, String.valueOf(port));
                    log.info("Allocated port:" + port + " for: " + name + " in: " + portProperty);
                }
            }
            if(!properties.containsKey(DUMP_FOLDER_PROPERTY)) {
                properties.setProperty(DUMP_FOLDER_PROPERTY, dumpPath().toString());
            }
            Path dumpPath = dumpPath();
            boolean createdDirs = dumpPath.toFile().mkdirs();
            if (createdDirs) {
                log.debug("Created dump folder " + dumpPath.toString());
            }
        } catch (IOException ex) {
            throw new MojoFailureException("Cannot start PostgreSQL Database", ex);
        }
    }

    private int allocatePort() throws IOException {
        try (ServerSocket socket = new ServerSocket()) {
            socket.setReuseAddress(true);
            socket.bind(null);
            return socket.getLocalPort();
        }
    }

    /**
     * Path of database dump folder
     *
     * @return Path object
     */
    private Path dumpPath() {
        if(dumpFolder == null) {
            dumpFolder = folder.toPath().resolve("dump").resolve(name).toAbsolutePath().toFile();
        }
        return dumpFolder.toPath().toAbsolutePath();
    }

}
