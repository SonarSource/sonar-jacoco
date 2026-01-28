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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileLocatorTest {

  private static KotlinFileLocator kotlinFileLocator = new KotlinFileLocator(null);
  @Test
  void should_match_suffix() {
    InputFile inputFile = new TestInputFileBuilder("module1", "src/main/java/org/sonar/test/File.java").build();
    FileLocator locator = new FileLocator(Collections.singleton(inputFile), kotlinFileLocator);
    assertThat(locator.getInputFile("org/sonar/test", "File.java")).isEqualTo(inputFile);
  }

  @Test
  void should_match_default_package() {
    InputFile inputFile = new TestInputFileBuilder("module1", "src/main/java/File.java").build();
    FileLocator locator = new FileLocator(Collections.singleton(inputFile), kotlinFileLocator);
    assertThat(locator.getInputFile("", "File.java")).isEqualTo(inputFile);
  }

  @Test
  void should_not_match() {
    InputFile inputFile = new TestInputFileBuilder("module1", "src/main/java/org/sonar/test/File.java").build();
    FileLocator locator = new FileLocator(Collections.singleton(inputFile), kotlinFileLocator);
    assertThat(locator.getInputFile("org/sonar/test", "File2.java")).isNull();
    assertThat(locator.getInputFile("org/sonar/test2", "File.java")).isNull();
  }

  @Test
  void should_match_first_with_many_options() {
    InputFile inputFile1 = new TestInputFileBuilder("module1", "src/main/java/org/sonar/test/File.java").build();
    InputFile inputFile2 = new TestInputFileBuilder("module1", "src/test/java/org/sonar/test/File.java").build();

    FileLocator locator = new FileLocator(Arrays.asList(inputFile1, inputFile2), kotlinFileLocator);
    assertThat(locator.getInputFile("org/sonar/test", "File.java")).isEqualTo(inputFile1);
  }

  @Test
  void should_fallback_on_Kotlin_file_locator_if_file_was_not_found() {
    InputFile inputFile = new TestInputFileBuilder("module1", "src/main/kotlin/File.kt").build();

    KotlinFileLocator kotlinFileLocatorMock = mock(KotlinFileLocator.class);

    when(kotlinFileLocatorMock.getInputFile("org/sonar/test", "File.kt")).thenReturn(inputFile);

    FileLocator locator = new FileLocator(Arrays.asList(inputFile), kotlinFileLocatorMock);

    assertThat(locator.getInputFile("org/sonar/test", "File.kt")).isEqualTo(inputFile);
  }

  @Test
  void should_not_fallback_on_Kotlin_file_locator_if_file_is_not_Kotlin() {
    InputFile inputFile = new TestInputFileBuilder("module1", "src/main/kotlin/File.java").build();

    KotlinFileLocator kotlinFileLocatorMock = mock(KotlinFileLocator.class);
    when(kotlinFileLocatorMock.getInputFile(any(), any())).thenReturn(inputFile);

    FileLocator locator = new FileLocator(Arrays.asList(inputFile), kotlinFileLocatorMock);

    assertThat(locator.getInputFile("org/sonar/test", "File.java")).isNull();
    verify(kotlinFileLocatorMock, never()).getInputFile(any(), any());
  }

  @Test
  void should_be_able_to_look_up_ambiguous_names(@TempDir Path temp) throws IOException {
    /*
      /tmp/junit153873785933058202/my-project
      ├── app
      │   ├── pom.xml
      │   ├── src
      │   │   └── main
      │   │       └── java
      │   │           └── File.java
      │   └── utils
      │       ├── pom.xml
      │       └── src
      │           └── main
      │               └── java
      │                   └── File.java
      ├── pom.xml
      └── utils
          ├── pom.xml
          └── src
              └── main
                  └── java
                      └── File.java
     */

    // Top level project
    Path myProjectBaseDir = Files.createDirectories(temp.resolve("my-project"));
    Path myProjectPomXml = Files.createFile(myProjectBaseDir.resolve("pom.xml"));
    // App module
    Path appModuleBaseDir = Files.createDirectory(myProjectBaseDir.resolve("app"));
    Path appModuleJavaSources = Files.createDirectories(appModuleBaseDir.resolve("src").resolve("main").resolve("java"));
    Path appModuleFileJava = Files.createFile(appModuleJavaSources.resolve("File.java"));
    Path appModulePomXml = Files.createFile(appModuleBaseDir.resolve("pom.xml"));
    // Utils module
    Path utilsModuleBaseDir = Files.createDirectory(myProjectBaseDir.resolve("utils"));
    Path utilsModuleJavaSources = Files.createDirectories(utilsModuleBaseDir.resolve("src").resolve("main").resolve("java"));
    Path utilsModuleFileJava = Files.createFile(utilsModuleJavaSources.resolve("File.java"));
    Path utilsModulePomXml = Files.createFile(utilsModuleBaseDir.resolve("pom.xml"));
    // Utils module nested into App module
    Path nestedUtilsModuleBaseDir = Files.createDirectory(appModuleBaseDir.resolve("utils"));
    Path nestedUtilsModuleJavaSources = Files.createDirectories(nestedUtilsModuleBaseDir.resolve("src").resolve("main").resolve("java"));
    Path nestedUtilsModuleFileJava = Files.createFile(nestedUtilsModuleJavaSources.resolve("File.java"));
    Path nestedUtilsModulePomXml = Files.createFile(nestedUtilsModuleBaseDir.resolve("pom.xml"));

    // Prepare all the input files to index
    InputFile appFile = new TestInputFileBuilder("my-project", myProjectBaseDir.toFile(), appModuleFileJava.toFile()).build();
    InputFile utilsFile = new TestInputFileBuilder("my-project", myProjectBaseDir.toFile(), utilsModuleFileJava.toFile()).build();
    InputFile nestedUtilsFile = new TestInputFileBuilder("my-project", myProjectBaseDir.toFile(), nestedUtilsModuleFileJava.toFile()).build();
    InputFile nestUtilsPomXmlFile = new TestInputFileBuilder("my-project", myProjectBaseDir.toFile(), nestedUtilsModulePomXml.toFile()).build();
    List<InputFile> filesToIndex = List.of(
            new TestInputFileBuilder("my-project", myProjectBaseDir.toFile(), myProjectPomXml.toFile()).build(),
            appFile,
            new TestInputFileBuilder("my-project", myProjectBaseDir.toFile(), appModulePomXml.toFile()).build(),
            utilsFile,
            new TestInputFileBuilder("my-project", myProjectBaseDir.toFile(), utilsModulePomXml.toFile()).build(),
            nestedUtilsFile,
            nestUtilsPomXmlFile
    );

    FileLocator locator = new FileLocator(filesToIndex, null);

    ProjectCoverageContext pcc = new ProjectCoverageContext();
    pcc.setProjectBaseDir(myProjectBaseDir);
    pcc.add(
            new ModuleCoverageContext(
                    "app",
                    appModuleBaseDir,
                    List.of(appModulePomXml, appModuleJavaSources)
            )
    );

    pcc.add(
            new ModuleCoverageContext(
                    "utils",
                    utilsModuleBaseDir,
                    List.of(utilsModulePomXml, utilsModuleJavaSources)
            )
    );

    pcc.add(
            new ModuleCoverageContext(
                    "app-utils",
                    utilsModuleBaseDir,
                    List.of(nestedUtilsModulePomXml, nestedUtilsModuleJavaSources)
            )
    );

    locator.setProjectCoverageContext(pcc);

    // Test existing files
    assertThat(locator.getInputFile("app", "", "File.java")).isEqualTo(appFile);
    assertThat(locator.getInputFile("utils", "", "File.java")).isEqualTo(utilsFile);
    assertThat(locator.getInputFile("app-utils", "", "File.java")).isEqualTo(nestedUtilsFile);
    assertThat(locator.getInputFile("app-utils", "", "pom.xml")).isEqualTo(nestUtilsPomXmlFile);

    // Test non-existing files
    assertThat(locator.getInputFile("app", "org/example", "Main.java")).isNull();
  }

}
