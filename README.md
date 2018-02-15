# postgresql-maven-plugin

## An integration test helper

This is a plugin for running a PostgreSQL server around integration testing.

It has 3 stages

* **setup** This stage
    * Chooses a port if none has been defined in <port/> (by listening to a tcp port, and releasing it for reuse)
    * Exposes said port as a property specified in <portProperty/>, this defaults to postgresql.${name}.port
    * Chooses a dump folder if none has been defined in <dumpFolder/>, this defaults to ${folder}/dump/${name}
    * Exposes said folder as a property: postgresql.dump.folder
* **startup** This stage
    * unpacks the postgresq-binary artifact.
    * Calls the prepare.sh/.bat script to set up ad database
    * Modifies the `postgresql.conf` file according to the <settings> tag
    * Calls the start.sh/.bat script to start up the database
* **shutdown** This stage
    * Calls the stop.sh/.bat script to shut down the database


## Example of usage:


        <plugin>
            <groupId>dk.kosmisk</groupId>
            <artifactId>postgresql-maven-plugin</artifactId>
            <version>LATEST</version>
            <configuration>
                <!-- <groupId>dk.kosmisk</groupId> -->
                <!-- <artifactId>postgresql-binary</artifactId> -->
                <!-- <version>LATEST</version> -->
                <!-- <folder>${project.build.directory}/postgresql</folder> -->
                <!-- <overwrite>true</overwrite> -->
            </configuration>
            <executions>
                <execution>
                    <id>postgresql-test-database</id>
                    <goals>
                        <goal>setup</goal>
                        <goal>startup</goal>
                        <goal>shutdown</goal>
                    </goals>
                    <configuration>
                        <name>testbase</name>
                        <!-- <dumpFolder>${folder}/dump/${name}</dumpFolder> -->
                        <!-- <portProperty>postgresql.testbase.port</portProperty> -->
                        <!-- <port>[random-port]</port> -->
                        <!-- <user>${user.name}</user> -->
                        <!-- <password>${user.name}</password> -->
                        <!-- <logfile>${folder}/db/testbase.log</logfile> -->
                        <!-- <settings>
                            <archive_mode>on</archive_mode>
                        </settings> -->
                    </configuration>
                </execution>
            </executions>
        </plugin>

    ....

        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-failsafe-plugin</artifactId>
            <version>LATEST</version>
            <configuration>
                <systemPropertyVariables>
                    <postgresql.testbase.port>${postgresql.testbase.port}</postgresql.testbase.port>
                    <postgresql.dump.folder>${postgresql.dump.folder}</postgresql.dump.folder>
                </systemPropertyVariables>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>integration-test</goal>
                        <goal>verify</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>

---