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
import java.util.List;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;

public class JacocoSensor implements Sensor {
  private static final String REPORT_PATHS_PROPERTY_KEY = "sonar.coverage.jacoco.xmlReportPaths";

  @Override public void describe(SensorDescriptor descriptor) {
    descriptor.name("JaCoCo XML report importer").onlyWhenConfiguration(c -> c.hasKey(REPORT_PATHS_PROPERTY_KEY));
  }

  @Override public void execute(SensorContext context) {
    String[] reportPaths = context.config().getStringArray(REPORT_PATHS_PROPERTY_KEY);
    Iterable<InputFile> inputFiles = context.fileSystem().inputFiles(context.fileSystem().predicates().all());
    FileLocator locator = new FileLocator(inputFiles);
    ReportImporter importer = new ReportImporter(context);

    for (String reportPath : reportPaths) {
      Path path = context.fileSystem().baseDir().toPath().resolve(reportPath);
      importReport(path, locator, importer);
    }
  }

  void importReport(Path reportPath, FileLocator locator, ReportImporter importer) {
    XmlReportParser parser = new XmlReportParser(reportPath);
    List<XmlReportParser.SourceFile> sourceFiles = parser.parse();

    for (XmlReportParser.SourceFile sourceFile : sourceFiles) {
      InputFile inputFile = locator.getInputFile(sourceFile.packageName(), sourceFile.name());
      if (inputFile == null) {
        continue;
      }

      importer.importCoverage(sourceFile, inputFile);
    }
  }
}
