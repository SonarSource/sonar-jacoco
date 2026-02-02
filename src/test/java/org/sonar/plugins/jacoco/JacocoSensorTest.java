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

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.log.LogTesterJUnit5;
import org.sonar.api.utils.log.LoggerLevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport
class JacocoSensorTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @RegisterExtension
  public LogTesterJUnit5 logTester = new LogTesterJUnit5();

  private JacocoSensor sensor = new JacocoSensor(new ProjectCoverageContext());

  @Test
  void describe_sensor() {
    SensorDescriptor descriptor = mock(SensorDescriptor.class);
    sensor.describe(descriptor);
    verify(descriptor).name("JaCoCo XML Report Importer");
  }

  @Test
  void do_not_index_files_when_no_report_was_found() throws IOException {
    File emptyFolderWithoutReport = temp.newFolder();
    SensorContextTester spiedContext = spy(SensorContextTester.create(emptyFolderWithoutReport));
    MapSettings settings = new MapSettings()
      .setProperty("sonar.moduleKey", "module")
      .setProperty("sonar.projectBaseDir", temp.getRoot().getAbsolutePath());
    spiedContext.setSettings(settings);
    DefaultFileSystem spiedFileSystem = spy(spiedContext.fileSystem());
    when(spiedContext.fileSystem()).thenReturn(spiedFileSystem);
    sensor.execute(spiedContext);
    // indexing all files in the filesystem is time consuming and should not be done if there no jacoco reports to import
    // one way to assert this is to ensure there's no calls on fileSystem.inputFiles(...)
    verify(spiedFileSystem, never()).inputFiles(any());
    var infoLevelLogs = logTester.getLogs(LoggerLevel.INFO)
            .stream()
            .map(laa -> laa.getFormattedMsg())
            .toList();
    assertThat(infoLevelLogs).containsExactlyInAnyOrder(
      "'sonar.coverage.jacoco.xmlReportPaths' is not defined. Using default locations: target/site/jacoco/jacoco.xml,target/site/jacoco-it/jacoco.xml,build/reports/jacoco/test/jacocoTestReport.xml",
      "No report imported, no coverage information will be imported by JaCoCo XML Report Importer");
  }

  @Test
  void do_nothing_if_report_parse_failure() {
    ModuleFileLocator locator = mock(ModuleFileLocator.class);
    ReportImporter importer = mock(ReportImporter.class);

    sensor.importReports(Collections.singletonList(Paths.get("invalid.xml")), locator, importer);

    assertThat(logTester.logs(LoggerLevel.INFO)).contains("Importing 1 report(s). Turn your logs in debug mode in order to see the exhaustive list.");

    assertThat(logTester.logs(LoggerLevel.ERROR)).hasSize(1);
    assertThat(logTester.logs(LoggerLevel.ERROR)).allMatch(s -> s.startsWith("Coverage report 'invalid.xml' could not be read/imported"));

    verifyNoInteractions(locator, importer);
  }

  @Test
  void parse_failure_do_not_fail_analysis() {
    ModuleFileLocator locator = mock(ModuleFileLocator.class);
    ReportImporter importer = mock(ReportImporter.class);
    InputFile inputFile = mock(InputFile.class);
    Path baseDir = Paths.get("src", "test", "resources");
    Path invalidFile = baseDir.resolve("invalid_ci_in_line.xml");
    Path validFile = baseDir.resolve("jacoco.xml");

    when(locator.getInputFile(null, "org/sonarlint/cli", "Stats.java")).thenReturn(inputFile);

    sensor.importReports(Arrays.asList(invalidFile, validFile), locator, importer);

    String expectedErrorMessage = String.format(
      "Coverage report '%s' could not be read/imported. Error: java.lang.IllegalStateException: Invalid report: failed to parse integer from the attribute 'ci' for the sourcefile 'File.java' at line 6 column 61",
      invalidFile.toString());

    assertThat(logTester.logs(LoggerLevel.INFO)).contains("Importing 2 report(s). Turn your logs in debug mode in order to see the exhaustive list.");

    assertThat(logTester.logs(LoggerLevel.ERROR)).contains(expectedErrorMessage);

    verify(importer, times(1)).importCoverage(any(), eq(inputFile));
  }

  @Test
  void test_load_real_report() throws URISyntaxException, IOException {
    MapSettings settings = new MapSettings();
    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, "jacoco.xml");
    settings.setProperty("sonar.moduleKey", "module");
    settings.setProperty("sonar.projectBaseDir", temp.getRoot().getAbsolutePath());
    SensorContextTester tester = SensorContextTester.create(temp.getRoot());
    tester.setSettings(settings);
    InputFile inputFile = TestInputFileBuilder
      .create("module", "org/sonarlint/cli/Main.java")
      .setLines(1000)
      .build();
    tester.fileSystem().add(inputFile);
    Path sample = load("jacoco.xml");
    Files.copy(sample, temp.getRoot().toPath().resolve("jacoco.xml"));

    sensor.execute(tester);
    assertThat(tester.lineHits(inputFile.key(), 110)).isEqualTo(1);
    assertThat(tester.conditions(inputFile.key(), 110)).isEqualTo(2);
    assertThat(tester.coveredConditions(inputFile.key(), 110)).isEqualTo(1);

    assertThat(logTester.logs(LoggerLevel.INFO)).contains("Importing 1 report(s). Turn your logs in debug mode in order to see the exhaustive list.");
  }

  @Test
  void import_failure_do_not_fail_analysis() throws URISyntaxException, IOException {
    MapSettings settings = new MapSettings();
    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, "invalid_line_number.xml");
    settings.setProperty("sonar.moduleKey", "module");
    settings.setProperty("sonar.projectBaseDir", temp.getRoot().getAbsolutePath());
    SensorContextTester tester = SensorContextTester.create(temp.getRoot());
    tester.setSettings(settings);
    InputFile inputFile = TestInputFileBuilder
      .create("module", "org/sonarlint/cli/File.java")
      .setLines(1000)
      .build();
    tester.fileSystem().add(inputFile);
    Path sample = load("invalid_line_number.xml");
    Files.copy(sample, temp.getRoot().toPath().resolve("invalid_line_number.xml"));

    sensor.execute(tester);

    assertThat(logTester.logs(LoggerLevel.INFO)).contains("Importing 1 report(s). Turn your logs in debug mode in order to see the exhaustive list.");

    String expectedLogError = String.format(
      "Cannot import coverage information for file '%s', coverage data is invalid. Error: java.lang.IllegalStateException: Line 1001 is out of range in the file %s (lines: 1000)",
      inputFile,
      inputFile);
    assertThat(logTester.logs(LoggerLevel.ERROR)).contains(expectedLogError);
  }

  @Test
  void sensor_should_not_fail_when_processing_an_aggregate_report() {
    var moduleBaseDir = temp.getRoot().toPath().resolve("library").toAbsolutePath().toString();
    Path aggregateReportPath = Path.of("src", "test", "resources", "jacoco-aggregate.xml");
    MapSettings settings = new MapSettings()
            .setProperty("sonar.modules", "library")
            .setProperty("sonar.moduleKey", "library")
            .setProperty("sonar.projectBaseDir", moduleBaseDir) // Must be the base dir of the module
            .setProperty("sonar.coverage.jacoco.xmlReportPaths", aggregateReportPath.toAbsolutePath().toString());

    SensorContextTester context = SensorContextTester.create(temp.getRoot());
    context.setSettings(settings);

    sensor.execute(context);
    assertThat(logTester.getLogs(LoggerLevel.ERROR)).isNull();
  }

  private Path load(String name) throws URISyntaxException {
    return Paths.get(this.getClass().getClassLoader().getResource(name).toURI());
  }

}
