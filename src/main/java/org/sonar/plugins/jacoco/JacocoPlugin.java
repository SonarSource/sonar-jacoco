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

import org.sonar.api.Plugin;
import org.sonar.api.PropertyType;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.resources.Qualifiers;

public class JacocoPlugin implements Plugin {
  @Override
  public void define(Context context) {
    context.addExtension(JacocoSensor.class);
    context.addExtension(PropertyDefinition.builder(ReportPathsProvider.REPORT_PATHS_PROPERTY_KEY)
      .onQualifiers(Qualifiers.PROJECT)
      .multiValues(true)
      .category("JaCoCo")
      .description("Paths to JaCoCo XML coverage report files. Each path can be either absolute or relative" +
        " to the project base directory. Wildcard patterns are accepted (*, ** and ?).")
      .build());

    context.addExtension(JacocoAggregateSensor.class);
    context.addExtension(PropertyDefinition.builder(ReportPathsProvider.AGGREGATE_REPORT_PATH_PROPERTY_KEY)
      .onQualifiers(Qualifiers.PROJECT)
      .type(PropertyType.STRING)
      .multiValues(false)
      .category("JaCoCo")
      .description("Single path to aggregate XML coverage report file.")
      .build());
  }
}
