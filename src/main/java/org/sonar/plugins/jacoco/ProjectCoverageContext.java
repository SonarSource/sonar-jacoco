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
import java.util.ArrayList;
import java.util.List;
import org.sonar.api.scanner.ScannerSide;

/**
 * Project-level component (a {@link ScannerSide} component without instantiation strategy is created once per analysis), shared by the module executions of
 * {@link JacocoSensor} and by {@link JacocoAggregateSensor}.
 */
@ScannerSide
public class ProjectCoverageContext {
  private Path projectBaseDir;

  private List<ModuleCoverageContext> moduleContexts = new ArrayList<>();

  private final XmlReportCache reportCache;

  public ProjectCoverageContext() {
    this(new XmlReportCache());
  }

  ProjectCoverageContext(XmlReportCache reportCache) {
    this.reportCache = reportCache;
  }

  XmlReportCache getReportCache() {
    return reportCache;
  }

  public List<ModuleCoverageContext> getModuleContexts() {
    return moduleContexts;
  }

  public void add(ModuleCoverageContext moduleContext) {
    this.moduleContexts.add(moduleContext);
  }


  public Path getProjectBaseDir() {
    return projectBaseDir;
  }

  public void setProjectBaseDir(Path projectBaseDir) {
    this.projectBaseDir = projectBaseDir;
  }
}
