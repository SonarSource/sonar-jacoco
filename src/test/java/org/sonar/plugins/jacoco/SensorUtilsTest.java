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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.utils.log.LogTesterJUnit5;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.LoggerLevel;
import org.sonar.api.utils.log.Loggers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SensorUtilsTest {

  private static final Logger LOG = Loggers.get(SensorUtilsTest.class);

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

    SensorUtils.importReport(parser, locator, importer, null);

    verify(importer).importCoverage(sourceFile, inputFile);
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

    SensorUtils.importReports(Arrays.asList(invalidFile, validFile), locator, importer, LOG);

    String expectedErrorMessage = String.format(
            "Coverage report '%s' could not be read/imported. Error: java.lang.IllegalStateException: Invalid report: failed to parse integer from the attribute 'ci' for the sourcefile 'File.java' at line 6 column 61",
            invalidFile);

    assertThat(logTester.logs(LoggerLevel.INFO)).contains("Importing 2 report(s). Turn your logs in debug mode in order to see the exhaustive list.");

    assertThat(logTester.logs(LoggerLevel.ERROR)).contains(expectedErrorMessage);

    verify(importer, times(1)).importCoverage(any(), eq(inputFile));
  }

  @Test
  void do_nothing_if_file_not_found() {
    ModuleFileLocator locator = mock(ModuleFileLocator.class);
    ReportImporter importer = mock(ReportImporter.class);
    XmlReportParser parser = mock(XmlReportParser.class);
    XmlReportParser.SourceFile sourceFile = mock(XmlReportParser.SourceFile.class);

    when(parser.parse()).thenReturn(Collections.singletonList(sourceFile));
    SensorUtils.importReport(parser, locator, importer, null);

    verifyNoInteractions(importer);
  }
}
