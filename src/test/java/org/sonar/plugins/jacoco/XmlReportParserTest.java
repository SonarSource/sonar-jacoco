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

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class XmlReportParserTest {

  @TempDir
  Path temp;

  private Path load(String name) throws URISyntaxException {
    return Paths.get(this.getClass().getClassLoader().getResource(name).toURI());
  }

  @Test
  void should_parse_all_items_in_report() throws URISyntaxException {
    Path sample = load("jacoco.xml");
    XmlReportParser report = new XmlReportParser(sample);
    List<XmlReportParser.SourceFile> sourceFiles = report.parse();

    assertThat(sourceFiles).hasSize(36);
    assertThat(sourceFiles.stream().mapToInt(sf -> sf.lines().size()).sum()).isEqualTo(1321);
  }

  @Test
  void should_parse_all_attributes() throws URISyntaxException {
    Path sample = load("simple.xml");
    XmlReportParser report = new XmlReportParser(sample);
    List<XmlReportParser.SourceFile> sourceFiles = report.parse();

    assertThat(sourceFiles).hasSize(1);
    assertThat(sourceFiles.stream().mapToInt(sf -> sf.lines().size()).sum()).isEqualTo(1);
    assertThat(sourceFiles.get(0).name()).isEqualTo("File.java");
    assertThat(sourceFiles.get(0).packageName()).isEqualTo("org/sonarlint/cli");
    assertThat(sourceFiles.get(0).lines().get(0).number()).isEqualTo(24);
    assertThat(sourceFiles.get(0).lines().get(0).missedInstrs()).isEqualTo(1);
    assertThat(sourceFiles.get(0).lines().get(0).coveredInstrs()).isEqualTo(2);
    assertThat(sourceFiles.get(0).lines().get(0).missedBranches()).isEqualTo(3);
    assertThat(sourceFiles.get(0).lines().get(0).coveredBranches()).isEqualTo(4);
  }

  @Test
  void should_treat_missing_mi_ci_mb_cb_in_line_as_zeros() throws Exception {
    Path sample = load("line_without_mi_ci_mb_cb.xml");
    XmlReportParser report = new XmlReportParser(sample);
    List<XmlReportParser.SourceFile> sourceFiles = report.parse();

    assertThat(sourceFiles).hasSize(1);
    assertThat(sourceFiles.stream().mapToInt(sf -> sf.lines().size()).sum()).isEqualTo(1);
    assertThat(sourceFiles.get(0).name()).isEqualTo("Example.java");
    assertThat(sourceFiles.get(0).packageName()).isEqualTo("org/example");
    assertThat(sourceFiles.get(0).lines().get(0).number()).isEqualTo(42);
    assertThat(sourceFiles.get(0).lines().get(0).missedInstrs()).isEqualTo(0);
    assertThat(sourceFiles.get(0).lines().get(0).coveredInstrs()).isEqualTo(0);
    assertThat(sourceFiles.get(0).lines().get(0).missedBranches()).isEqualTo(0);
    assertThat(sourceFiles.get(0).lines().get(0).coveredBranches()).isEqualTo(0);
  }

  @Test
  void should_fail_if_report_is_not_xml() throws IOException {
    Path filePath = temp.resolve("report.xml");
    XmlReportParser report = new XmlReportParser(filePath);

    IllegalStateException exception = assertThrows(IllegalStateException.class, report::parse);
    assertThat(exception).hasMessage("Failed to parse JaCoCo XML report: " + filePath.toAbsolutePath());
  }

  @Test
  void should_fail_if_name_missing_in_package() throws URISyntaxException {
    Path sample = load("name_missing_in_package.xml");
    XmlReportParser report = new XmlReportParser(sample);

    IllegalStateException exception = assertThrows(IllegalStateException.class, report::parse);
    assertThat(exception).hasMessage("Invalid report: couldn't find the attribute 'name' for a 'package' at line 4 column 14");
  }

  @Test
  void should_fail_if_name_missing_in_sourcefile() throws URISyntaxException {
    Path sample = load("name_missing_in_sourcefile.xml");
    XmlReportParser report = new XmlReportParser(sample);

    IllegalStateException exception = assertThrows(IllegalStateException.class, report::parse);
    assertThat(exception).hasMessage("Invalid report: couldn't find the attribute 'name' for a sourcefile at line 5 column 21");
  }

  @Test
  void should_fail_if_line_not_within_sourcefile() throws URISyntaxException {
    Path sample = load("line_not_within_sourcefile.xml");
    XmlReportParser report = new XmlReportParser(sample);

    IllegalStateException exception = assertThrows(IllegalStateException.class, report::parse);
    assertThat(exception).hasMessage( "Invalid report: expected to find 'line' within a 'sourcefile' at line 5 column 52");
  }

  @Test
  void should_fail_if_sourcefile_not_within_package() throws URISyntaxException {
    Path sample = load("sourcefile_not_within_package.xml");
    XmlReportParser report = new XmlReportParser(sample);

    IllegalStateException exception = assertThrows(IllegalStateException.class, report::parse);
    assertThat(exception).hasMessage( "Invalid report: expected to find 'sourcefile' within a 'package' at line 4 column 17");
  }

  @Test
  void should_fail_if_ci_is_invalid_in_line() throws URISyntaxException {
    Path sample = load("invalid_ci_in_line.xml");
    XmlReportParser report = new XmlReportParser(sample);

    IllegalStateException exception = assertThrows(IllegalStateException.class, report::parse);
    assertThat(exception).hasMessage("Invalid report: failed to parse integer from the attribute 'ci' for the sourcefile 'File.java' at line 6 column 61");
  }

  @Test
  void should_fail_if_nr_is_invalid_in_line() throws URISyntaxException {
    Path sample = load("invalid_nr_in_line.xml");
    XmlReportParser report = new XmlReportParser(sample);

    IllegalStateException exception = assertThrows(IllegalStateException.class, report::parse);
    assertThat(exception).hasMessage("Invalid report: failed to parse integer from the attribute 'nr' for the sourcefile 'File.java' at line 6 column 31");
  }

  @Test
  void should_fail_if_nr_missing_in_line() throws URISyntaxException {
    Path sample = load("nr_missing_in_line.xml");
    XmlReportParser report = new XmlReportParser(sample);

    IllegalStateException exception = assertThrows(IllegalStateException.class, report::parse);
    assertThat(exception).hasMessage("Invalid report: couldn't find the attribute 'nr' for the sourcefile 'File.java' at line 6 column 21");
  }

  @Test
  void should_import_kotlin_report() throws URISyntaxException {
    Path sample = load("kotlin.xml");
    XmlReportParser report = new XmlReportParser(sample);
    List<XmlReportParser.SourceFile> sourceFiles = report.parse();

    assertThat(sourceFiles).hasSize(5);
    assertThat(sourceFiles.stream().mapToInt(sf -> sf.lines().size()).sum()).isEqualTo(79);
  }

  @Test
  void line_equality_checks_work_as_expected() {
    var line = new XmlReportParser.Line(1, 2, 3, 4, 5);
    var identicalLine = new XmlReportParser.Line(1, 2, 3, 4, 5);

    assertThat(line)
            //Equality
            .isEqualTo(line)
            .hasSameHashCodeAs(line)
            .isEqualTo(identicalLine)
            .hasSameHashCodeAs(identicalLine)
            // Inequality
            .isNotEqualTo(null)
            .isNotEqualTo(new Object())
            .isNotEqualTo(new XmlReportParser.Line(42, 2, 3, 4, 5))
            .isNotEqualTo(new XmlReportParser.Line(1, 42, 3, 4, 5))
            .isNotEqualTo(new XmlReportParser.Line(1, 2, 42, 4, 5))
            .isNotEqualTo(new XmlReportParser.Line(1, 2, 3, 42, 5))
            .isNotEqualTo(new XmlReportParser.Line(1, 2, 3, 4, 42));
  }

  @Test
  void should_import_aggregate_report() throws URISyntaxException {
    Path sample = load("jacoco-aggregate.xml");
    XmlReportParser parser = new XmlReportParser(sample);

    List<XmlReportParser.SourceFile> sourceFiles = parser.parse();

    assertThat(sourceFiles).hasSize(1);
    var singleFile = sourceFiles.get(0);
    assertThat(singleFile.packageName()).isEqualTo("org/example");
    assertThat(singleFile.name()).isEqualTo("Library.java");
    assertThat(singleFile.lines()).containsExactly(
            new XmlReportParser.Line(3, 3, 0, 0, 0),
            new XmlReportParser.Line(5, 0, 2, 1, 1),
            new XmlReportParser.Line(6, 2, 0, 0, 0),
            new XmlReportParser.Line(8, 0, 5, 0, 0)
    );

    assertThat(singleFile.groupName()).isEqualTo("library");
  }
}
