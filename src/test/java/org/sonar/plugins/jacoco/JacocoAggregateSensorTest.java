/*
 * SonarQube JaCoCo Plugin
 * Copyright (C) 2018-2025 SonarSource SA
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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.log.LogTesterJUnit5;
import org.sonar.api.utils.log.LoggerLevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class JacocoAggregateSensorTest {
  private static final String NO_REPORT_TO_IMPORT_LOG_MESSAGE = "No aggregate XML report found. No coverage coverage information will be added at project level.";
  @TempDir
  Path basedir;

  @RegisterExtension
  public LogTesterJUnit5 logTester = new LogTesterJUnit5();

  private SensorContextTester context;

  @BeforeEach
  void setup() {
    context = SensorContextTester.create(basedir);
  }

  @Test
  void description_name_is_as_expected() {
    SensorDescriptor descriptor = mock(SensorDescriptor.class);
    var sensor = new JacocoAggregateSensor();
    sensor.describe(descriptor);
    verify(descriptor).name("JaCoCo Aggregate XML Report Importer");
  }

  @Test
  void log_missing_report_and_return_early_when_missing_analysis_parameter() {
    var sensor = new JacocoAggregateSensor();
    sensor.execute(context);

    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly(NO_REPORT_TO_IMPORT_LOG_MESSAGE);
  }

  @Test
  void log_missing_report_and_return_early_when_analysis_parameter_points_to_report_that_does_not_exist() {
    MapSettings settings = new MapSettings();
    settings.setProperty(ReportPathsProvider.AGGREGATE_REPORT_PATH_PROPERTY_KEY, "non-existing-report.xml");
    context.setSettings(settings);

    var sensor = new JacocoAggregateSensor();
    sensor.execute(context);

    assertThat(logTester.logs(LoggerLevel.DEBUG)).containsOnly(NO_REPORT_TO_IMPORT_LOG_MESSAGE);
  }

  @Test
  void executes_as_expected() throws IOException {
    Path reportPath = basedir.resolve("my-aggregate-report.xml");
    Files.copy(Path.of("src", "test", "resources", "jacoco.xml"), reportPath);

    MapSettings settings = new MapSettings();
    settings.setProperty(ReportPathsProvider.AGGREGATE_REPORT_PATH_PROPERTY_KEY, reportPath.toAbsolutePath().toString());
    context.setSettings(settings);

    var sensor = new JacocoAggregateSensor();
    sensor.execute(context);
    assertThat(logTester.logs(LoggerLevel.DEBUG)).doesNotContain(NO_REPORT_TO_IMPORT_LOG_MESSAGE);
  }

}