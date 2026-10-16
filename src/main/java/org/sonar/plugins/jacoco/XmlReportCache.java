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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parses each JaCoCo XML report at most once per analysis.
 * <p>
 * An aggregated report is usually imported by every module of a multi-module project through {@code sonar.coverage.jacoco.xmlReportPaths}. Each module only keeps the
 * source files it owns, so parsing the whole report again for each of them is wasted work. The outcome of the first parse of a report, its source files or the parse
 * failure, is kept by absolute and normalized report path and handed to every module that imports the same report. A failure is rethrown to each of them, so that each
 * module still logs it and raises its analysis warning as before.
 */
class XmlReportCache {
  private static final Logger LOG = LoggerFactory.getLogger(XmlReportCache.class);

  private final Function<Path, List<XmlReportParser.SourceFile>> reportParser;
  private final Map<Path, ParsedReport> parsedReports = new HashMap<>();

  XmlReportCache() {
    this(reportPath -> new XmlReportParser(reportPath).parse());
  }

  XmlReportCache(Function<Path, List<XmlReportParser.SourceFile>> reportParser) {
    this.reportParser = reportParser;
  }

  /**
   * @return the source files declared by the report, parsed during the first call for this report path
   * @throws RuntimeException the exception thrown by the first parse of this report, if it failed
   */
  synchronized List<XmlReportParser.SourceFile> parse(Path reportPath) {
    Path key = reportPath.toAbsolutePath().normalize();
    ParsedReport parsedReport = parsedReports.get(key);
    if (parsedReport == null) {
      parsedReport = parseReport(reportPath);
      parsedReports.put(key, parsedReport);
    } else {
      LOG.debug("Report '{}' was already parsed during this analysis, reusing its content", reportPath);
    }
    return parsedReport.sourceFilesOrThrow();
  }

  /**
   * Releases the parsed reports. A report requested afterwards is parsed again.
   */
  synchronized void clear() {
    parsedReports.clear();
  }

  synchronized int size() {
    return parsedReports.size();
  }

  private ParsedReport parseReport(Path reportPath) {
    try {
      return new ParsedReport(Collections.unmodifiableList(reportParser.apply(reportPath)), null);
    } catch (RuntimeException e) {
      return new ParsedReport(null, e);
    }
  }

  private record ParsedReport(@Nullable List<XmlReportParser.SourceFile> sourceFiles, @Nullable RuntimeException failure) {
    List<XmlReportParser.SourceFile> sourceFilesOrThrow() {
      if (failure != null) {
        throw failure;
      }
      return sourceFiles;
    }
  }
}
