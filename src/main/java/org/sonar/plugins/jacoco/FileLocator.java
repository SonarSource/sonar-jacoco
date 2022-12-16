/*
 * SonarQube JaCoCo Plugin
 * Copyright (C) 2018-2022 SonarSource SA
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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.CheckForNull;
import org.sonar.api.batch.fs.InputFile;

public class FileLocator {
  private final ReversePathTree tree = new ReversePathTree();
  private final KotlinFileLocator kotlinFileLocator;

  public FileLocator(Iterable<InputFile> inputFiles, KotlinFileLocator kotlinFileLocator) {
    this(StreamSupport.stream(inputFiles.spliterator(), false).collect(Collectors.toList()), kotlinFileLocator);
  }

  public FileLocator(List<InputFile> inputFiles, KotlinFileLocator kotlinFileLocator) {
    this.kotlinFileLocator = kotlinFileLocator;
    for (InputFile inputFile : inputFiles) {
      String[] path = inputFile.relativePath().split("/");
      tree.index(inputFile, path);
    }
  }

  @CheckForNull
  public InputFile getInputFile(String packagePath, String fileName) {
    String filePath = packagePath.isEmpty() ? fileName : (packagePath + "/" + fileName);
    String[] path = filePath.split("/");
    InputFile fileWithSuffix = tree.getFileWithSuffix(path);
    if (fileWithSuffix == null && fileName.endsWith(".kt")) {
      fileWithSuffix = kotlinFileLocator.getInputFile(packagePath, fileName);
    }

    return fileWithSuffix;
  }
}
