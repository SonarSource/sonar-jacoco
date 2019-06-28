/*
 * SonarQube JaCoCo Plugin
 * Copyright (C) 2018-2019 SonarSource SA
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
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.log.LogTester;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

public class JacocoSensorTest {
  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public LogTester logTester = new LogTester();

  private JacocoSensor sensor = new JacocoSensor();

  @Test
  public void describe_sensor() {
    SensorDescriptor descriptor = mock(SensorDescriptor.class);
    sensor.describe(descriptor);
    verify(descriptor).name("JaCoCo XML Report Importer");
  }

  @Test
  public void import_coverage() {
    FileLocator locator = mock(FileLocator.class);
    ReportImporter importer = mock(ReportImporter.class);
    XmlReportParser parser = mock(XmlReportParser.class);
    InputFile inputFile = mock(InputFile.class);

    XmlReportParser.SourceFile sourceFile = new XmlReportParser.SourceFile("package", "File.java");
    sourceFile.lines().add(new XmlReportParser.Line(1, 0, 1, 0, 0));

    when(parser.parse()).thenReturn(Collections.singletonList(sourceFile));
    when(locator.getInputFile("package", "File.java")).thenReturn(inputFile);

    sensor.importReport(parser, locator, importer);

    verify(importer).importCoverage(sourceFile, inputFile);
  }

  @Test
  public void do_nothing_if_no_path() {
    ReportPathsProvider reportPathsProvider = mock(ReportPathsProvider.class);
    FileLocator locator = mock(FileLocator.class);
    ReportImporter importer = mock(ReportImporter.class);

    when(reportPathsProvider.getPaths()).thenReturn(Collections.emptySet());
    sensor.importReports(reportPathsProvider, locator, importer);

    verify(reportPathsProvider).getPaths();
    verifyZeroInteractions(locator, importer);
  }

  @Test
  public void do_nothing_if_report_doesnt_exist() {
    ReportPathsProvider reportPathsProvider = mock(ReportPathsProvider.class);
    FileLocator locator = mock(FileLocator.class);
    ReportImporter importer = mock(ReportImporter.class);

    when(reportPathsProvider.getPaths()).thenReturn(Collections.singletonList(Paths.get("invalid.xml")));
    sensor.importReports(reportPathsProvider, locator, importer);

    verify(reportPathsProvider).getPaths();
    assertThat(logTester.logs()).contains("Report doesn't exist: 'invalid.xml'");
    verifyZeroInteractions(locator, importer);
  }

  @Test
  public void parse_failure_do_not_fail_analysis() {
    ReportPathsProvider reportPathsProvider = mock(ReportPathsProvider.class);
    FileLocator locator = mock(FileLocator.class);
    ReportImporter importer = mock(ReportImporter.class);
    InputFile inputFile = mock(InputFile.class);
    Path baseDir = Paths.get("src", "test", "resources");
    Path invalidFile = baseDir.resolve("invalid_ci_in_line.xml");
    Path validFile = baseDir.resolve("jacoco.xml");

    when(locator.getInputFile("org/sonarlint/cli", "Stats.java")).thenReturn(inputFile);
    when(reportPathsProvider.getPaths()).thenReturn(Arrays.asList(invalidFile, validFile));

    sensor.importReports(reportPathsProvider, locator, importer);

    verify(reportPathsProvider).getPaths();
    String expectedErrorMessage = String.format(
      "Coverage report '%s' could not be read/imported. Error: java.lang.IllegalStateException: Invalid report: failed to parse integer from the attribute 'ci' for the sourcefile 'File.java' at line 6 column 61",
      invalidFile.toString());
    assertThat(logTester.logs()).contains(expectedErrorMessage);
    verify(importer, times(1)).importCoverage(any(), eq(inputFile));
  }

  @Test
  public void do_nothing_if_file_not_found() {
    FileLocator locator = mock(FileLocator.class);
    ReportImporter importer = mock(ReportImporter.class);
    XmlReportParser parser = mock(XmlReportParser.class);
    XmlReportParser.SourceFile sourceFile = mock(XmlReportParser.SourceFile.class);

    when(parser.parse()).thenReturn(Collections.singletonList(sourceFile));
    sensor.importReport(parser, locator, importer);

    verifyZeroInteractions(importer);
  }

  @Test
  public void test_load_real_report() throws URISyntaxException, IOException {
    MapSettings settings = new MapSettings();
    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, "jacoco.xml");
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
  }

  @Test
  public void import_failure_do_not_fail_analysis() throws URISyntaxException, IOException {
    MapSettings settings = new MapSettings();
    settings.setProperty(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY, "invalid_line_number.xml");
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

    String expectedLogError = String.format(
      "Cannot import coverage information for file '%s', coverage data is invalid. Error: java.lang.IllegalStateException: Line 1001 is out of range in the file %s (lines: 1000)",
      inputFile,
      inputFile);
    assertThat(logTester.logs()).contains(expectedLogError);
  }

  private Path load(String name) throws URISyntaxException {
    return Paths.get(this.getClass().getClassLoader().getResource(name).toURI());
  }

}
