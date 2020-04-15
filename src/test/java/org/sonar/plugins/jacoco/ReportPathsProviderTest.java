/*
 * SonarQube JaCoCo Plugin
 * Copyright (C) 2018-2020 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.jacoco;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import org.junit.Rule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;

import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport
class ReportPathsProviderTest {
  @TempDir
  Path temp;

  @Rule
  public LogTester logTester = new LogTester();

  private SensorContextTester tester;
  private ReportPathsProvider provider;
  private Path mavenPath1 = Paths.get("target", "site", "jacoco", "jacoco.xml");
  private Path mavenPath2 = Paths.get("target", "site", "jacoco-it", "jacoco.xml");
  private Path baseDir;

  @BeforeEach
  void setUp() throws IOException {
    baseDir = temp.resolve("baseDir");
    Files.createDirectory(baseDir);
    tester = SensorContextTester.create(baseDir);
    provider = new ReportPathsProvider(tester);
  }

  private void createMavenReport(Path relativePath) throws IOException {
    Path reportPath = baseDir.resolve(relativePath);
    Files.createDirectories(reportPath.getParent());
    Files.createFile(reportPath);
  }

  @Test
  void should_use_provided_paths() throws IOException {
    // even though a report will exist in a default location, it shouldn't get loaded since a path is passed as a parameter.
    createMavenReport(mavenPath1);
    createMavenReport(Paths.get("mypath1"));
    createMavenReport(Paths.get("mypath2"));

    MapSettings settings = new MapSettings();
    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, "mypath1,mypath2");
    tester.setSettings(settings);

    Collection<Path> paths = provider.getPaths();
    assertThat(paths).containsOnly(baseDir.resolve("mypath1"), baseDir.resolve("mypath2"));
  }

  @Test
  void should_use_provided_absolute_path() throws IOException {
    MapSettings settings = new MapSettings();
    Path absolutePath = baseDir.resolve("path");

    createMavenReport(absolutePath);

    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, absolutePath.toString());
    tester.setSettings(settings);

    Collection<Path> paths = provider.getPaths();
    assertThat(paths).containsOnly(absolutePath);
  }

  @Test
  void should_return_empty_if_provided_and_default_does_not_exist() throws IOException {
    MapSettings settings = new MapSettings();
    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, "mypath1,mypath2");
    tester.setSettings(settings);

    Collection<Path> paths = provider.getPaths();

    assertThat(paths).isEmpty();
    assertThat(logTester.logs(LoggerLevel.WARN)).allMatch(s -> s.startsWith("Coverage report doesn't exist:"));
    assertThat(logTester.logs(LoggerLevel.INFO)).contains("Can not find JaCoCo reports from property 'sonar.coverage.jacoco.xmlReportPaths'. "
    + "Using default locations: target/site/jacoco/jacoco.xml,target/site/jacoco-it/jacoco.xml,build/reports/jacoco/test/jacocoTestReport.xml");
  }

  @Test
  void should_fallback_to_defaults_if_exist() throws IOException {
    createMavenReport(mavenPath1);
    createMavenReport(mavenPath2);

    Collection<Path> paths = provider.getPaths();
    assertThat(paths).containsOnly(baseDir.resolve(mavenPath1), baseDir.resolve(mavenPath2));
    assertThat(logTester.logs(LoggerLevel.INFO)).contains("Can not find JaCoCo reports from property 'sonar.coverage.jacoco.xmlReportPaths'. "
      + "Using default locations: target/site/jacoco/jacoco.xml,target/site/jacoco-it/jacoco.xml,build/reports/jacoco/test/jacocoTestReport.xml");
  }

  @Test
  void should_return_empty_if_nothing_specified_and_default_doesnt_exist() throws IOException {
    Collection<Path> paths = provider.getPaths();
    assertThat(paths).isEmpty();
    assertThat(logTester.logs(LoggerLevel.INFO)).contains("Can not find JaCoCo reports from property 'sonar.coverage.jacoco.xmlReportPaths'. "
      + "Using default locations: target/site/jacoco/jacoco.xml,target/site/jacoco-it/jacoco.xml,build/reports/jacoco/test/jacocoTestReport.xml");
  }
}
