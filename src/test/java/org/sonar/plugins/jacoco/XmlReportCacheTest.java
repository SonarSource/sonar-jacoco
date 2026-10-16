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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.event.Level;
import org.sonar.api.testfixtures.log.LogTesterJUnit5;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class XmlReportCacheTest {
  private static final Path RESOURCES_DIR = Path.of("src", "test", "resources");
  private static final Path SINGLE_FILE_REPORT = RESOURCES_DIR.resolve("simple.xml");
  private static final Path MANY_FILES_REPORT = RESOURCES_DIR.resolve("jacoco.xml");
  private static final Path INVALID_REPORT = RESOURCES_DIR.resolve("invalid_ci_in_line.xml");

  @RegisterExtension
  public LogTesterJUnit5 logTester = new LogTesterJUnit5();

  private final List<Path> parsedPaths = new ArrayList<>();
  private final XmlReportCache cache = new XmlReportCache(this::countingParse);

  @Test
  void parse_a_report_only_once() {
    List<XmlReportParser.SourceFile> firstSourceFiles = cache.parse(MANY_FILES_REPORT);
    List<XmlReportParser.SourceFile> secondSourceFiles = cache.parse(MANY_FILES_REPORT);

    assertThat(parsedPaths).containsExactly(MANY_FILES_REPORT);
    assertThat(firstSourceFiles).hasSize(36);
    assertThat(secondSourceFiles).isSameAs(firstSourceFiles);
    assertThat(cache.size()).isEqualTo(1);
  }

  @Test
  void return_the_same_source_files_as_the_parser() {
    List<XmlReportParser.SourceFile> expected = new XmlReportParser(MANY_FILES_REPORT).parse();

    List<XmlReportParser.SourceFile> sourceFiles = cache.parse(MANY_FILES_REPORT);

    assertThat(sourceFiles)
      .extracting(XmlReportParser.SourceFile::packageName, XmlReportParser.SourceFile::name, XmlReportParser.SourceFile::lines)
      .containsExactlyElementsOf(expected.stream()
        .map(sourceFile -> tuple(sourceFile.packageName(), sourceFile.name(), sourceFile.lines()))
        .toList());
  }

  @Test
  void key_reports_by_absolute_and_normalized_path() {
    Path relativePath = RESOURCES_DIR.resolve("search").resolve("..").resolve("jacoco.xml");

    cache.parse(relativePath);
    cache.parse(MANY_FILES_REPORT.toAbsolutePath());
    cache.parse(MANY_FILES_REPORT);

    assertThat(parsedPaths).containsExactly(relativePath);
    assertThat(cache.size()).isEqualTo(1);
  }

  @Test
  void parse_different_reports_separately() {
    assertThat(cache.parse(SINGLE_FILE_REPORT)).hasSize(1);
    assertThat(cache.parse(MANY_FILES_REPORT)).hasSize(36);
    assertThat(cache.parse(SINGLE_FILE_REPORT)).hasSize(1);

    assertThat(parsedPaths).containsExactly(SINGLE_FILE_REPORT, MANY_FILES_REPORT);
    assertThat(cache.size()).isEqualTo(2);
  }

  @Test
  void rethrow_the_parse_failure_on_every_request_without_parsing_again() {
    assertThatThrownBy(() -> cache.parse(INVALID_REPORT))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Invalid report: failed to parse integer from the attribute 'ci' for the sourcefile 'File.java' at line 6 column 61");
    assertThatThrownBy(() -> cache.parse(INVALID_REPORT))
      .isInstanceOf(IllegalStateException.class)
      .hasMessage("Invalid report: failed to parse integer from the attribute 'ci' for the sourcefile 'File.java' at line 6 column 61");

    assertThat(parsedPaths).containsExactly(INVALID_REPORT);
  }

  @Test
  void log_in_debug_when_a_report_is_reused() {
    logTester.setLevel(Level.DEBUG);

    cache.parse(SINGLE_FILE_REPORT);
    assertThat(logTester.logs(Level.DEBUG)).isEmpty();

    cache.parse(SINGLE_FILE_REPORT);
    assertThat(logTester.logs(Level.DEBUG)).containsExactly("Report '" + SINGLE_FILE_REPORT + "' was already parsed during this analysis, reusing its content");
  }

  @Test
  void parse_again_after_clear() {
    cache.parse(SINGLE_FILE_REPORT);

    cache.clear();

    assertThat(cache.size()).isZero();
    cache.parse(SINGLE_FILE_REPORT);
    assertThat(parsedPaths).containsExactly(SINGLE_FILE_REPORT, SINGLE_FILE_REPORT);
  }

  @Test
  void do_not_allow_callers_to_modify_the_shared_source_files() {
    List<XmlReportParser.SourceFile> sourceFiles = cache.parse(SINGLE_FILE_REPORT);

    assertThatThrownBy(sourceFiles::clear).isInstanceOf(UnsupportedOperationException.class);
  }

  private List<XmlReportParser.SourceFile> countingParse(Path reportPath) {
    parsedPaths.add(reportPath);
    return new XmlReportParser(reportPath).parse();
  }
}
