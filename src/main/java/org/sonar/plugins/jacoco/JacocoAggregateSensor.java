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

import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.scanner.sensor.ProjectSensor;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

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
    Path reportPath = null;
    try {
      reportPath = new ReportPathsProvider(context).getAggregateReportPath();
    } catch (FileNotFoundException e) {
      LOG.error(String.format("The aggregate JaCoCo sensor will stop: %s", e.getMessage()));
      return;
    }
    if (reportPath == null) {
      LOG.debug("No aggregate XML report found. No coverage coverage information will be added at project level.");
      return;
    }
    Iterable<InputFile> inputFiles = context.fileSystem().inputFiles(context.fileSystem().predicates().all());
    Stream<InputFile> kotlinInputFileStream = StreamSupport.stream(inputFiles.spliterator(), false).filter(f -> "kotlin".equals(f.language()));
    FileLocator locator = new FileLocator(inputFiles, new KotlinFileLocator(kotlinInputFileStream));
    locator.setProjectCoverageContext(projectCoverageContext);
    ReportImporter importer = new ReportImporter(context);

    LOG.info("Importing aggregate report {}.", reportPath);
    SensorUtils.importReport(new XmlReportParser(reportPath), locator, importer, LOG);
  }
}
