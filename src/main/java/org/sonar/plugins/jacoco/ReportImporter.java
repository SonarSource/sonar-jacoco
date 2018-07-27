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

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.batch.sensor.coverage.NewCoverage;

public class ReportImporter {
  private final SensorContext ctx;

  public ReportImporter(SensorContext ctx) {
    this.ctx = ctx;
  }

  public void importCoverage(XmlReportParser.SourceFile sourceFile, InputFile inputFile) {
    NewCoverage newCoverage = ctx.newCoverage()
      .onFile(inputFile);

    for (XmlReportParser.Line line : sourceFile.lines()) {
      if (line.coveredBranches() > 0 || line.missedBranches() > 0) {
        int branches = line.coveredBranches() + line.missedBranches();
        newCoverage.conditions(line.number(), branches, line.coveredBranches());
      } else {
        newCoverage.lineHits(line.number(), line.coveredInstrs() > 0 ? 1 : 0);
      }
    }

    newCoverage.save();
  }
}
