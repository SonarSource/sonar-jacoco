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
import java.util.List;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JacocoSensorTest {
  @TempDir
  Path temp;

  @RegisterExtension
  public LogTesterJUnit5 logTester = new LogTesterJUnit5();

  private AnalysisWarnings analysisWarnings = mock(AnalysisWarnings.class);
  private ProjectCoverageContext projectCoverageContext = new ProjectCoverageContext();
  private JacocoSensor sensor = new JacocoSensor(projectCoverageContext, analysisWarnings);

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
    spiedContext.fileSystem().add(TestInputFileBuilder.create("module", "src/main/java/Foo.java").build());
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
    tester.fileSystem().add(TestInputFileBuilder.create("module", "src/main/java/Foo.java").build());
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
  void skip_module_without_any_file() throws IOException {
    logTester.setLevel(Level.DEBUG);
    Files.writeString(temp.resolve("jacoco.xml"), "this is not a JaCoCo report");
    SensorContextTester spiedContext = spy(SensorContextTester.create(temp));
    spiedContext.setSettings(moduleSettings("aggregator", "jacoco.xml"));
    TestFileSystem spiedFileSystem = spy(spiedContext.fileSystem());
    when(spiedContext.fileSystem()).thenReturn(spiedFileSystem);

    sensor.execute(spiedContext);

    verify(spiedFileSystem, never()).inputFiles(any());
    verifyNoInteractions(analysisWarnings);
    assertThat(logTester.logs(Level.DEBUG)).contains("No main file in module 'aggregator', skipping the import of JaCoCo reports");
    assertThat(logTester.logs(Level.INFO)).isEmpty();
    assertThat(logTester.logs(Level.WARN)).isEmpty();
    assertThat(logTester.logs(Level.ERROR)).isEmpty();
    assertThat(projectCoverageContext.getModuleContexts())
      .extracting(moduleContext -> moduleContext.name)
      .containsExactly("aggregator");
  }

  @Test
  void skip_module_with_only_test_files() throws IOException {
    logTester.setLevel(Level.DEBUG);
    Files.writeString(temp.resolve("jacoco.xml"), "this is not a JaCoCo report");
    SensorContextTester tester = SensorContextTester.create(temp);
    tester.setSettings(moduleSettings("tests", "jacoco.xml"));
    tester.fileSystem().add(TestInputFileBuilder
      .create("tests", "org/sonarlint/cli/Main.java")
      .setType(InputFile.Type.TEST)
      .setLines(1000)
      .build());

    sensor.execute(tester);

    verifyNoInteractions(analysisWarnings);
    assertThat(logTester.logs(Level.DEBUG)).contains("No main file in module 'tests', skipping the import of JaCoCo reports");
    assertThat(logTester.logs(Level.ERROR)).isEmpty();
    assertThat(projectCoverageContext.getModuleContexts())
      .extracting(moduleContext -> moduleContext.name)
      .containsExactly("tests");
  }

  @Test
  void skip_module_without_main_file_does_not_warn_about_missing_reports() {
    logTester.setLevel(Level.DEBUG);
    SensorContextTester tester = SensorContextTester.create(temp);
    tester.setSettings(moduleSettings("aggregator", "target/site/jacoco/jacoco.xml"));

    sensor.execute(tester);

    verifyNoInteractions(analysisWarnings);
    assertThat(logTester.logs(Level.WARN)).isEmpty();
    assertThat(logTester.logs(Level.DEBUG)).contains("No main file in module 'aggregator', skipping the import of JaCoCo reports");
  }

  @Test
  void import_coverage_into_main_file_of_a_language_not_known_by_jacoco() throws IOException {
    logTester.setLevel(Level.DEBUG);
    Files.writeString(temp.resolve("jacoco.xml"), """
      <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
      <report name="scala-module">
        <package name="org/example">
          <sourcefile name="Hello.scala">
            <line nr="3" mi="0" ci="2" mb="1" cb="1"/>
          </sourcefile>
        </package>
      </report>
      """);
    SensorContextTester tester = SensorContextTester.create(temp);
    tester.setSettings(moduleSettings("scala-module", "jacoco.xml"));
    InputFile scalaFile = TestInputFileBuilder
      .create("scala-module", "src/main/scala/org/example/Hello.scala")
      .setLanguage("scala")
      .setLines(10)
      .build();
    tester.fileSystem().add(scalaFile);

    sensor.execute(tester);

    assertThat(tester.lineHits(scalaFile.key(), 3)).isEqualTo(1);
    assertThat(tester.conditions(scalaFile.key(), 3)).isEqualTo(2);
    assertThat(tester.coveredConditions(scalaFile.key(), 3)).isEqualTo(1);
    assertThat(logTester.logs(Level.DEBUG)).noneMatch(log -> log.startsWith("No main file in module"));
  }

  private MapSettings moduleSettings(String moduleKey, String reportPaths) {
    return new MapSettings()
      .setProperty("sonar.moduleKey", moduleKey)
      .setProperty("sonar.projectBaseDir", temp.toAbsolutePath().toString())
      .setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, reportPaths);
  }

  private Path load(String name) throws URISyntaxException {
    return Paths.get(this.getClass().getClassLoader().getResource(name).toURI());
  }

}
