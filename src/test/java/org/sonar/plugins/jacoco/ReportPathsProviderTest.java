/*
 * SonarQube JaCoCo Plugin
 * Copyright (C) 2018-2026 SonarSource SA
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
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.log.LogTesterJUnit5;
import org.sonar.api.utils.log.LoggerLevel;

import static org.assertj.core.api.Assertions.assertThat;

class ReportPathsProviderTest {
  @TempDir
  Path temp;

  @RegisterExtension
  public LogTesterJUnit5 logTester = new LogTesterJUnit5();

  private MapSettings settings;
  private SensorContextTester tester;
  private ReportPathsProvider provider;
  private Path mavenPath1 = Paths.get("target", "site", "jacoco", "jacoco.xml");
  private Path mavenPath2 = Paths.get("target", "site", "jacoco-it", "jacoco.xml");
  private Path baseDir;

  @BeforeEach
  void setUp() throws IOException {
    baseDir = temp.resolve("baseDir");
    Files.createDirectory(baseDir);
    settings = new MapSettings();
    tester = SensorContextTester.create(baseDir);
    tester.setSettings(settings);
    provider = new ReportPathsProvider(tester);
  }

  private void createMavenReport(Path relativePath) throws IOException {
    Path reportPath = baseDir.resolve(relativePath);
    Files.createDirectories(reportPath.getParent());
    Files.createFile(reportPath);
  }

  @Test
  void should_return_no_duplicate_paths() throws IOException {
    Path report = temp.resolve("aggregate-report.xml");
    Files.createDirectories(report.getParent());
    Files.createFile(report);
    settings.setProperty(ReportPathsProvider.AGGREGATE_REPORT_PATHS_PROPERTY_KEY, report + "," + report);
    assertThat(provider.getAggregateReportPaths())
      .withFailMessage("We expect no duplicates")
      .isEqualTo(Set.of(report));
  }

  @Test
  void should_return_multiple_paths_when_aggregate_paths_property_is_provided() throws IOException {
    Path report = temp.resolve("aggregate-report.xml");
    Path report2 = temp.resolve("aggregate-report2.xml");
    Files.createDirectories(report.getParent());
    Files.createFile(report);
    Files.createFile(report2);
    settings.setProperty(ReportPathsProvider.AGGREGATE_REPORT_PATHS_PROPERTY_KEY, report + "," + report2);
    assertThat(provider.getAggregateReportPaths()).isEqualTo(Set.of(report, report2));
  }

  @Test
  void should_return_null_if_the_aggregate_report_property_is_not_defined() {
    assertThat(provider.getAggregateReportPaths()).isEmpty();
  }

  @Test
  void should_throw_an_exception_if_the_aggregate_report_property_points_to_a_file_that_should_not_exist() {
    Path reportThatDoesNotExist = temp.resolve("report-that-does-not-exist.xml");
    settings.setProperty(ReportPathsProvider.AGGREGATE_REPORT_PATHS_PROPERTY_KEY, reportThatDoesNotExist.toString());
    assertThat(provider.getAggregateReportPaths()).isEmpty();
  }

  @Test
  void should_return_all_the_expected_report_paths_without_duplicates() throws IOException {
    Path report = temp.resolve("aggregate-report.xml");
    Path report2 = temp.resolve("submodule").resolve("aggregate-report.xml");
    Files.createDirectories(report.getParent());
    Files.createDirectories(report2.getParent());
    Files.createFile(report);
    Files.createFile(report2);
    settings.setProperty(ReportPathsProvider.AGGREGATE_REPORT_PATHS_PROPERTY_KEY, report + "," + report + "," + report2);
    assertThat(provider.getAggregateReportPaths()).isEqualTo(Set.of(report, report2));
  }

  @Test
  void should_return_the_expected_report_path_when_the_aggregate_report_property_is_set_correctly() throws IOException {
    Path report = temp.resolve("aggregate-report.xml");
    Files.createDirectories(report.getParent());
    Files.createFile(report);
    settings.setProperty(ReportPathsProvider.AGGREGATE_REPORT_PATHS_PROPERTY_KEY, report.toString());
    assertThat(provider.getAggregateReportPaths()).isEqualTo(Set.of(report));
  }

  @Test
  void should_use_provided_paths() throws IOException {
    // even though a report will exist in a default location, it shouldn't get loaded since a path is passed as a parameter.
    createMavenReport(mavenPath1);
    createMavenReport(Paths.get("mypath1"));
    createMavenReport(Paths.get("mypath2"));

    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, "mypath1,mypath2");

    Collection<Path> paths = provider.getPaths();
    assertThat(paths).containsOnly(baseDir.resolve("mypath1"), baseDir.resolve("mypath2"));
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  void should_use_provided_absolute_path() throws IOException {
    Path absolutePath = baseDir.resolve("path");

    createMavenReport(absolutePath);

    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, absolutePath.toString());

    Collection<Path> paths = provider.getPaths();
    assertThat(paths).containsOnly(absolutePath);
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  void should_resolve_relative_path_pattern() throws IOException {
    Path reportPath = baseDir.resolve(Paths.get("target", "custom", "jacoco.xml"));
    Files.createDirectories(reportPath.getParent());
    Files.createFile(reportPath);

    Path realPath = reportPath.toRealPath();

    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, "target/custom/*.xml");
    assertThat(provider.getPaths()).containsOnly(realPath);

    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, "target/**/ja*.xml");
    assertThat(provider.getPaths()).containsOnly(realPath);

    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, "target\\**\\ja*.xml");
    assertThat(provider.getPaths()).containsOnly(realPath);

    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, "**/*.xml");
    assertThat(provider.getPaths()).containsOnly(realPath);
    assertThat(logTester.logs()).isEmpty();

    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, "target/**/unknown.xml");
    assertThat(provider.getPaths()).isEmpty();
    assertThat(logTester.logs()).hasSize(1);
    assertThat(logTester.logs(LoggerLevel.WARN)).contains("No coverage report can be found with sonar.coverage.jacoco.xmlReportPaths='target/**/unknown.xml'." +
      " Using default locations: target/site/jacoco/jacoco.xml,target/site/jacoco-it/jacoco.xml,build/reports/jacoco/test/jacocoTestReport.xml");
  }

  @Test
  void should_resolve_absolute_path_pattern() throws IOException {
    Path reportPath = baseDir.resolve(Paths.get("target", "custom", "jacoco.xml"));
    Files.createDirectories(reportPath.getParent());
    Files.createFile(reportPath);

    String unixLikeAbsoluteBaseDir = baseDir.toRealPath().toString().replace('\\', '/');
    String unixLikeAbsoluteXmlPattern = unixLikeAbsoluteBaseDir + "/**/*.xml";
    String windowsLikeAbsoluteXmlPattern = unixLikeAbsoluteXmlPattern.replace('/', '\\');

    Path realPath = reportPath.toRealPath();

    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, unixLikeAbsoluteXmlPattern);
    assertThat(provider.getPaths()).containsOnly(realPath);

    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, windowsLikeAbsoluteXmlPattern);
    assertThat(provider.getPaths()).containsOnly(realPath);
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  void should_return_empty_if_provided_and_default_does_not_exist() {
    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, "mypath1");

    Collection<Path> paths = provider.getPaths();

    assertThat(paths).isEmpty();
    assertThat(logTester.logs()).hasSize(1);
    assertThat(logTester.logs(LoggerLevel.WARN)).containsExactly("No coverage report can be found with sonar.coverage.jacoco.xmlReportPaths='mypath1'." +
      " Using default locations: target/site/jacoco/jacoco.xml,target/site/jacoco-it/jacoco.xml,build/reports/jacoco/test/jacocoTestReport.xml");
  }

  @Test
  void should_return_empty_with_log_details_if_several_provided_paths_does_not_exist() {
    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, "mypath1,mypath2,");

    Collection<Path> paths = provider.getPaths();

    assertThat(paths).isEmpty();
    assertThat(logTester.logs()).hasSize(3);
    assertThat(logTester.logs(LoggerLevel.WARN)).containsExactly("No coverage report can be found with sonar.coverage.jacoco.xmlReportPaths='mypath1,mypath2'. Using default locations: target/site/jacoco/jacoco.xml,target/site/jacoco-it/jacoco.xml,build/reports/jacoco/test/jacocoTestReport.xml");
    assertThat(logTester.logs(LoggerLevel.INFO)).containsExactly(
      "Coverage report doesn't exist for pattern: 'mypath1'",
      "Coverage report doesn't exist for pattern: 'mypath2'");
  }

  @Test
  void should_fallback_to_defaults_if_exist() throws IOException {
    createMavenReport(mavenPath1);
    createMavenReport(mavenPath2);

    Collection<Path> paths = provider.getPaths();
    assertThat(paths).containsOnly(baseDir.resolve(mavenPath1), baseDir.resolve(mavenPath2));
    assertThat(logTester.logs()).hasSize(1);
    assertThat(logTester.logs(LoggerLevel.INFO)).contains("'sonar.coverage.jacoco.xmlReportPaths' is not defined." +
      " Using default locations: target/site/jacoco/jacoco.xml,target/site/jacoco-it/jacoco.xml,build/reports/jacoco/test/jacocoTestReport.xml");
  }

  @Test
  void should_return_empty_if_nothing_specified_and_default_doesnt_exist() {
    Collection<Path> paths = provider.getPaths();
    assertThat(paths).isEmpty();
    assertThat(logTester.logs()).hasSize(1);
    assertThat(logTester.logs(LoggerLevel.INFO))
      .containsExactly("'sonar.coverage.jacoco.xmlReportPaths' is not defined." +
        " Using default locations: target/site/jacoco/jacoco.xml,target/site/jacoco-it/jacoco.xml,build/reports/jacoco/test/jacocoTestReport.xml");
  }
}
