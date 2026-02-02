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

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.sonar.api.batch.fs.InputFile;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SensorUtilsTest {

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
