/*
 * SonarQube JaCoCo Plugin
 * Copyright (C) 2018-2023 SonarSource SA
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

class ReportPathsProvider {
  private static final Logger LOG = Loggers.get(ReportPathsProvider.class);

  private static final String[] DEFAULT_PATHS = {"target/site/jacoco/jacoco.xml", "target/site/jacoco-it/jacoco.xml", "build/reports/jacoco/test/jacocoTestReport.xml"};
  static final String REPORT_PATHS_PROPERTY_KEY = "sonar.coverage.jacoco.xmlReportPaths";

  private final SensorContext context;

  ReportPathsProvider(SensorContext context) {
    this.context = context;
  }

  Collection<Path> getPaths() {
    Path baseDir = context.fileSystem().baseDir().toPath().toAbsolutePath();

    List<String> patternPathList = Stream.of(context.config().getStringArray(REPORT_PATHS_PROPERTY_KEY))
      .filter(pattern -> !pattern.isEmpty())
      .toList();

    Set<Path> reportPaths = new HashSet<>();
    if (!patternPathList.isEmpty()) {
      for (String patternPath : patternPathList) {
        List<Path> paths = WildcardPatternFileScanner.scan(baseDir, patternPath);
        if (paths.isEmpty() && patternPathList.size() > 1) {
          LOG.info("Coverage report doesn't exist for pattern: '{}'", patternPath);
        }
        reportPaths.addAll(paths);
      }
    }

    if (!reportPaths.isEmpty()) {
      return reportPaths;
    } else {
      if (!patternPathList.isEmpty()) {
        LOG.warn("No coverage report can be found with sonar.coverage.jacoco.xmlReportPaths='{}'. Using default locations: {}",
          String.join(",", patternPathList), String.join(",", DEFAULT_PATHS));
      } else {
        LOG.info("'sonar.coverage.jacoco.xmlReportPaths' is not defined. Using default locations: {}", String.join(",", DEFAULT_PATHS));
      }
      return Arrays.stream(DEFAULT_PATHS)
        .map(baseDir::resolve)
        .filter(Files::isRegularFile)
        .collect(Collectors.toSet());
    }
  }


}
