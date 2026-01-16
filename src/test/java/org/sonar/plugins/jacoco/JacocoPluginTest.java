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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.sonar.api.Plugin;
import org.sonar.api.PropertyType;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.resources.Qualifiers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class JacocoPluginTest {
  private JacocoPlugin plugin = new JacocoPlugin();
  private Plugin.Context ctx = mock(Plugin.Context.class);
  @Test
  void should_add_sensors_and_property_definitions() {
    plugin.define(ctx);

    ArgumentCaptor<Object> arg = ArgumentCaptor.forClass(Object.class);
    verify(ctx, times(5)).addExtension(arg.capture());
    verifyNoMoreInteractions(ctx);

    assertThat(arg.getAllValues().get(0)).isEqualTo(ProjectCoverageContext.class);

    assertThat(arg.getAllValues().get(1)).isEqualTo(JacocoSensor.class);
    assertThat(arg.getAllValues().get(2)).isInstanceOf(PropertyDefinition.class);
    PropertyDefinition multiValueReportPaths = (PropertyDefinition) arg.getAllValues().get(2);
    assertThat(multiValueReportPaths.key()).isEqualTo("sonar.coverage.jacoco.xmlReportPaths");
    assertThat(multiValueReportPaths.multiValues()).isTrue();
    assertThat(multiValueReportPaths.category()).isEqualTo("JaCoCo");
    assertThat(multiValueReportPaths.qualifiers()).containsOnly(Qualifiers.PROJECT);

    assertThat(arg.getAllValues().get(3)).isEqualTo(JacocoAggregateSensor.class);
    PropertyDefinition aggregateReportPath = (PropertyDefinition) arg.getAllValues().get(4);
    assertThat(aggregateReportPath.key()).isEqualTo("sonar.coverage.jacoco.aggregateXmlReportPath");
    assertThat(aggregateReportPath.type()).isEqualTo(PropertyType.STRING);
    assertThat(aggregateReportPath.multiValues()).isFalse();
    assertThat(aggregateReportPath.category()).isEqualTo("JaCoCo");
    assertThat(aggregateReportPath.qualifiers()).containsOnly(Qualifiers.PROJECT);
  }
}
