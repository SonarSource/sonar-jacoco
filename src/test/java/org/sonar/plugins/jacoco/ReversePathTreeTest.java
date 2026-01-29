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
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

class ReversePathTreeTest {
  @Test
  void module_aware_resolution_of_input_file_with_name_clashes_across_modules_works_as_expected() {
    String module = "module";
    InputFile target = TestInputFileBuilder.create("", "module/src/main/java/org/example/App.java").build();
    String[] path = target.relativePath().split("/");

    InputFile clashingFile = TestInputFileBuilder.create("", "module-clash/src/main/java/org/example/App.java").build();
    String[] clashingPath = clashingFile.relativePath().split("/");

    var reverseParseTree = new ReversePathTree();
    reverseParseTree.index(clashingFile, clashingPath);
    reverseParseTree.index(target, path);

    String[] pathWithoutSourceDirectory = new String[]{"org", "example", "App.java"};
    assertThat(reverseParseTree.getFileWithSuffix(module, pathWithoutSourceDirectory)).isEqualTo(target);
  }

  @Test
  void module_aware_resolution_of_input_file_with_an_empty_index_returns_null() {
    var reverseParseTree = new ReversePathTree();
    String[] pathWithoutSourceDirectory = new String[]{"src","main", "java","org", "example", "App.java"};
    assertThat(reverseParseTree.getFileWithSuffix("my-module", pathWithoutSourceDirectory)).isNull();
  }

  @Test
  void module_aware_resolution_of_input_file_with_missing_file_returns_null() {
    var reverseParseTree = new ReversePathTree();
    InputFile differentFile = TestInputFileBuilder.create("", "module-clash/src/main/java/org/example/App.java").build();
    String[] differentFilePath = differentFile.relativePath().split("/");
    reverseParseTree.index(differentFile, differentFilePath);

    String[] pathWithoutSourceDirectory = new String[]{"src","main", "java","org", "example", "App.java"};
    assertThat(reverseParseTree.getFileWithSuffix("my-module", pathWithoutSourceDirectory)).isNull();
  }

  @Test
  void module_aware_resolution_of_input_file_with_missing_file_despite_may_similar_files_returns_null() {
    var reverseParseTree = new ReversePathTree();

    InputFile differentFile = TestInputFileBuilder.create("", "module-clash/src/main/java/org/example/App.java").build();
    String[] differentFilePath = differentFile.relativePath().split("/");
    reverseParseTree.index(differentFile, differentFilePath);

    InputFile yetAnotherDifferentFile = TestInputFileBuilder.create("", "module-clash-again/src/main/java/org/example/App.java").build();
    String[] yeAnotherDifferentFilePath = yetAnotherDifferentFile.relativePath().split("/");
    reverseParseTree.index(yetAnotherDifferentFile, yeAnotherDifferentFilePath);

    String[] pathWithoutSourceDirectory = new String[]{"src","main", "java","org", "example", "App.java"};
    assertThat(reverseParseTree.getFileWithSuffix("my-module", pathWithoutSourceDirectory)).isNull();
  }
}