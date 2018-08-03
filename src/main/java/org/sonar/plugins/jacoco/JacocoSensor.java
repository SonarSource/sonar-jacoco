/*
 * SonarQube JaCoCo Plugin
 * Copyright (C) 2018-2018 SonarSource SA
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
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public class JacocoSensor implements Sensor {
  private static final Logger LOG = Loggers.get(JacocoSensor.class);

  @Override public void describe(SensorDescriptor descriptor) {
    descriptor.name("JaCoCo XML Report Importer");
  }

  @Override public void execute(SensorContext context) {
    ReportPathsProvider reportPathsProvider = new ReportPathsProvider(context);
    Iterable<InputFile> inputFiles = context.fileSystem().inputFiles(context.fileSystem().predicates().all());
    FileLocator locator = new FileLocator(inputFiles);
    ReportImporter importer = new ReportImporter(context);
    
    importReports(reportPathsProvider, locator, importer);
  }

  void importReports(ReportPathsProvider reportPathsProvider, FileLocator locator, ReportImporter importer) {
    Collection<Path> reportPaths = reportPathsProvider.getPaths();
    if (reportPaths.isEmpty()) {
      LOG.debug("No reports found");
      return;
    }

    for (Path reportPath : reportPaths) {
      LOG.debug("Reading report '{}'", reportPath);
      importReport(new XmlReportParser(reportPath), locator, importer);
    }
  }

  void importReport(XmlReportParser reportParser, FileLocator locator, ReportImporter importer) {
    List<XmlReportParser.SourceFile> sourceFiles = reportParser.parse();

    for (XmlReportParser.SourceFile sourceFile : sourceFiles) {
      InputFile inputFile = locator.getInputFile(sourceFile.packageName(), sourceFile.name());
      if (inputFile == null) {
        continue;
      }

      importer.importCoverage(sourceFile, inputFile);
    }
  }
}
