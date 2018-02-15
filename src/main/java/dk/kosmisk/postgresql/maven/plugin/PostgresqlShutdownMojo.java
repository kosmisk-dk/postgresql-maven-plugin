package dk.kosmisk.postgresql.maven.plugin;

import java.io.IOException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;

/**
 *
 * Mojo for shutting down PostgreSQL instances on integration test completion
 *
 * @author Source (source (at) kosmisk.dk)
 */
@Mojo(threadSafe = true, name = "shutdown", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST, requiresProject = false)
public class PostgresqlShutdownMojo extends PostgresqlAbstractMojo {

    private Log log;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skip()) {
            return;
        }

        log = getLog();
        ProcessBuilder stopProcess = DATABASES_STOP_COMMANDS.remove(name);
        if (stopProcess == null) {
            throw new MojoExecutionException("Cannot stop database: " + name + ". Don't know how");
        }

        log.info("Stopping database: " + name);
        try {

            stopProcess.start()
                    .waitFor();
        } catch (IOException | InterruptedException ex) {
            log.error("Cannot stop database: " + name, ex);
        }
    }

}
