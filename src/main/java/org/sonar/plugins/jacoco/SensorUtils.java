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
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.notifications.AnalysisWarnings;

class SensorUtils {
  static final String NOTHING_MATCHED_ANALYSIS_WARNING = "Some JaCoCo coverage reports could not be matched to any analysed source file."
    + " No coverage was imported from them; see the analysis logs for details.";

  private SensorUtils() {
    /* This class should not be instantiated */
  }

  static void importReports(Collection<Path> reportPaths, FileLocator locator, ReportImporter importer, Logger logger, AnalysisWarnings analysisWarnings, String contextLabel) {
    logger.info("Importing {} report(s). Turn your logs in debug mode in order to see the exhaustive list.", reportPaths.size());

    for (Path reportPath : reportPaths) {
      logger.debug("Reading report '{}'", reportPath);
      try {
        ImportSummary summary = importReport(new XmlReportParser(reportPath), locator, importer, logger);
        logImportSummary(summary, reportPath, locator, logger, analysisWarnings, contextLabel);
      } catch (Exception e) {
        String message = String.format("Coverage report '%s' could not be read/imported. Error: %s: %s", reportPath, e.getClass().getName(), e.getMessage());
        logger.error(message);
        analysisWarnings.addUnique(message);
      }
    }
  }

  private static void logImportSummary(ImportSummary summary, Path reportPath, FileLocator locator, Logger logger, AnalysisWarnings analysisWarnings, String contextLabel) {
    if (summary.notFound == 0) {
      return;
    }
    if (summary.notFound < summary.total) {
      logger.info("Coverage report '{}': {} of {} files were not found in the analysed sources of '{}'."
        + " This is expected when a single aggregated report is imported by several modules."
        + " Enable debug logs for the full list.", reportPath, summary.notFound, summary.total, contextLabel);
    } else if (locator.hasFilesCoverableByJacoco()) {
      logger.warn(
        "None of the {} files in coverage report '{}' could be matched to the analysed sources of '{}'. No coverage was imported from this report.",
        summary.total,
        reportPath,
        contextLabel
      );
      analysisWarnings.addUnique(NOTHING_MATCHED_ANALYSIS_WARNING);
    } else {
      // A context without any indexed file, such as an aggregator module, matches nothing by construction: that is not a failed import.
      logger.debug("Coverage report '{}' was not imported into '{}', which has no source file to analyze.", reportPath, contextLabel);
    }
  }

  static ImportSummary importReport(XmlReportParser reportParser, FileLocator locator, ReportImporter importer, Logger logger) {
    List<XmlReportParser.SourceFile> sourceFiles = reportParser.parse();
    int notFound = 0;

    for (XmlReportParser.SourceFile sourceFile : sourceFiles) {
      InputFile inputFile = locator.getInputFile(sourceFile.groupName(), sourceFile.packageName(), sourceFile.name());
      if (inputFile == null) {
        notFound++;
        String group = sourceFile.groupName() == null ? "" : (" (group '" + sourceFile.groupName() + "')");
        logger.debug("File '{}/{}'{} not found in the analysed sources", sourceFile.packageName(), sourceFile.name(), group);
        continue;
      }

      try {
        importer.importCoverage(sourceFile, inputFile);
      } catch (IllegalStateException e) {
        logger.error("Cannot import coverage information for file '{}', coverage data is invalid. Error: {}: {}", inputFile, e.getClass().getName(), e.getMessage());
      }
    }

    return new ImportSummary(sourceFiles.size(), notFound);
  }

  /**
   * How many source files a single report declared, and how many of them could not be matched to an analysed file.
   */
  static class ImportSummary {
    final int total;
    final int notFound;

    ImportSummary(int total, int notFound) {
      this.total = total;
      this.notFound = notFound;
    }
  }
}
