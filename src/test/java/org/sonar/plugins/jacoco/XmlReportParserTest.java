/*
 * SonarQube JaCoCo Plugin
 * Copyright (C) 2018-2019 SonarSource SA
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;

public class XmlReportParserTest {
  @Rule
  public ExpectedException exception = ExpectedException.none();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private Path load(String name) throws URISyntaxException {
    return Paths.get(this.getClass().getClassLoader().getResource(name).toURI());
  }

  @Test
  public void should_parse_all_items_in_report() throws URISyntaxException {
    Path sample = load("jacoco.xml");
    XmlReportParser report = new XmlReportParser(sample);
    List<XmlReportParser.SourceFile> sourceFiles = report.parse();

    assertThat(sourceFiles).hasSize(36);
    assertThat(sourceFiles.stream().mapToInt(sf -> sf.lines().size()).sum()).isEqualTo(1321);
  }

  @Test
  public void should_parse_all_attributes() throws URISyntaxException {
    Path sample = load("simple.xml");
    XmlReportParser report = new XmlReportParser(sample);
    List<XmlReportParser.SourceFile> sourceFiles = report.parse();

    assertThat(sourceFiles).hasSize(1);
    assertThat(sourceFiles.stream().mapToInt(sf -> sf.lines().size()).sum()).isEqualTo(1);
    assertThat(sourceFiles.get(0).name()).isEqualTo("File.java");
    assertThat(sourceFiles.get(0).packageName()).isEqualTo("org/sonarlint/cli");
    assertThat(sourceFiles.get(0).lines().get(0).coveredBranches()).isEqualTo(0);
    assertThat(sourceFiles.get(0).lines().get(0).coveredInstrs()).isEqualTo(3);
    assertThat(sourceFiles.get(0).lines().get(0).missedBranches()).isEqualTo(0);
    assertThat(sourceFiles.get(0).lines().get(0).missedInstrs()).isEqualTo(0);
  }

  @Test
  public void should_fail_if_report_is_not_xml() throws IOException {
    Path filePath = temp.newFile("report.xml").toPath();
    XmlReportParser report = new XmlReportParser(filePath);

    exception.expect(IllegalStateException.class);
    exception.expectMessage("Failed to parse JaCoCo XML report: " + filePath.toAbsolutePath());
    report.parse();
  }

  @Test
  public void should_fail_if_name_missing_in_sourcefile() throws URISyntaxException {
    Path sample = load("name_missing_in_sourcefile.xml");
    XmlReportParser report = new XmlReportParser(sample);

    exception.expect(IllegalStateException.class);
    exception.expectMessage("Invalid report: couldn't find the attribute 'name' for a sourcefile in line 5");
    report.parse();
  }

  @Test
  public void should_fail_if_line_not_within_sourcefile() throws URISyntaxException {
    Path sample = load("line_not_within_sourcefile.xml");
    XmlReportParser report = new XmlReportParser(sample);

    exception.expect(IllegalStateException.class);
    exception.expectMessage( "Invalid report: expected to find 'line' within a 'sourcefile' in line 5");
    report.parse();
  }

  @Test
  public void should_fail_if_sourcefile_not_within_package() throws URISyntaxException {
    Path sample = load("sourcefile_not_within_package.xml");
    XmlReportParser report = new XmlReportParser(sample);

    exception.expect(IllegalStateException.class);
    exception.expectMessage( "Invalid report: expected to find 'sourcefile' within a 'package' in line 4");
    report.parse();
  }

  @Test
  public void should_fail_if_ci_missing_in_line() throws URISyntaxException {
    Path sample = load("ci_missing_in_line.xml");
    XmlReportParser report = new XmlReportParser(sample);

    exception.expect(IllegalStateException.class);
    exception.expectMessage("Invalid report: couldn't find the attribute 'ci' for the sourcefile 'File.java' in line 6");
    report.parse();
  }

  @Test
  public void should_fail_if_ci_is_invalid_in_line() throws URISyntaxException {
    Path sample = load("invalid_ci_in_line.xml");
    XmlReportParser report = new XmlReportParser(sample);

    exception.expect(IllegalStateException.class);
    exception.expectMessage("Invalid report: failed to parse integer from the attribute 'ci' for the sourcefile 'File.java' in line 6");
    report.parse();
  }

  @Test
  public void should_import_kotlin_report() throws URISyntaxException {
    Path sample = load("kotlin.xml");
    XmlReportParser report = new XmlReportParser(sample);
    List<XmlReportParser.SourceFile> sourceFiles = report.parse();

    assertThat(sourceFiles).hasSize(5);
    assertThat(sourceFiles.stream().mapToInt(sf -> sf.lines().size()).sum()).isEqualTo(79);
  }

}
