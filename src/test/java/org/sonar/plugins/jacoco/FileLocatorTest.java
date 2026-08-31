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

import com.sonarsource.scanner.engine.sensor.test.fixtures.TestInputFileBuilder;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.fs.InputFile;

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
    ModuleFileLocator locator = new ModuleFileLocator(Collections.singleton(inputFile), kotlinFileLocator);
    assertThat(locator.getInputFile("org/sonar/test", "File.java")).isEqualTo(inputFile);
  }

  @Test
  void should_match_default_package() {
    InputFile inputFile = new TestInputFileBuilder("module1", "src/main/java/File.java").build();
    ModuleFileLocator locator = new ModuleFileLocator(Collections.singleton(inputFile), kotlinFileLocator);
    assertThat(locator.getInputFile("", "File.java")).isEqualTo(inputFile);
  }

  @Test
  void should_not_match() {
    InputFile inputFile = new TestInputFileBuilder("module1", "src/main/java/org/sonar/test/File.java").build();
    ModuleFileLocator locator = new ModuleFileLocator(Collections.singleton(inputFile), kotlinFileLocator);
    assertThat(locator.getInputFile("org/sonar/test", "File2.java")).isNull();
    assertThat(locator.getInputFile("org/sonar/test2", "File.java")).isNull();
  }

  @Test
  void should_match_first_with_many_options() {
    InputFile inputFile1 = new TestInputFileBuilder("module1", "src/main/java/org/sonar/test/File.java").build();
    InputFile inputFile2 = new TestInputFileBuilder("module1", "src/test/java/org/sonar/test/File.java").build();

    ModuleFileLocator locator = new ModuleFileLocator(Arrays.asList(inputFile1, inputFile2), kotlinFileLocator);
    assertThat(locator.getInputFile("org/sonar/test", "File.java")).isEqualTo(inputFile1);
    assertThat(locator.getInputFiles(null, "org/sonar/test", "File.java")).containsExactly(inputFile1);
  }

  @Test
  void should_fallback_on_Kotlin_file_locator_if_file_was_not_found() {
    InputFile inputFile = new TestInputFileBuilder("module1", "src/main/kotlin/File.kt").build();

    KotlinFileLocator kotlinFileLocatorMock = mock(KotlinFileLocator.class);

    when(kotlinFileLocatorMock.getInputFile("org/sonar/test", "File.kt")).thenReturn(inputFile);

    ModuleFileLocator locator = new ModuleFileLocator(Arrays.asList(inputFile), kotlinFileLocatorMock);

    assertThat(locator.getInputFile("org/sonar/test", "File.kt")).isEqualTo(inputFile);
  }

  @Test
  void should_not_fallback_on_Kotlin_file_locator_if_file_is_not_Kotlin() {
    InputFile inputFile = new TestInputFileBuilder("module1", "src/main/kotlin/File.java").build();

    KotlinFileLocator kotlinFileLocatorMock = mock(KotlinFileLocator.class);
    when(kotlinFileLocatorMock.getInputFile(any(), any())).thenReturn(inputFile);

    ModuleFileLocator locator = new ModuleFileLocator(Arrays.asList(inputFile), kotlinFileLocatorMock);

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
                    nestedUtilsModuleBaseDir,
                    List.of(nestedUtilsModulePomXml, nestedUtilsModuleJavaSources)
            )
    );

    ProjectFileLocator locator = new ProjectFileLocator(filesToIndex, null, pcc);

    // Test existing files
    assertThat(locator.getInputFile("app", "", "File.java")).isEqualTo(appFile);
    assertThat(locator.getInputFiles("app", "", "File.java")).containsExactly(appFile);
    assertThat(locator.getInputFile("utils", "", "File.java")).isEqualTo(utilsFile);
    assertThat(locator.getInputFile("app-utils", "", "File.java")).isEqualTo(nestedUtilsFile);
    assertThat(locator.getInputFile("app-utils", "", "pom.xml")).isEqualTo(nestUtilsPomXmlFile);

    // Test non-existing files
    assertThat(locator.getInputFile("app", "org/example", "Main.java")).isNull();
  }

  @Test
  void module_file_locator_should_not_fail_when_locating_a_file_with_a_group_but_missing_project_coverage_context() {
    ModuleFileLocator locator = new ModuleFileLocator(Collections.emptyList(), null);
    // Should return null and not blow up with an NPE
    assertThat(locator.getInputFile("group", "org/package", "NotRelevant.java")).isNull();
  }

  @Test
  void module_file_locator_should_not_fail_when_kotlin_file_locator_is_null() {
    ModuleFileLocator locator = new ModuleFileLocator(Collections.emptyList(), null);

    assertThat(locator.getInputFiles(null, "org/example", "Missing.kt")).isEmpty();
  }

  @Test
  void project_file_locator_should_not_fail_when_locating_a_file_with_a_null_group(@TempDir Path tmp) {
    var projectCoverageContext = new ProjectCoverageContext();
    projectCoverageContext.add(new ModuleCoverageContext(
            "utils",
            tmp.getRoot().resolve("utils"),
            List.of(Path.of("src", "main", "java"))
    ));
    ProjectFileLocator locator = new ProjectFileLocator(List.of(), null, projectCoverageContext);
    // Should return null and not blow up with an NPE
    assertThat(locator.getInputFile(null, "", "DoesNotExist.java")).isNull();
  }

  @Test
  void project_file_locator_should_return_all_ambiguous_main_files_for_a_null_group() {
    InputFile appFile = new TestInputFileBuilder("my-project", "app/src/main/java/org/example/File.java").build();
    InputFile appTestFile = new TestInputFileBuilder("my-project", "app/src/test/java/org/example/File.java")
            .setType(InputFile.Type.TEST)
            .build();
    InputFile utilsFile = new TestInputFileBuilder("my-project", "utils/src/main/java/org/example/File.java").build();

    ProjectFileLocator locator = new ProjectFileLocator(List.of(appFile, appTestFile, utilsFile), null, new ProjectCoverageContext());

    // The compatibility method still returns the first match.
    assertThat(locator.getInputFile(null, "org/example", "File.java")).isEqualTo(appFile);
    // A group-less project-level report has no module identity, so every matching main source receives its coverage.
    assertThat(locator.getInputFiles(null, "org/example", "File.java")).containsExactly(appFile, utilsFile);
  }

  @Test
  void project_file_locator_should_not_fan_out_a_default_package_file() {
    InputFile defaultPackageFile = new TestInputFileBuilder("my-project", "app/src/main/java/File.java").build();
    InputFile packagedFile = new TestInputFileBuilder("my-project", "utils/src/main/java/org/example/File.java").build();

    ProjectFileLocator locator = new ProjectFileLocator(List.of(defaultPackageFile, packagedFile), null, new ProjectCoverageContext());

    assertThat(locator.getInputFiles(null, "", "File.java")).containsExactly(defaultPackageFile);
  }

  @Test
  void project_file_locator_should_return_kotlin_suffix_match_when_kotlin_file_locator_is_null() {
    InputFile inputFile = new TestInputFileBuilder("my-project", "app/src/main/kotlin/org/example/File.kt")
            .setLanguage("kotlin")
            .build();
    ProjectFileLocator locator = new ProjectFileLocator(List.of(inputFile), null, new ProjectCoverageContext());

    assertThat(locator.getInputFiles(null, "org/example", "File.kt")).containsExactly(inputFile);
  }

  @Test
  void project_file_locator_should_return_all_main_kotlin_files_found_by_package_declaration() {
    InputFile appFile = new TestInputFileBuilder("my-project", "app/src/main/kotlin/org/example/File.kt")
            .setLanguage("kotlin")
            .setContents("package org.example")
            .setCharset(Charset.defaultCharset())
            .build();
    InputFile appTestFile = new TestInputFileBuilder("my-project", "app/src/test/kotlin/org/example/File.kt")
            .setLanguage("kotlin")
            .setType(InputFile.Type.TEST)
            .setContents("package org.example")
            .setCharset(Charset.defaultCharset())
            .build();
    InputFile utilsFile = new TestInputFileBuilder("my-project", "utils/src/main/kotlin/utils/File.kt")
            .setLanguage("kotlin")
            .setContents("package org.example")
            .setCharset(Charset.defaultCharset())
            .build();
    List<InputFile> inputFiles = List.of(appFile, appTestFile, utilsFile);
    KotlinFileLocator projectKotlinFileLocator = new KotlinFileLocator(inputFiles.stream());
    ProjectFileLocator locator = new ProjectFileLocator(inputFiles, projectKotlinFileLocator, new ProjectCoverageContext());

    assertThat(locator.getInputFiles(null, "org/example", "File.kt")).containsExactly(appFile, utilsFile);
  }

  @Test
  void should_have_files_coverable_by_jacoco_for_main_java_files() {
    InputFile inputFile = inputFile("src/main/java/File.java", "java", InputFile.Type.MAIN);
    ModuleFileLocator locator = new ModuleFileLocator(Collections.singleton(inputFile), kotlinFileLocator);
    assertThat(locator.hasFilesCoverableByJacoco()).isTrue();
  }

  @Test
  void should_have_files_coverable_by_jacoco_for_main_kotlin_files() {
    InputFile inputFile = inputFile("src/main/kotlin/File.kt", "kotlin", InputFile.Type.MAIN);
    ModuleFileLocator locator = new ModuleFileLocator(Collections.singleton(inputFile), kotlinFileLocator);
    assertThat(locator.hasFilesCoverableByJacoco()).isTrue();
  }

  @Test
  void should_have_files_coverable_by_jacoco_when_only_some_indexed_files_are_coverable() {
    // A module usually indexes more than its main sources: a single coverable file is enough.
    InputFile mainFile = inputFile("src/main/java/File.java", "java", InputFile.Type.MAIN);
    InputFile pomFile = inputFile("pom.xml", "xml", InputFile.Type.MAIN);
    ModuleFileLocator locator = new ModuleFileLocator(Arrays.asList(pomFile, mainFile), kotlinFileLocator);
    assertThat(locator.hasFilesCoverableByJacoco()).isTrue();
  }

  @Test
  void should_not_have_files_coverable_by_jacoco_for_test_files() {
    InputFile inputFile = inputFile("src/test/java/FileTest.java", "java", InputFile.Type.TEST);
    ModuleFileLocator locator = new ModuleFileLocator(Collections.singleton(inputFile), kotlinFileLocator);
    assertThat(locator.hasFilesCoverableByJacoco()).isFalse();
  }

  @Test
  void should_not_have_files_coverable_by_jacoco_for_languages_not_covered_by_jacoco() {
    InputFile inputFile = inputFile("pom.xml", "xml", InputFile.Type.MAIN);
    ModuleFileLocator locator = new ModuleFileLocator(Collections.singleton(inputFile), kotlinFileLocator);
    assertThat(locator.hasFilesCoverableByJacoco()).isFalse();
  }

  @Test
  void should_not_have_files_coverable_by_jacoco_for_files_without_language() {
    InputFile inputFile = inputFile("src/main/resources/data.txt", null, InputFile.Type.MAIN);
    ModuleFileLocator locator = new ModuleFileLocator(Collections.singleton(inputFile), kotlinFileLocator);
    assertThat(locator.hasFilesCoverableByJacoco()).isFalse();
  }

  @Test
  void should_not_have_files_coverable_by_jacoco_when_no_file_is_indexed() {
    ModuleFileLocator locator = new ModuleFileLocator(Collections.emptyList(), kotlinFileLocator);
    assertThat(locator.hasFilesCoverableByJacoco()).isFalse();
  }

  private static InputFile inputFile(String relativePath, String language, InputFile.Type type) {
    return new TestInputFileBuilder("module1", relativePath).setLanguage(language).setType(type).build();
  }

}
