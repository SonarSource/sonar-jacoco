/*
 * SonarQube JaCoCo Plugin
 * Copyright (C) 2018-2020 SonarSource SA
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
import java.util.Set;
import java.util.stream.Collectors;
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
    Set<Path> reportPaths = new HashSet<>();

    for (String pathName : context.config().getStringArray(REPORT_PATHS_PROPERTY_KEY)) {
      Path path = toAbsolutePath(pathName);
      if (reportExists(path)) {
        reportPaths.add(path);
      } else {
        LOG.warn("Coverage report doesn't exist: '{}'", path);
      }
    }

    if (!reportPaths.isEmpty()) {
      return reportPaths;
    } else {

      LOG.info("Can not find JaCoCo reports from property 'sonar.coverage.jacoco.xmlReportPaths'. Using default locations: {}", String.join(",", DEFAULT_PATHS));

      return Arrays.stream(DEFAULT_PATHS)
        .map(this::toAbsolutePath)
        .filter(ReportPathsProvider::reportExists)
        .collect(Collectors.toSet());
    }
  }

  private Path toAbsolutePath(String reportPath) {
    return context.fileSystem().baseDir().toPath().resolve(reportPath);
  }

  private static boolean reportExists(Path path) {
    return Files.isRegularFile(path);
  }

}
