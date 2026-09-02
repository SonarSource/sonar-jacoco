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

import java.io.File;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.batch.fs.InputFile;

public abstract class FileLocator {
  public static final String SEPARATOR_REGEX = Pattern.quote(File.separator);
  private static final Set<String> COVERED_LANGUAGES = Set.of("java", "kotlin");

  protected final ReversePathTree tree = new ReversePathTree();
  protected final KotlinFileLocator kotlinFileLocator;
  private final int coverableFileCount;

  protected FileLocator(Iterable<InputFile> inputFiles, KotlinFileLocator kotlinFileLocator) {
    this(StreamSupport.stream(inputFiles.spliterator(), false).toList(), kotlinFileLocator);
  }

  protected FileLocator(List<InputFile> inputFiles, @Nullable KotlinFileLocator kotlinFileLocator) {
    this.kotlinFileLocator = kotlinFileLocator;
    int coverable = 0;
    for (InputFile inputFile : inputFiles) {
      // InputFile.relativePath() always uses '/' as separator
      String[] path = inputFile.relativePath().split("/");
      tree.index(inputFile, path);
      if (isCoverableByJacoco(inputFile)) {
        coverable++;
      }
    }
    this.coverableFileCount = coverable;
  }

  private static boolean isCoverableByJacoco(InputFile inputFile) {
    return inputFile.type() == InputFile.Type.MAIN && inputFile.language() != null && COVERED_LANGUAGES.contains(inputFile.language());
  }

  public boolean hasFilesCoverableByJacoco() {
    return coverableFileCount > 0;
  }

  int skippedAmbiguousReportEntries() {
    return 0;
  }

  @CheckForNull
  public InputFile getInputFile(@Nullable String groupName, String packagePath, String fileName) {
    List<InputFile> files = getInputFiles(groupName, packagePath, fileName);
    return files.isEmpty() ? null : files.get(0);
  }

  protected List<InputFile> getInputFiles(@Nullable String groupName, String packagePath, String fileName) {
    String filePath = packagePath.isEmpty()
            ? fileName
            : normalizePath(packagePath + "/" + fileName);

    List<InputFile> files = lookupAll(groupName, filePath);

    if (fileName.endsWith(".kt")) {
      return lookupKotlinFiles(packagePath, fileName, files);
    }
    return files;
  }

  protected List<InputFile> lookupKotlinFiles(String packagePath, String fileName, List<InputFile> files) {
    if (!files.isEmpty() || kotlinFileLocator == null) {
      return files;
    }
    InputFile file = kotlinFileLocator.getInputFile(packagePath, fileName);
    return file == null ? List.of() : List.of(file);
  }

  protected List<InputFile> lookupAll(@Nullable String groupName, String filePath) {
    InputFile file = lookup(groupName, filePath);
    return file == null ? List.of() : List.of(file);
  }

  @CheckForNull
  protected abstract InputFile lookup(@Nullable String groupName, String filePath);

  private static String normalizePath(String path) {
    return path.replace("/", File.separator);
  }

}
