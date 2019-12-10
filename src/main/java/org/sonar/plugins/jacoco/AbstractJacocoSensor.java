/*
 * SonarQube JaCoCo Plugin
 * Copyright (C) 2019 SonarSource SA
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
import java.util.Collection;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.Sensor;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public abstract class AbstractJacocoSensor implements Sensor {

  protected final Logger LOG = Loggers.get(getClass());

  @Override
  public void describe(SensorDescriptor descriptor) {
    descriptor.name(getSensorName());
  }

  @Override
  public void execute(SensorContext context) {
    ReportPathsProvider reportPathsProvider = new ReportPathsProvider(context);
    Iterable<InputFile> inputFiles = context.fileSystem().inputFiles(context.fileSystem().predicates().all());
    FileLocator locator = new FileLocator(inputFiles);
    ReportImporter importer = new ReportImporter(context);

    importReports(reportPathsProvider, locator, importer, context);
  }
  
  protected void importReports(ReportPathsProvider reportPathsProvider, FileLocator locator, ReportImporter importer, SensorContext context) {
	    Collection<Path> reportPaths = reportPathsProvider.getPaths();
	    if (reportPaths.isEmpty()) {
	      LOG.debug("No reports found");
	      return;
	    }

	    for (Path reportPath : reportPaths) {
	      if (!Files.isRegularFile(reportPath)) {
	        LOG.warn("Report doesn't exist: '{}'", reportPath);
	      } else {
	        LOG.debug("Reading report '{}'", reportPath);
	        try {
	          importReport(new XmlReportParser(reportPath), locator, importer, context);
	        } catch (Exception e) {
	          LOG.error("Coverage report '{}' could not be read/imported. Error: {}", reportPath, e);
	        }
	      }
	    }
	  }

  protected abstract String getSensorName();
  
  protected abstract void importReport(XmlReportParser reportParser, FileLocator locator, ReportImporter importer, SensorContext context);

}
