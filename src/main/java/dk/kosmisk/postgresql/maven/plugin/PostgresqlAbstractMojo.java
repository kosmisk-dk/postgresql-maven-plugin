package dk.kosmisk.postgresql.maven.plugin;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 *
 * Common data for starting up and shutting down PostgreSQL instances
 *
 * @author Source (source (at) kosmisk.dk)
 */
public abstract class PostgresqlAbstractMojo extends AbstractMojo {

    protected static final ConcurrentHashMap<String, ProcessBuilder> DATABASES_STOP_COMMANDS = new ConcurrentHashMap<>();
    protected static final ConcurrentSkipListSet<String> ARTIFACT_UNPACKED = new ConcurrentSkipListSet<>();

    /**
     * Name of database to be created during startup
     */
    @Parameter(required = true)
    protected String name;

    /**
     * PostgreSQL server instance port number (defaults to dynamically assigned)
     */
    @Parameter
    protected Integer port;

    /**
     * Name of the property to store port number in (default
     * postgresql.${name}.port)
     */
    @Parameter
    protected String portProperty;

    /**
     * Folder to contain "bin" and "db" directories
     */
    @Parameter(defaultValue = "${project.build.directory}/postgresql", property = "postgresql.folder")
    protected File folder;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject project;

    protected boolean skip() {
        boolean skipTests = Boolean.parseBoolean(project.getProperties().getProperty("skipTests", "false"));
        boolean skipITs = Boolean.parseBoolean(project.getProperties().getProperty("skipITs", "false"));
        return skipTests || skipITs;
    }

    protected String getPropertyName() {
        if (portProperty == null) {
            portProperty = "postgresql." + name + ".port";
        }
        return portProperty;
    }

    public MavenProject getProject() {
        return project;
    }

    protected int resolvePort() throws MojoExecutionException {
        if (port != null && port > 0) {
            return port;
        }
        String propertyPort = project.getProperties().getProperty(getPropertyName());
        if (propertyPort == null) {
            throw new MojoExecutionException("Cannot find port for: " + name);
        }
        port = Integer.parseInt(propertyPort);
        return port;
    }

    /**
     * Construct a windows/darwin/linux-i386/x64 classifier
     *
     * @return lowercase string ${os}-${architecture}
     */
    protected static String classifier() {
        String system = System.getProperty("os.name")
                .replaceFirst("\\s.*", "")
                .toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch");
        if ("i386".equalsIgnoreCase(arch)) {
            arch = "x86";
        } else if ("amd64".equalsIgnoreCase(arch) || "x86_64".equalsIgnoreCase(arch)) {
            arch = "x64";
        }
        return system + "-" + arch;
    }

    /**
     * Get the script extension
     *
     * @return .bat for windows, empty string for everything else
     */
    protected static String scriptExtension() {
        String system = System.getProperty("os.name")
                .replaceFirst("\\s.*", "")
                .toLowerCase(Locale.ROOT);
        if (system.startsWith("win")) {
            return ".bat";
        }
        if (system.startsWith("mac") || system.startsWith("linux")) {
            return ".sh";
        }
        throw new IllegalStateException("Cannot determine architecture: " + system);
    }

}
