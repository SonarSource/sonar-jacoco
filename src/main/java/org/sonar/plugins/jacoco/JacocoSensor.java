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
import java.util.Collection;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public class JacocoSensor implements Sensor {
  private static final Logger LOG = Loggers.get(JacocoSensor.class);

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor.name("JaCoCo XML Report Importer");
  }

  @Override
  public void execute(SensorContext context) {
    Collection<Path> reportPaths = new ReportPathsProvider(context).getPaths();
    if (reportPaths.isEmpty()) {
      LOG.info("No report imported, no coverage information will be imported by JaCoCo XML Report Importer");
      return;
    }
    Iterable<InputFile> inputFiles = context.fileSystem().inputFiles(context.fileSystem().predicates().all());
    Stream<InputFile> kotlinInputFileStream = StreamSupport.stream(inputFiles.spliterator(), false).filter(f -> "kotlin".equals(f.language()));
    FileLocator locator = new FileLocator(inputFiles, new KotlinFileLocator(kotlinInputFileStream));
    ReportImporter importer = new ReportImporter(context);

    importReports(reportPaths, locator, importer);
  }

  void importReports(Collection<Path> reportPaths, FileLocator locator, ReportImporter importer) {
    LOG.info("Importing {} report(s). Turn your logs in debug mode in order to see the exhaustive list.", reportPaths.size());

    for (Path reportPath : reportPaths) {
      LOG.debug("Reading report '{}'", reportPath);
      try {
        SensorUtils.importReport(new XmlReportParser(reportPath), locator, importer, LOG);
      } catch (Exception e) {
        LOG.error("Coverage report '{}' could not be read/imported. Error: {}", reportPath, e);
      }
    }
  }
}
