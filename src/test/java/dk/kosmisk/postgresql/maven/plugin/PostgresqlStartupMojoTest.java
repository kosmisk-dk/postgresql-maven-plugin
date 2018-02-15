/*
 * Copyright (C) 2018 DBC A/S (http://dbc.dk/)
 *
 * This is part of dbc-postgresql-maven-plugin integrationtesting with a real postgresql server instance
 *
 * dbc-postgresql-maven-plugin integrationtesting with a real postgresql server instance is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * dbc-postgresql-maven-plugin integrationtesting with a real postgresql server instance is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package dk.kosmisk.postgresql.maven.plugin;

import difflib.DiffUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.*;

/**
 *
 * @author Source (source (at) kosmisk.dk)
 */
@RunWith(Parameterized.class)
public class PostgresqlStartupMojoTest {

    private final String name;
    private final File original;
    private final File mapping;
    private final File actual;
    private final File expected;

    public PostgresqlStartupMojoTest(String name, File original, File mapping, File actual, File expected) {
        this.name = name;
        this.original = original;
        this.mapping = mapping;
        this.actual = actual;
        this.expected = expected;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> tests() throws Exception {
        URL resource = PostgresqlStartupMojoTest.class.getClassLoader().getResource("config-expansion");
        File dir = new File(resource.toURI());
        Path pathPrefix = dir.toPath();
        return Arrays.stream(dir.list((f, s) -> f.isDirectory()))
                .map(test -> new Object[] {test,
                                             pathPrefix.resolve(test).resolve("original").toFile(),
                                             pathPrefix.resolve(test).resolve("mapping").toFile(),
                                             pathPrefix.resolve(test).resolve("actual").toFile(),
                                             pathPrefix.resolve(test).resolve("expected").toFile()})
                .collect(Collectors.toSet());
    }

    @Test
    public void test() throws Exception {
        System.out.println("test: " + name);
        Properties properties = new Properties();
        try {
            FileInputStream fis = new FileInputStream(mapping);
            properties.load(fis);
        } catch (IOException ex) {
            System.err.println("Exception: " + ex);
        }
        FileUtils.copyFile(original, actual);
        HashMap<String, String> map = new HashMap<>();
        properties.forEach((k, v) -> map.put(String.valueOf(k), String.valueOf(v)));
        PostgresqlStartupMojo.processConfig(actual, map);

        List<String> expectdContent = FileUtils.readLines(expected, StandardCharsets.UTF_8);
        List<String> actualContent = FileUtils.readLines(actual, StandardCharsets.UTF_8);
        List<String> generateUnifiedDiff = DiffUtils.generateUnifiedDiff(
                "expected", "actual", expectdContent,
                DiffUtils.diff(actualContent, expectdContent),
                3);
        if (!generateUnifiedDiff.isEmpty()) {
            for (String string : generateUnifiedDiff) {
                System.out.println(string);
            }
            fail("Output didn't match");
        }
    }
}
