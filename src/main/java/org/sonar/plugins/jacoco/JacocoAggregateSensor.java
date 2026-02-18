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
import java.util.Set;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.scanner.sensor.ProjectSensor;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

import static org.sonar.plugins.jacoco.SensorUtils.importReports;

public class JacocoAggregateSensor implements ProjectSensor {
  private static final Logger LOG = Loggers.get(JacocoAggregateSensor.class);
  private final ProjectCoverageContext projectCoverageContext;

  public JacocoAggregateSensor(ProjectCoverageContext projectCoverageContext) {
    this.projectCoverageContext = projectCoverageContext;
  }

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor.name("JaCoCo Aggregate XML Report Importer");
  }

  @Override
  public void execute(SensorContext context) {
    this.projectCoverageContext.setProjectBaseDir(Paths.get(context.config().get("sonar.projectBaseDir").get()));
    Set<Path> reportPaths = new ReportPathsProvider(context).getAggregateReportPaths();
    if (reportPaths.isEmpty()) {
      LOG.debug("No aggregate XML report found. No coverage coverage information will be added at project level.");
      return;
    }
    Iterable<InputFile> inputFiles = context.fileSystem().inputFiles(context.fileSystem().predicates().all());
    Stream<InputFile> kotlinInputFileStream = StreamSupport.stream(inputFiles.spliterator(), false).filter(f -> "kotlin".equals(f.language()));
    FileLocator locator = new ProjectFileLocator(inputFiles, new KotlinFileLocator(kotlinInputFileStream), projectCoverageContext);
    ReportImporter importer = new ReportImporter(context);

    importReports(reportPaths, locator, importer, LOG);
  }
}
