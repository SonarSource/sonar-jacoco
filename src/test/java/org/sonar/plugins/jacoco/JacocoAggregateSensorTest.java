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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.event.Level;
import org.sonar.api.batch.sensor.SensorDescriptor;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.testfixtures.log.LogTesterJUnit5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
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
    context = spy(SensorContextTester.create(basedir));
    context.settings().clear();
    context.settings().setProperty("sonar.projectBaseDir", basedir.toString());
    logTester.setLevel(Level.DEBUG);
  }

  @Test
  void description_name_is_as_expected() {
    SensorDescriptor descriptor = mock(SensorDescriptor.class);
    var sensor = new JacocoAggregateSensor(new ProjectCoverageContext());
    sensor.describe(descriptor);
    verify(descriptor).name("JaCoCo Aggregate XML Report Importer");
  }

  @Test
  void log_missing_report_and_return_early_when_missing_analysis_parameter() {
    var sensor = new JacocoAggregateSensor(new ProjectCoverageContext());
    sensor.execute(context);

    assertThat(logTester.logs(Level.DEBUG)).containsOnly(NO_REPORT_TO_IMPORT_LOG_MESSAGE);
  }

  @Test
  void log_missing_report_and_return_early_when_analysis_parameter_points_to_report_that_does_not_exist() {
    context.settings()
            .setProperty(ReportPathsProvider.AGGREGATE_REPORT_PATHS_PROPERTY_KEY, "non-existing-report.xml");

    var sensor = new JacocoAggregateSensor(new ProjectCoverageContext());
    sensor.execute(context);

    assertThat(logTester.logs(Level.DEBUG)).
            containsExactly("No aggregate XML report found. No coverage coverage information will be added at project level.");
    assertThat(logTester.logs(Level.WARN))
            .contains("No coverage report found for pattern: 'non-existing-report.xml'");
    assertThat(logTester.logs(Level.INFO)).isEmpty();
    verify(context, times(1)).addTelemetryProperty(TelemetryProperties.AGGREGATE_REPORT_PATH_PROPERTY_KEY_IS_SET, "true");
  }

  @Test
  void executes_as_expected() {
    Path aggregateReport = Path.of("src", "test", "resources", "jacoco-aggregate.xml");

    context.settings()
            .setProperty(ReportPathsProvider.AGGREGATE_REPORT_PATHS_PROPERTY_KEY, aggregateReport.toAbsolutePath().toString());

    var sensor = new JacocoAggregateSensor(new ProjectCoverageContext());
    sensor.execute(context);
    assertThat(logTester.logs(Level.DEBUG)).doesNotContain(NO_REPORT_TO_IMPORT_LOG_MESSAGE);
    assertThat(logTester.logs(Level.INFO)).containsOnly(
            "Importing 1 report(s). Turn your logs in debug mode in order to see the exhaustive list."
    );
  }

  @Test
  void should_not_fail_when_processing_a_single_module_report() {
    Path singModuleReport = Path.of("src", "test", "resources", "jacoco.xml");

    context.settings()
            .setProperty(ReportPathsProvider.AGGREGATE_REPORT_PATHS_PROPERTY_KEY, singModuleReport.toAbsolutePath().toString());

    var sensor = new JacocoAggregateSensor(new ProjectCoverageContext());
    sensor.execute(context);
    assertThat(logTester.logs(Level.DEBUG)).doesNotContain(NO_REPORT_TO_IMPORT_LOG_MESSAGE);
    assertThat(logTester.logs(Level.DEBUG)).contains("Reading report '" + singModuleReport.toAbsolutePath() + "'");
    assertThat(logTester.logs(Level.INFO)).containsOnly(
            "Importing 1 report(s). Turn your logs in debug mode in order to see the exhaustive list."
    );
    assertThat(logTester.logs(Level.ERROR)).isEmpty();
  }

}
