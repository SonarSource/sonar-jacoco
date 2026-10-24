/*
 * SonarQube JaCoCo Plugin
 * Copyright (C) 2018-2026 SonarSource Sàrl
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

import com.sonarsource.scanner.engine.sensor.test.fixtures.SensorContextTester;
import com.sonarsource.scanner.engine.sensor.test.fixtures.TestFileSystem;
import com.sonarsource.scanner.engine.sensor.test.fixtures.TestInputFileBuilder;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.event.Level;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.notifications.AnalysisWarnings;
import org.sonar.api.testfixtures.log.LogTesterJUnit5;
import org.sonar.scanner.plugin.api.impl.config.MapSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class JacocoSensorTest {
  @TempDir
  Path temp;

  @RegisterExtension
  public LogTesterJUnit5 logTester = new LogTesterJUnit5();

  private AnalysisWarnings analysisWarnings = mock(AnalysisWarnings.class);
  private JacocoSensor sensor = new JacocoSensor(new ProjectCoverageContext(), analysisWarnings);

  @Test
  void describe_sensor() {
    SensorDescriptor descriptor = mock(SensorDescriptor.class);
    sensor.describe(descriptor);
    verify(descriptor).name("JaCoCo XML Report Importer");
  }

  @Test
  void do_not_index_files_when_no_report_was_found() throws IOException {
    File emptyFolderWithoutReport = Files.createDirectory(temp.resolve("empty")).toFile();
    SensorContextTester spiedContext = spy(SensorContextTester.create(emptyFolderWithoutReport));
    MapSettings settings = new MapSettings()
      .setProperty("sonar.moduleKey", "module")
      .setProperty("sonar.projectBaseDir", temp.toAbsolutePath().toString());
    spiedContext.setSettings(settings);
    TestFileSystem spiedFileSystem = spy(spiedContext.fileSystem());
    when(spiedContext.fileSystem()).thenReturn(spiedFileSystem);
    sensor.execute(spiedContext);
    // indexing all files in the filesystem is time consuming and should not be done if there no jacoco reports to import
    // one way to assert this is to ensure there's no calls on fileSystem.inputFiles(...)
    verify(spiedFileSystem, never()).inputFiles(any());
    var infoLevelLogs = logTester.getLogs(Level.INFO)
            .stream()
            .map(laa -> laa.getFormattedMsg())
            .toList();
    assertThat(infoLevelLogs).containsExactlyInAnyOrder(
      "'sonar.coverage.jacoco.xmlReportPaths' is not defined. Using default locations: target/site/jacoco/jacoco.xml,target/site/jacoco-it/jacoco.xml,build/reports/jacoco/test/jacocoTestReport.xml",
      "No report imported, no coverage information will be imported by JaCoCo XML Report Importer");
  }

  @Test
  void do_nothing_if_report_parse_failure() {
    SensorContextTester tester = SensorContextTester.create(temp);
    MapSettings settings = new MapSettings()
            .setProperty("sonar.moduleKey", "module")
            .setProperty("sonar.projectBaseDir", temp.toAbsolutePath().toString());
    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, "invalid.xml");
    tester.setSettings(settings);
    sensor.execute(tester);

    assertThat(logTester.logs(Level.INFO)).contains("No report imported, no coverage information will be imported by JaCoCo XML Report Importer");

    List<String> warningLogs = logTester.logs(Level.WARN);
    assertThat(warningLogs)
            .hasSize(1);
    assertThat(warningLogs.get(0)).startsWith("No coverage report can be found with sonar.coverage.jacoco.xmlReportPaths='invalid.xml'.");
    verify(analysisWarnings).addUnique(argThat(message ->
            message.startsWith("No coverage report can be found with sonar.coverage.jacoco.xmlReportPaths='invalid.xml'.")));
  }

  @Test
  void test_load_real_report() throws URISyntaxException, IOException {
    MapSettings settings = new MapSettings();
    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, "jacoco.xml");
    settings.setProperty("sonar.moduleKey", "module");
    settings.setProperty("sonar.projectBaseDir", temp.toAbsolutePath().toString());
    SensorContextTester tester = SensorContextTester.create(temp);
    tester.setSettings(settings);
    InputFile inputFile = TestInputFileBuilder
      .create("module", "org/sonarlint/cli/Main.java")
      .setLines(1000)
      .build();
    tester.fileSystem().add(inputFile);
    Path sample = load("jacoco.xml");
    Files.copy(sample, temp.resolve("jacoco.xml"));

    sensor.execute(tester);
    assertThat(tester.lineHits(inputFile.key(), 110)).isEqualTo(1);
    assertThat(tester.conditions(inputFile.key(), 110)).isEqualTo(2);
    assertThat(tester.coveredConditions(inputFile.key(), 110)).isEqualTo(1);

    assertThat(logTester.logs(Level.INFO)).contains("Importing 1 report(s). Turn your logs in debug mode in order to see the exhaustive list.");
  }

  @Test
  void import_failure_do_not_fail_analysis() throws URISyntaxException, IOException {
    MapSettings settings = new MapSettings();
    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, "invalid_line_number.xml");
    settings.setProperty("sonar.moduleKey", "module");
    settings.setProperty("sonar.projectBaseDir", temp.toAbsolutePath().toString());
    SensorContextTester tester = SensorContextTester.create(temp);
    tester.setSettings(settings);
    InputFile inputFile = TestInputFileBuilder
      .create("module", "org/sonarlint/cli/File.java")
      .setLines(1000)
      .build();
    tester.fileSystem().add(inputFile);
    Path sample = load("invalid_line_number.xml");
    Files.copy(sample, temp.resolve("invalid_line_number.xml"));

    sensor.execute(tester);

    assertThat(logTester.logs(Level.INFO)).contains("Importing 1 report(s). Turn your logs in debug mode in order to see the exhaustive list.");

    String expectedLogError = "Cannot import coverage information for file '" + inputFile
      + "', coverage data is invalid. Error: java.lang.IllegalStateException: Line 1001 is out of range in the file "
      + inputFile + " (lines: 1000)";
    assertThat(logTester.logs(Level.ERROR)).contains(expectedLogError);
  }

  @Test
  void sensor_should_not_fail_when_processing_an_aggregate_report() {
    var moduleBaseDir = temp.resolve("library").toAbsolutePath().toString();
    Path aggregateReportPath = Path.of("src", "test", "resources", "jacoco-aggregate.xml");
    MapSettings settings = new MapSettings()
            .setProperty("sonar.modules", "library")
            .setProperty("sonar.moduleKey", "library")
            .setProperty("sonar.projectBaseDir", moduleBaseDir) // Must be the base dir of the module
            .setProperty("sonar.coverage.jacoco.xmlReportPaths", aggregateReportPath.toAbsolutePath().toString());

    SensorContextTester context = SensorContextTester.create(temp);
    context.setSettings(settings);

    sensor.execute(context);
    assertThat(logTester.getLogs(Level.ERROR)).isEmpty();
  }

  @Test
  void parse_a_report_shared_by_several_modules_only_once() throws URISyntaxException, IOException {
    Files.copy(load("jacoco.xml"), temp.resolve("jacoco.xml"));
    List<Path> parsedReports = new ArrayList<>();
    ProjectCoverageContext projectCoverageContext = new ProjectCoverageContext(new XmlReportCache(reportPath -> {
      parsedReports.add(reportPath);
      return new XmlReportParser(reportPath).parse();
    }));
    JacocoSensor sharedSensor = new JacocoSensor(projectCoverageContext, analysisWarnings);
    InputFile mainFile = TestInputFileBuilder.create("module-a", "org/sonarlint/cli/Main.java").setLines(1000).build();
    InputFile statsFile = TestInputFileBuilder.create("module-b", "org/sonarlint/cli/Stats.java").setLines(1000).build();
    SensorContextTester moduleA = moduleContext("module-a", "jacoco.xml", mainFile);
    SensorContextTester moduleB = moduleContext("module-b", "jacoco.xml", statsFile);

    sharedSensor.execute(moduleA);
    sharedSensor.execute(moduleB);

    assertThat(parsedReports).containsExactly(temp.resolve("jacoco.xml"));
    assertThat(projectCoverageContext.getReportCache().size()).isEqualTo(1);
    assertThat(moduleA.lineHits(mainFile.key(), 110)).isEqualTo(1);
    assertThat(moduleA.conditions(mainFile.key(), 110)).isEqualTo(2);
    assertThat(moduleA.coveredConditions(mainFile.key(), 110)).isEqualTo(1);
    assertThat(moduleB.lineHits(statsFile.key(), 24)).isEqualTo(1);
    assertThat(logTester.logs(Level.INFO)).containsExactly(
      "Importing 1 report(s). Turn your logs in debug mode in order to see the exhaustive list.",
      "Coverage report '" + temp.resolve("jacoco.xml") + "': 35 of 36 files were not found in the analysed sources of 'module-a'."
        + " This is expected when a single aggregated report is imported by several modules. Enable debug logs for the full list.",
      "Importing 1 report(s). Turn your logs in debug mode in order to see the exhaustive list.",
      "Coverage report '" + temp.resolve("jacoco.xml") + "': 35 of 36 files were not found in the analysed sources of 'module-b'."
        + " This is expected when a single aggregated report is imported by several modules. Enable debug logs for the full list.");
  }

  @Test
  void import_the_same_coverage_from_a_shared_report_as_from_a_report_parsed_for_the_module_only() throws URISyntaxException, IOException {
    Files.copy(load("jacoco.xml"), temp.resolve("jacoco.xml"));
    JacocoSensor sharedSensor = new JacocoSensor(new ProjectCoverageContext(), analysisWarnings);
    InputFile mainFile = TestInputFileBuilder.create("module-a", "org/sonarlint/cli/Main.java").setLines(1000).build();
    InputFile statsFile = TestInputFileBuilder.create("module-b", "org/sonarlint/cli/Stats.java").setLines(1000).build();
    SensorContextTester sharedModuleB = moduleContext("module-b", "jacoco.xml", statsFile);
    SensorContextTester isolatedModuleB = moduleContext("module-b", "jacoco.xml", statsFile);

    sharedSensor.execute(moduleContext("module-a", "jacoco.xml", mainFile));
    sharedSensor.execute(sharedModuleB);
    new JacocoSensor(new ProjectCoverageContext(), analysisWarnings).execute(isolatedModuleB);

    assertThat(coverageOf(sharedModuleB, statsFile))
      .isNotEmpty()
      .isEqualTo(coverageOf(isolatedModuleB, statsFile));
  }

  @Test
  void report_an_invalid_shared_report_in_every_module() throws URISyntaxException, IOException {
    Files.copy(load("invalid_ci_in_line.xml"), temp.resolve("invalid.xml"));
    List<Path> parsedReports = new ArrayList<>();
    JacocoSensor sharedSensor = new JacocoSensor(new ProjectCoverageContext(new XmlReportCache(reportPath -> {
      parsedReports.add(reportPath);
      return new XmlReportParser(reportPath).parse();
    })), analysisWarnings);
    InputFile fileA = TestInputFileBuilder.create("module-a", "org/sonarlint/cli/File.java").setLines(1000).build();
    InputFile fileB = TestInputFileBuilder.create("module-b", "org/sonarlint/cli/File.java").setLines(1000).build();

    sharedSensor.execute(moduleContext("module-a", "invalid.xml", fileA));
    sharedSensor.execute(moduleContext("module-b", "invalid.xml", fileB));

    String expectedErrorMessage = "Coverage report '" + temp.resolve("invalid.xml")
      + "' could not be read/imported. Error: java.lang.IllegalStateException: Invalid report: failed to parse integer from the attribute 'ci' for the sourcefile 'File.java' at line 6 column 61";
    assertThat(parsedReports).hasSize(1);
    assertThat(logTester.logs(Level.ERROR)).containsExactly(expectedErrorMessage, expectedErrorMessage);
    verify(analysisWarnings, times(2)).addUnique(expectedErrorMessage);
  }

  private SensorContextTester moduleContext(String moduleKey, String reportPaths, InputFile inputFile) {
    MapSettings settings = new MapSettings()
      .setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, reportPaths)
      .setProperty("sonar.moduleKey", moduleKey)
      .setProperty("sonar.projectBaseDir", temp.toAbsolutePath().toString());
    SensorContextTester context = SensorContextTester.create(temp);
    context.setSettings(settings);
    context.fileSystem().add(inputFile);
    return context;
  }

  private static Map<Integer, List<Integer>> coverageOf(SensorContextTester context, InputFile inputFile) {
    Map<Integer, List<Integer>> coverage = new TreeMap<>();
    for (int line = 1; line <= inputFile.lines(); line++) {
      Integer hits = context.lineHits(inputFile.key(), line);
      if (hits != null) {
        coverage.put(line, Arrays.asList(hits, context.conditions(inputFile.key(), line), context.coveredConditions(inputFile.key(), line)));
      }
    }
    return coverage;
  }

  private Path load(String name) throws URISyntaxException {
    return Paths.get(this.getClass().getClassLoader().getResource(name).toURI());
  }

}
