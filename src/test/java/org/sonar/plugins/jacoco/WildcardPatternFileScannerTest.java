/*
 * SonarQube JaCoCo Plugin
 * Copyright (C) 2018-2020 SonarSource SA
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
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.sonar.api.utils.log.LogTester;
import org.sonar.api.utils.log.LoggerLevel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.plugins.jacoco.WildcardPatternFileScanner.indexOfMatcherSpecialChar;
import static org.sonar.plugins.jacoco.WildcardPatternFileScanner.scan;
import static org.sonar.plugins.jacoco.WildcardPatternFileScanner.toUnixLikePath;

@org.junit.jupiter.migrationsupport.rules.EnableRuleMigrationSupport
class WildcardPatternFileScannerTest {

  static final Path RELATIVE_BASE_FOLDER = Paths.get("src", "test", "resources", "search");

  static final Path RELATIVE_F1 = RELATIVE_BASE_FOLDER.resolve("f1.xml");
  static final Path RELATIVE_F2 = RELATIVE_BASE_FOLDER.resolve("f2.xml");
  static final Path RELATIVE_SUBFOLDER = RELATIVE_BASE_FOLDER.resolve(Paths.get("subfolder"));
  static final Path RELATIVE_G1 = RELATIVE_BASE_FOLDER.resolve(Paths.get("subfolder", "g1.xml"));
  static final Path RELATIVE_G2 = RELATIVE_BASE_FOLDER.resolve(Paths.get("subfolder", "g2.xml"));

  static final Path ABSOLUTE_BASE_FOLDER;
  static final Path ABSOLUTE_F1;
  static final Path ABSOLUTE_F2;
  static final Path ABSOLUTE_SUBFOLDER;
  static final Path ABSOLUTE_G1;
  static final Path ABSOLUTE_G2;
  static {
    try {
      ABSOLUTE_BASE_FOLDER = RELATIVE_BASE_FOLDER.toRealPath();
      ABSOLUTE_F1 = RELATIVE_F1.toRealPath();
      ABSOLUTE_F2 = RELATIVE_F2.toRealPath();
      ABSOLUTE_SUBFOLDER = RELATIVE_SUBFOLDER.toRealPath();
      ABSOLUTE_G1 = RELATIVE_G1.toRealPath();
      ABSOLUTE_G2 = RELATIVE_G2.toRealPath();
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @Rule
  public LogTester logTester = new LogTester();

  @Test
  void search_pattern_paths_in_folder() throws IOException {
    assertThat(scan(RELATIVE_BASE_FOLDER, "*")).containsExactlyInAnyOrder(ABSOLUTE_F1, ABSOLUTE_F2);
    assertThat(scan(RELATIVE_BASE_FOLDER, "**")).containsExactlyInAnyOrder(ABSOLUTE_F1, ABSOLUTE_F2, ABSOLUTE_G1, ABSOLUTE_G2);
    assertThat(scan(RELATIVE_BASE_FOLDER, "**.xml")).containsExactlyInAnyOrder(ABSOLUTE_F1, ABSOLUTE_F2, ABSOLUTE_G1, ABSOLUTE_G2);
    assertThat(scan(RELATIVE_BASE_FOLDER, "**/*")).containsExactlyInAnyOrder(ABSOLUTE_F1, ABSOLUTE_F2, ABSOLUTE_G1, ABSOLUTE_G2);
    assertThat(scan(RELATIVE_BASE_FOLDER, "*/**")).containsExactlyInAnyOrder(ABSOLUTE_G1, ABSOLUTE_G2);
    assertThat(scan(RELATIVE_BASE_FOLDER, "subfolder/*")).containsExactlyInAnyOrder(ABSOLUTE_G1, ABSOLUTE_G2);
    assertThat(scan(RELATIVE_BASE_FOLDER, "**/g*.xml")).containsExactlyInAnyOrder(ABSOLUTE_G1, ABSOLUTE_G2);
    assertThat(scan(RELATIVE_BASE_FOLDER, ABSOLUTE_BASE_FOLDER + "/f*.xml")).containsExactlyInAnyOrder(ABSOLUTE_F1, ABSOLUTE_F2);
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  void search_non_pattern_paths_in_folder() throws IOException {
    assertThat(scan(RELATIVE_BASE_FOLDER, "f1.xml")).containsExactlyInAnyOrder(RELATIVE_F1);
    assertThat(scan(RELATIVE_BASE_FOLDER, "subfolder/g1.xml")).containsExactlyInAnyOrder(RELATIVE_G1);
    assertThat(scan(RELATIVE_BASE_FOLDER, "subfolder\\g1.xml")).containsExactlyInAnyOrder(RELATIVE_G1);
    assertThat(scan(RELATIVE_BASE_FOLDER, ABSOLUTE_F1.toString())).containsExactlyInAnyOrder(ABSOLUTE_F1);
    assertThat(scan(RELATIVE_BASE_FOLDER, "unknown-file.xml")).isEmpty();
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  void search_paths_in_not_existing_folder() throws IOException {
    assertThat(scan(Paths.get("not-existing-folder"), "*")).isEmpty();
    assertThat(logTester.logs()).isEmpty();
  }

  @Test
  void not_allowed_to_search_from_filesystem_root() throws IOException {
    Path root = ABSOLUTE_BASE_FOLDER.getRoot();
    assertThat(scan(root, "*.xml")).isEmpty();
    assertThat(logTester.logs()).hasSize(1);
    assertThat(logTester.logs(LoggerLevel.ERROR)).containsExactly(
      "Failed to get Jacoco report paths: Scanning '" + root + "'" +
        " with pattern '*.xml' threw a IOException: For performance reason, wildcard pattern search is not possible from filesystem root");
  }

  @Test
  void to_unix_path() {
    assertThat(toUnixLikePath("c:\\a\\b")).isEqualTo("c:/a/b");
    assertThat(toUnixLikePath("/a/b")).isEqualTo("/a/b");
    assertThat(toUnixLikePath("a/b")).isEqualTo("a/b");
    assertThat(toUnixLikePath("a")).isEqualTo("a");
  }

  @Test
  void index_of_special_char() {
    assertThat(indexOfMatcherSpecialChar("")).isEqualTo(-1);
    assertThat(indexOfMatcherSpecialChar("/a/b")).isEqualTo(-1);
    assertThat(indexOfMatcherSpecialChar("c:/b")).isEqualTo(-1);
    assertThat(indexOfMatcherSpecialChar("*/abc")).isEqualTo(0);
    assertThat(indexOfMatcherSpecialChar("**/*/abc")).isEqualTo(0);
    assertThat(indexOfMatcherSpecialChar("a/?/b/*")).isEqualTo(2);
  }

}
