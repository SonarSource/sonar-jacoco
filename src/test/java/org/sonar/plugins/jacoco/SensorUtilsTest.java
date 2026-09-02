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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.notifications.AnalysisWarnings;
import org.sonar.api.testfixtures.log.LogTesterJUnit5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SensorUtilsTest {

  private static final Logger LOG = LoggerFactory.getLogger(SensorUtilsTest.class);
  private static final Path RESOURCES_DIR = Paths.get("src", "test", "resources");
  /** Declares a single source file, org/sonarlint/cli/File.java. */
  private static final Path SINGLE_FILE_REPORT = RESOURCES_DIR.resolve("simple.xml");
  /** Declares 36 source files, the first of which is org/sonarlint/cli/Stats.java. */
  private static final Path MANY_FILES_REPORT = RESOURCES_DIR.resolve("jacoco.xml");

  @RegisterExtension
  public LogTesterJUnit5 logTester = new LogTesterJUnit5();

  @Test
  void import_coverage() {
    ModuleFileLocator locator = mock(ModuleFileLocator.class);
    ReportImporter importer = mock(ReportImporter.class);
    XmlReportParser parser = mock(XmlReportParser.class);
    InputFile inputFile = mock(InputFile.class);

    XmlReportParser.SourceFile sourceFile = new XmlReportParser.SourceFile("package", "File.java");
    sourceFile.lines().add(new XmlReportParser.Line(1, 0, 1, 0, 0));

    when(parser.parse()).thenReturn(Collections.singletonList(sourceFile));
    when(locator.getInputFile(null,"package", "File.java")).thenReturn(inputFile);

    SensorUtils.importReport(parser, locator, importer, LOG);

    verify(importer).importCoverage(sourceFile, inputFile);
  }

  @Test
  void parse_failure_do_not_fail_analysis() {
    ModuleFileLocator locator = mock(ModuleFileLocator.class);
    ReportImporter importer = mock(ReportImporter.class);
    InputFile inputFile = mock(InputFile.class);
    Path invalidFile = RESOURCES_DIR.resolve("invalid_ci_in_line.xml");
    Path validFile = MANY_FILES_REPORT;

    when(locator.getInputFile(null, "org/sonarlint/cli", "Stats.java")).thenReturn(inputFile);

    AnalysisWarnings analysisWarnings = mock(AnalysisWarnings.class);
    SensorUtils.importReports(Arrays.asList(invalidFile, validFile), locator, importer, LOG, analysisWarnings, "my-module");

    String expectedErrorMessage = String.format(
            "Coverage report '%s' could not be read/imported. Error: java.lang.IllegalStateException: Invalid report: failed to parse integer from the attribute 'ci' for the sourcefile 'File.java' at line 6 column 61",
            invalidFile);

    assertThat(logTester.logs(Level.INFO)).contains("Importing 2 report(s). Turn your logs in debug mode in order to see the exhaustive list.");

    assertThat(logTester.logs(Level.ERROR)).contains(expectedErrorMessage);
    verify(analysisWarnings).addUnique(expectedErrorMessage);

    verify(importer, times(1)).importCoverage(any(), eq(inputFile));
  }

  @Test
  void do_nothing_if_file_not_found() {
    logTester.setLevel(Level.DEBUG);
    ModuleFileLocator locator = mock(ModuleFileLocator.class);
    ReportImporter importer = mock(ReportImporter.class);
    XmlReportParser parser = mock(XmlReportParser.class);
    XmlReportParser.SourceFile sourceFile = mock(XmlReportParser.SourceFile.class);

    when(parser.parse()).thenReturn(Collections.singletonList(sourceFile));
    SensorUtils.importReport(parser, locator, importer, LOG);

    // One line per unresolved file would flood the logs of a multi-module build importing an aggregated report, so it stays at debug level.
    assertThat(logTester.logs(Level.DEBUG)).anySatisfy(logMessage -> assertThat(logMessage).contains("File 'null/null' not found in the analysed sources"));
    assertThat(logTester.logs(Level.WARN)).isEmpty();
  }

  @Test
  void log_a_single_info_summary_when_only_some_files_are_not_found() {
    ModuleFileLocator locator = mock(ModuleFileLocator.class);
    ReportImporter importer = mock(ReportImporter.class);
    AnalysisWarnings analysisWarnings = mock(AnalysisWarnings.class);
    InputFile inputFile = mock(InputFile.class);
    when(locator.hasFilesCoverableByJacoco()).thenReturn(true);
    when(locator.getInputFile(null, "org/sonarlint/cli", "Stats.java")).thenReturn(inputFile);

    SensorUtils.importReports(Collections.singletonList(MANY_FILES_REPORT), locator, importer, LOG, analysisWarnings, "my-module");

    assertThat(logTester.logs(Level.INFO)).contains(String.format(
            "Coverage report '%s': 35 of 36 files were not found in the analysed sources of 'my-module'."
                    + " This is expected when a single aggregated report is imported by several modules."
                    + " Enable debug logs for the full list.",
            MANY_FILES_REPORT));
    assertThat(logTester.logs(Level.WARN)).isEmpty();
    verifyNoInteractions(analysisWarnings);
  }

  @Test
  void log_nothing_extra_when_all_files_are_found() {
    ModuleFileLocator locator = mock(ModuleFileLocator.class);
    ReportImporter importer = mock(ReportImporter.class);
    AnalysisWarnings analysisWarnings = mock(AnalysisWarnings.class);
    InputFile inputFile = mock(InputFile.class);
    when(locator.hasFilesCoverableByJacoco()).thenReturn(true);
    when(locator.getInputFile(null, "org/sonarlint/cli", "File.java")).thenReturn(inputFile);

    SensorUtils.importReports(Collections.singletonList(SINGLE_FILE_REPORT), locator, importer, LOG, analysisWarnings, "my-module");

    assertThat(logTester.logs(Level.WARN)).isEmpty();
    assertThat(logTester.logs(Level.INFO)).containsExactly("Importing 1 report(s). Turn your logs in debug mode in order to see the exhaustive list.");
    verifyNoInteractions(analysisWarnings);
  }

  @Test
  void warn_once_after_all_ambiguous_report_entries_are_skipped() {
    ProjectFileLocator locator = mock(ProjectFileLocator.class);
    ReportImporter importer = mock(ReportImporter.class);
    AnalysisWarnings analysisWarnings = mock(AnalysisWarnings.class);
    when(locator.skippedAmbiguousReportEntries()).thenReturn(0, 1, 1, 2);

    SensorUtils.importReports(Arrays.asList(SINGLE_FILE_REPORT, SINGLE_FILE_REPORT), locator, importer, LOG, analysisWarnings, "my-project");

    assertThat(logTester.logs(Level.WARN)).containsExactly(
            "Coverage was not imported for 2 JaCoCo report source file(s) because each matched multiple project source files."
                    + " Enable debug logs for the full list.");
    assertThat(logTester.logs(Level.INFO)).containsExactly("Importing 2 report(s). Turn your logs in debug mode in order to see the exhaustive list.");
    assertThat(logTester.logs(Level.DEBUG)).noneMatch(message -> message.contains("not found in the analysed sources"));
    verify(analysisWarnings, times(2)).addUnique(SensorUtils.AMBIGUOUS_MATCH_ANALYSIS_WARNING);
  }

  @Test
  void warn_once_when_no_file_of_a_report_is_found() {
    ModuleFileLocator locator = mock(ModuleFileLocator.class);
    ReportImporter importer = mock(ReportImporter.class);
    AnalysisWarnings analysisWarnings = mock(AnalysisWarnings.class);
    when(locator.hasFilesCoverableByJacoco()).thenReturn(true);

    SensorUtils.importReports(Collections.singletonList(SINGLE_FILE_REPORT), locator, importer, LOG, analysisWarnings, "my-module");

    assertThat(logTester.logs(Level.WARN)).containsExactly(String.format(
            "None of the 1 files in coverage report '%s' could be matched to the analysed sources of 'my-module'."
                    + " No coverage was imported from this report.",
            SINGLE_FILE_REPORT));
    verify(analysisWarnings).addUnique(SensorUtils.NOTHING_MATCHED_ANALYSIS_WARNING);
  }

  @Test
  void raise_the_same_analysis_warning_message_for_every_report_that_matches_nothing() {
    ModuleFileLocator locator = mock(ModuleFileLocator.class);
    ReportImporter importer = mock(ReportImporter.class);
    AnalysisWarnings analysisWarnings = mock(AnalysisWarnings.class);
    when(locator.hasFilesCoverableByJacoco()).thenReturn(true);

    SensorUtils.importReports(Arrays.asList(SINGLE_FILE_REPORT, MANY_FILES_REPORT), locator, importer, LOG, analysisWarnings, "my-module");

    // The detail stays in the logs, one line per report, while the analysis warning is a constant: addUnique then
    // collapses the two calls below into a single entry in the UI, whatever the number of modules and reports.
    assertThat(logTester.logs(Level.WARN)).hasSize(2);
    verify(analysisWarnings, times(2)).addUnique(SensorUtils.NOTHING_MATCHED_ANALYSIS_WARNING);
  }

  @Test
  void do_not_warn_when_the_module_has_no_indexed_file() {
    ModuleFileLocator locator = mock(ModuleFileLocator.class);
    ReportImporter importer = mock(ReportImporter.class);
    AnalysisWarnings analysisWarnings = mock(AnalysisWarnings.class);
    // An aggregator module indexes only its build script, and a test-only module only test sources: neither can appear in a JaCoCo report, so matching nothing is expected rather
    // than a failed import.
    when(locator.hasFilesCoverableByJacoco()).thenReturn(false);

    SensorUtils.importReports(Collections.singletonList(SINGLE_FILE_REPORT), locator, importer, LOG, analysisWarnings, "aggregator");

    assertThat(logTester.logs(Level.WARN)).isEmpty();
    verifyNoInteractions(analysisWarnings);
  }
}
