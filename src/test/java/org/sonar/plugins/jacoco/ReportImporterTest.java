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

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.sensor.internal.SensorContextTester;

class ReportImporterTest {
  @TempDir
  Path temp;

  private SensorContextTester ctx;
  private ReportImporter importer;

  @BeforeEach
  void setUp() throws IOException {
    Path module = temp.resolve("module");
    Files.createDirectory(module);
    ctx = SensorContextTester.create(module);
    importer = new ReportImporter(ctx);
  }

  @Test
  void should_import_coverage() {
    InputFile inputFile = TestInputFileBuilder.create("module", "filePath")
      .setLines(10)
      .build();
    XmlReportParser.SourceFile sourceFile = new XmlReportParser.SourceFile("package", "name");
    sourceFile.lines().add(new XmlReportParser.Line(1, 0, 0, 1, 1));
    sourceFile.lines().add(new XmlReportParser.Line(2, 1, 2, 0, 0));
    sourceFile.lines().add(new XmlReportParser.Line(3, 2, 0, 0, 0));

    importer.importCoverage(sourceFile, inputFile);

    Assertions.assertThat(ctx.coveredConditions(inputFile.key(), 1)).isEqualTo(1);
    Assertions.assertThat(ctx.coveredConditions(inputFile.key(), 2)).isNull();
    Assertions.assertThat(ctx.coveredConditions(inputFile.key(), 3)).isNull();

    Assertions.assertThat(ctx.conditions(inputFile.key(), 1)).isEqualTo(2);
    Assertions.assertThat(ctx.conditions(inputFile.key(), 2)).isNull();
    Assertions.assertThat(ctx.conditions(inputFile.key(), 3)).isNull();

    Assertions.assertThat(ctx.lineHits(inputFile.key(), 1)).isEqualTo(0);
    Assertions.assertThat(ctx.lineHits(inputFile.key(), 2)).isEqualTo(1);
    Assertions.assertThat(ctx.lineHits(inputFile.key(), 3)).isEqualTo(0);
  }
}
