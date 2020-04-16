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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.sonar.plugins.jacoco.FileSystemWildcardPatternScanner.indexOfMatcherSpecialChar;
import static org.sonar.plugins.jacoco.FileSystemWildcardPatternScanner.toUnixLikePath;

class FileSystemWildcardPatternScannerTest {

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

  List<Path> paths = new ArrayList<>();
  List<String> errors = new ArrayList<>();

  @Test
  void search_pattern_paths_in_folder() throws IOException {
    FileSystemWildcardPatternScanner.of("*").scan(RELATIVE_BASE_FOLDER, paths::add, errors::add);
    assertThat(paths).containsExactlyInAnyOrder(ABSOLUTE_F1, ABSOLUTE_F2, ABSOLUTE_SUBFOLDER);
    assertThat(errors).isEmpty();

    paths.clear();
    FileSystemWildcardPatternScanner.of("**").scan(RELATIVE_BASE_FOLDER, path -> true, paths::add, errors::add);
    assertThat(paths).containsExactlyInAnyOrder(ABSOLUTE_F1, ABSOLUTE_F2, ABSOLUTE_SUBFOLDER, ABSOLUTE_G1, ABSOLUTE_G2);
    assertThat(errors).isEmpty();

    paths.clear();
    FileSystemWildcardPatternScanner.of("**").scan(RELATIVE_BASE_FOLDER, Files::isRegularFile, paths::add, errors::add);
    assertThat(paths).containsExactlyInAnyOrder(ABSOLUTE_F1, ABSOLUTE_F2, ABSOLUTE_G1, ABSOLUTE_G2);
    assertThat(errors).isEmpty();

    paths.clear();
    FileSystemWildcardPatternScanner.of("**").scan(RELATIVE_BASE_FOLDER, Files::isDirectory, paths::add, errors::add);
    assertThat(paths).containsExactlyInAnyOrder(ABSOLUTE_SUBFOLDER);
    assertThat(errors).isEmpty();

    paths.clear();
    FileSystemWildcardPatternScanner.of("**.xml").scan(RELATIVE_BASE_FOLDER, paths::add, errors::add);
    assertThat(paths).containsExactlyInAnyOrder(ABSOLUTE_F1, ABSOLUTE_F2, ABSOLUTE_G1, ABSOLUTE_G2);
    assertThat(errors).isEmpty();

    paths.clear();
    FileSystemWildcardPatternScanner.of("**/*").scan(RELATIVE_BASE_FOLDER, Files::isRegularFile, paths::add, errors::add);
    assertThat(paths).containsExactlyInAnyOrder(ABSOLUTE_F1, ABSOLUTE_F2, ABSOLUTE_G1, ABSOLUTE_G2);
    assertThat(errors).isEmpty();

    paths.clear();
    FileSystemWildcardPatternScanner.of("*/**").scan(RELATIVE_BASE_FOLDER, Files::isRegularFile, paths::add, errors::add);
    assertThat(paths).containsExactlyInAnyOrder(ABSOLUTE_G1, ABSOLUTE_G2);
    assertThat(errors).isEmpty();

    paths.clear();
    FileSystemWildcardPatternScanner.of("subfolder/*").scan(RELATIVE_BASE_FOLDER, paths::add, errors::add);
    assertThat(paths).containsExactlyInAnyOrder(ABSOLUTE_G1, ABSOLUTE_G2);
    assertThat(errors).isEmpty();

    paths.clear();
    FileSystemWildcardPatternScanner.of("**/g*.xml").scan(RELATIVE_BASE_FOLDER, paths::add, errors::add);
    assertThat(paths).containsExactlyInAnyOrder(ABSOLUTE_G1, ABSOLUTE_G2);
    assertThat(errors).isEmpty();

    paths.clear();
    FileSystemWildcardPatternScanner.of(RELATIVE_BASE_FOLDER.toRealPath() + "/f*.xml").scan(RELATIVE_BASE_FOLDER, paths::add, errors::add);
    assertThat(paths).containsExactlyInAnyOrder(ABSOLUTE_F1, ABSOLUTE_F2);
    assertThat(errors).isEmpty();
  }

  @Test
  void search_non_pattern_paths_in_folder() throws IOException {
    FileSystemWildcardPatternScanner.of("f1.xml").scan(RELATIVE_BASE_FOLDER, paths::add, errors::add);
    assertThat(paths).containsExactlyInAnyOrder(RELATIVE_F1);
    assertThat(errors).isEmpty();

    paths.clear();
    FileSystemWildcardPatternScanner.of("subfolder/g1.xml").scan(RELATIVE_BASE_FOLDER, paths::add, errors::add);
    assertThat(paths).containsExactlyInAnyOrder(RELATIVE_G1);
    assertThat(errors).isEmpty();

    paths.clear();
    FileSystemWildcardPatternScanner.of("subfolder\\g1.xml").scan(RELATIVE_BASE_FOLDER, paths::add, errors::add);
    assertThat(paths).containsExactlyInAnyOrder(RELATIVE_G1);
    assertThat(errors).isEmpty();

    paths.clear();
    FileSystemWildcardPatternScanner.of(ABSOLUTE_F1.toString()).scan(RELATIVE_BASE_FOLDER, paths::add, errors::add);
    assertThat(paths).containsExactlyInAnyOrder(ABSOLUTE_F1);
    assertThat(errors).isEmpty();

    paths.clear();
    FileSystemWildcardPatternScanner.of("unknown-file.xml").scan(RELATIVE_BASE_FOLDER, paths::add, errors::add);
    assertThat(paths).isEmpty();
    assertThat(errors).isEmpty();
  }

  @Test
  void search_paths_in_not_existing_folder() throws IOException {
    Path notExistingBaseFolder = Paths.get("not-existing-folder");
    FileSystemWildcardPatternScanner.of("*").scan(notExistingBaseFolder, paths::add, errors::add);
    assertThat(paths).isEmpty();
    assertThat(errors).isEmpty();
  }

  @Test
  void search_pre_filter_non_pattern_paths() {
    FileSystemWildcardPatternScanner.of("f1.xml").scan(RELATIVE_BASE_FOLDER, Files::isDirectory, paths::add, errors::add);
    assertThat(paths).isEmpty();
    assertThat(errors).isEmpty();

    FileSystemWildcardPatternScanner.of("f1.xml").scan(RELATIVE_BASE_FOLDER, Files::isRegularFile, paths::add, errors::add);
    assertThat(paths).containsExactlyInAnyOrder(RELATIVE_F1);
    assertThat(errors).isEmpty();
  }

  @Test
  void not_allowed_to_search_from_filesystem_root() throws IOException {
    Path root = ABSOLUTE_BASE_FOLDER.getRoot();
    FileSystemWildcardPatternScanner.of("*.xml").scan(root, paths::add, errors::add);
    assertThat(paths).isEmpty();
    assertThat(errors).containsExactly(
      "For performance reason, wildcard pattern search is not possible from filesystem root '" + root + "' (pattern: '*.xml')");
  }

  @Test
  void throw_exception_in_path_consumer() throws IOException {
    FileSystemWildcardPatternScanner.of("*").scan(RELATIVE_BASE_FOLDER, path -> { throw new RuntimeException("My error"); }, errors::add);
    assertThat(paths).isEmpty();
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0)).contains(" with pattern '*' threw a RuntimeException: My error");
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
