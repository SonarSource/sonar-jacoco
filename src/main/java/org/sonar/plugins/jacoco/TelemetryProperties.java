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

public final class TelemetryProperties {
  /**
   * A boolean flag indicating whether {@link ReportPathsProvider#AGGREGATE_REPORT_PATH_PROPERTY_KEY} was configured in the analysis.
   * Expected values are the strings:
   * <ul>
   *   <li>false (default)</li>
   *   <li>true</li>
   * </ul>
   */
  public static final String AGGREGATE_REPORT_PATH_PROPERTY_KEY_IS_SET = "sonar.coverage.jacoco.aggregateXmlReportPath.set";

  private TelemetryProperties() {
    /* This utility class should not be instantiated */
  }
}
