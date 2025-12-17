/*
 * SonarQube JaCoCo Plugin
 * Copyright (C) 2018-2025 SonarSource SA
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

import java.util.List;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.utils.log.Logger;

class SensorUtils {
  private SensorUtils() {
    /* This class should not be instantiated */
  }

  static void importReport(XmlReportParser reportParser, FileLocator locator, ReportImporter importer, Logger logger) {
    List<XmlReportParser.SourceFile> sourceFiles = reportParser.parse();

    for (XmlReportParser.SourceFile sourceFile : sourceFiles) {
      // FIXME for the case of project sensor, we need the group
      InputFile inputFile = locator.getInputFile(sourceFile.packageName(), sourceFile.name());
      if (inputFile == null) {
        continue;
      }

      try {
        importer.importCoverage(sourceFile, inputFile);
      } catch (IllegalStateException e) {
        logger.error("Cannot import coverage information for file '{}', coverage data is invalid. Error: {}", inputFile, e);
      }
    }
  }
}
