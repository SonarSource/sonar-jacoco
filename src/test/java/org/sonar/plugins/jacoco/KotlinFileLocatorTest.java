/*
 * SonarQube JaCoCo Plugin
 * Copyright (C) 2018-2025 SonarSource Sàrl
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

import java.nio.charset.Charset;
import org.junit.jupiter.api.Test;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class KotlinFileLocatorTest {
  @Test
  void should_return_an_input_file_by_package_and_filename() {
    InputFile inputFile = new TestInputFileBuilder("module", "src/main/java/org/sonar/test/File.kt")
      .setContents("package abc.def.ghijkl")
      .setCharset(Charset.defaultCharset())
      .build();

    KotlinFileLocator kotlinFileLocator = new KotlinFileLocator(Stream.of(inputFile));

    assertThat(kotlinFileLocator.getInputFile("abc/def/ghijkl", "File.kt")).isEqualTo(inputFile);
  }

  @Test
  void should_not_return_an_input_file_if_package_is_incorrect() {

    InputFile inputFile = new TestInputFileBuilder("module", "src/main/java/org/sonar/test/File.kt")
      .setContents("package a.b.c")
      .setCharset(Charset.defaultCharset())
      .build();

    KotlinFileLocator kotlinFileLocator = new KotlinFileLocator(Stream.of(inputFile));

    assertThat(kotlinFileLocator.getInputFile("a/b/c/d", "File.kt")).isNull();
  }
  @Test
  void should_not_return_an_input_file_if_package_is_missing() {

    InputFile inputFile = new TestInputFileBuilder("module", "src/main/java/org/sonar/test/File.kt")
      .setContents("val a = b + c")
      .setCharset(Charset.defaultCharset())
      .build();

    KotlinFileLocator kotlinFileLocator = new KotlinFileLocator(Stream.of(inputFile));

    assertThat(kotlinFileLocator.getInputFile("a/b/c", "File.kt")).isNull();
  }

  @Test
  void should_skip_endline_comments() {
    InputFile inputFile = new TestInputFileBuilder("module", "src/main/java/org/sonar/test/File.kt")
      .setContents("// This is a top-level comment" + System.lineSeparator() +
        "package a.b.c")
      .setCharset(Charset.defaultCharset())
      .build();

    KotlinFileLocator kotlinFileLocator = new KotlinFileLocator(Stream.of(inputFile));

    assertThat(kotlinFileLocator.getInputFile("a/b/c", "File.kt")).isEqualTo(inputFile);
  }

  @Test
  void should_skip_package_in_shebang_line() {
    InputFile inputFile = new TestInputFileBuilder("module", "src/main/java/org/sonar/test/File.kt")
      .setContents("#! /env/usr/bin kotlin package x.y.z" + System.lineSeparator() +
        "package a.b.c")
      .setCharset(Charset.defaultCharset())
      .build();

    KotlinFileLocator kotlinFileLocator = new KotlinFileLocator(Stream.of(inputFile));

    assertThat(kotlinFileLocator.getInputFile("a/b/c", "File.kt")).isEqualTo(inputFile);
    assertThat(kotlinFileLocator.getInputFile("x/y/z", "File.kt")).isNull();
  }

  @Test
  void should_skip_empty_lines() {
    InputFile inputFile = new TestInputFileBuilder("module", "src/main/java/org/sonar/test/File.kt")
      .setContents(" " + System.lineSeparator() +
        "\t" + System.lineSeparator() +
        "" + System.lineSeparator() +
        "package a.b.c")
      .setCharset(Charset.defaultCharset())
      .build();

    KotlinFileLocator kotlinFileLocator = new KotlinFileLocator(Stream.of(inputFile));

    assertThat(kotlinFileLocator.getInputFile("a/b/c", "File.kt")).isEqualTo(inputFile);
  }

  @Test
  void should_skip_inline_delimited_comments_in_package_header() {
    InputFile inputFile = new TestInputFileBuilder("module", "src/main/java/org/sonar/test/File.kt")
      .setContents("package /* comment */ a.b.c")
      .setCharset(Charset.defaultCharset())
      .build();

    KotlinFileLocator kotlinFileLocator = new KotlinFileLocator(Stream.of(inputFile));

    assertThat(kotlinFileLocator.getInputFile("a/b/c", "File.kt")).isEqualTo(inputFile);
  }

  @Test
  void should_skip_multiline_delimited_comments_in_package_header() {
    InputFile inputFile = new TestInputFileBuilder("module", "src/main/java/org/sonar/test/File.kt")
      .setContents("package /*comment" + System.lineSeparator() +
      "continued */ a.b.c")
      .setCharset(Charset.defaultCharset())
      .build();

    KotlinFileLocator kotlinFileLocator = new KotlinFileLocator(Stream.of(inputFile));

    assertThat(kotlinFileLocator.getInputFile("a/b/c", "File.kt")).isEqualTo(inputFile);
  }


  @Test
  void should_skip_delimited_comments_before_endline_comment() {
    InputFile inputFile = new TestInputFileBuilder("module", "src/main/java/org/sonar/test/File.kt")
      .setContents("/**/// package a.b.c")
      .setCharset(Charset.defaultCharset())
      .build();
    KotlinFileLocator kotlinFileLocator = new KotlinFileLocator(Stream.of(inputFile));

    assertThat(kotlinFileLocator.getInputFile("a/b/c", "File.kt")).isNull();
  }

  @Test
  void should_skip_file_annotation_before_package() {
    InputFile inputFile = new TestInputFileBuilder("module", "src/main/java/org/sonar/test/File.kt")
      .setContents("/**/@file:SuppressWarnings(\"UNUSED\")@file:JvmName(\"hi\")package a.b.c")
      .setCharset(Charset.defaultCharset())
      .build();
    KotlinFileLocator kotlinFileLocator = new KotlinFileLocator(Stream.of(inputFile));

    assertThat(kotlinFileLocator.getInputFile("a/b/c", "File.kt")).isEqualTo(inputFile);
  }

  @Test
  void should_not_read_missing_package_header_from_string() {
    InputFile inputFile = new TestInputFileBuilder("module", "src/main/java/org/sonar/test/File.kt")
      .setContents(
        "@file:SuppressWarnings(\"UNUSED\")" + System.lineSeparator() +
          "val a = \"package a.b.c\""
      )
      .setCharset(Charset.defaultCharset())
      .build();
    KotlinFileLocator kotlinFileLocator = new KotlinFileLocator(Stream.of(inputFile));

    assertThat(kotlinFileLocator.getInputFile("a/b/c", "File.kt")).isNull();
  }

  @Test
  void should_not_read_package_header_from_comments() {
    InputFile inputFile = new TestInputFileBuilder("module", "src/main/java/org/sonar/test/File.kt")
      .setContents("/* package x.y.z */ package a.b.c //package g.h.i")
      .setCharset(Charset.defaultCharset())
      .build();
    KotlinFileLocator kotlinFileLocator = new KotlinFileLocator(Stream.of(inputFile));

    assertThat(kotlinFileLocator.getInputFile("x/y/z", "File.kt")).isNull();
    assertThat(kotlinFileLocator.getInputFile("g/h/i", "File.kt")).isNull();
    assertThat(kotlinFileLocator.getInputFile("a/b/c", "File.kt")).isEqualTo(inputFile);
  }

  @Test
  void should_not_read_missing_package_header_from_annotation_value() {
    InputFile inputFile = new TestInputFileBuilder("module", "src/main/java/org/sonar/test/File.kt")
      .setContents("@file:SuppressWarnings(\" package x.y.z \")@file:JvmName(\" package g.h.i \") //package a.b.c")
      .setCharset(Charset.defaultCharset())
      .build();
    KotlinFileLocator kotlinFileLocator = new KotlinFileLocator(Stream.of(inputFile));

    assertThat(kotlinFileLocator.getInputFile("x/y/z", "File.kt")).isNull();
    assertThat(kotlinFileLocator.getInputFile("g/h/i", "File.kt")).isNull();
    assertThat(kotlinFileLocator.getInputFile("a/b/c", "File.kt")).isNull();
  }

  @Test
  void should_not_read_package_header_from_strings_and_annotation_values() {
    InputFile inputFile = new TestInputFileBuilder("module", "src/main/java/org/sonar/test/File.kt")
      .setContents("@file:SuppressWarnings(\" package x.y.z \")@file:JvmName(\" package g.h.i \")  package a.b.c; val c = (\" package d.e.f \")")
      .setCharset(Charset.defaultCharset())
      .build();
    KotlinFileLocator kotlinFileLocator = new KotlinFileLocator(Stream.of(inputFile));

    assertThat(kotlinFileLocator.getInputFile("x/y/z", "File.kt")).isNull();
    assertThat(kotlinFileLocator.getInputFile("g/h/i", "File.kt")).isNull();
    assertThat(kotlinFileLocator.getInputFile("d/e/f", "File.kt")).isNull();
    assertThat(kotlinFileLocator.getInputFile("a/b/c", "File.kt")).isEqualTo(inputFile);
  }

  @Test
  void should_not_read_missing_package_header_from_strings_and_annotation_values() {
    InputFile inputFile = new TestInputFileBuilder("module", "src/main/java/org/sonar/test/File.kt")
      .setContents("@file:SuppressWarnings(\" package x.y.z \")@file:JvmName(\" package g.h.i \") val c = (\" package d.e.f \")")
      .setCharset(Charset.defaultCharset())
      .build();
    KotlinFileLocator kotlinFileLocator = new KotlinFileLocator(Stream.of(inputFile));

    assertThat(kotlinFileLocator.getInputFile("x/y/z", "File.kt")).isNull();
    assertThat(kotlinFileLocator.getInputFile("g/h/i", "File.kt")).isNull();
    assertThat(kotlinFileLocator.getInputFile("d/e/f", "File.kt")).isNull();
    assertThat(kotlinFileLocator.getInputFile("a/b/c", "File.kt")).isNull();
  }

  @Test
  void should_accept_package_with_backticks_and_newlines() {
    InputFile inputFile = new TestInputFileBuilder("module", "src/main/java/org/sonar/test/File.kt")
      .setContents(
              "@file:SuppressWarnings(\" package x.y.z \")@file:JvmName(\" package g.h.i \") val c = (\" package d.e.f \")" + System.lineSeparator() +
                      "package a       " + System.lineSeparator() +
                      "    .`b    b`    " + System.lineSeparator() +
                      ".c   "
      )
      .setCharset(Charset.defaultCharset())
      .build();
    KotlinFileLocator kotlinFileLocator = new KotlinFileLocator(Stream.of(inputFile));

    assertThat(kotlinFileLocator.getInputFile("x/y/z", "File.kt")).isNull();
    assertThat(kotlinFileLocator.getInputFile("g/h/i", "File.kt")).isNull();
    assertThat(kotlinFileLocator.getInputFile("d/e/f", "File.kt")).isNull();
    assertThat(kotlinFileLocator.getInputFile("a/b    b/c", "File.kt")).isEqualTo(inputFile);
  }

  @Test
  void should_skip_comments_inside_package_declaration() {
    InputFile inputFile = new TestInputFileBuilder("module", "src/main/java/org/sonar/test/File.kt")
      .setContents(
              "package a/*abc*/./*def*/b/*ghi*/./*j*/c" + System.lineSeparator()
              + System.lineSeparator()
              + "     // some comment here" + System.lineSeparator()
              + ".d"
      )
      .setCharset(Charset.defaultCharset())
      .build();
    KotlinFileLocator kotlinFileLocator = new KotlinFileLocator(Stream.of(inputFile));

    assertThat(kotlinFileLocator.getInputFile("a/b/c/d", "File.kt")).isEqualTo(inputFile);
  }

  @Test
  void should_accept_package_with_package_keyword_in_backticks() {
    InputFile inputFile = new TestInputFileBuilder("module", "src/main/java/org/sonar/test/File.kt")
            .setContents(
                    "@file:SuppressWarnings(\" package x.y.z \")@file:JvmName(\" package g.h.i \") val c = (\" package d.e.f \")" + System.lineSeparator() +
                            "package a       " + System.lineSeparator() +
                            "    .`package`    " + System.lineSeparator() +
                            ".c   "
            )
            .setCharset(Charset.defaultCharset())
            .build();
    KotlinFileLocator kotlinFileLocator = new KotlinFileLocator(Stream.of(inputFile));

    assertThat(kotlinFileLocator.getInputFile("x/y/z", "File.kt")).isNull();
    assertThat(kotlinFileLocator.getInputFile("g/h/i", "File.kt")).isNull();
    assertThat(kotlinFileLocator.getInputFile("d/e/f", "File.kt")).isNull();
    assertThat(kotlinFileLocator.getInputFile("a/package/c", "File.kt")).isEqualTo(inputFile);
  }
}


