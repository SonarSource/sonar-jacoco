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

import java.io.File;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.batch.fs.InputFile;

public abstract class FileLocator {
  public static final String SEPARATOR_REGEX = Pattern.quote(File.separator);

  protected final ReversePathTree tree = new ReversePathTree();
  protected final KotlinFileLocator kotlinFileLocator;

  protected FileLocator(Iterable<InputFile> inputFiles, KotlinFileLocator kotlinFileLocator) {
    this(StreamSupport.stream(inputFiles.spliterator(), false).collect(Collectors.toList()), kotlinFileLocator);
  }

  protected FileLocator(List<InputFile> inputFiles, @Nullable KotlinFileLocator kotlinFileLocator) {
    this.kotlinFileLocator = kotlinFileLocator;
    for (InputFile inputFile : inputFiles) {
      String[] path = inputFile.relativePath().split(SEPARATOR_REGEX);
      tree.index(inputFile, path);
    }
  }

  @CheckForNull
  public InputFile getInputFile(@Nullable String groupName, String packagePath, String fileName) {
    String filePath = packagePath.isEmpty()
            ? fileName
            : (packagePath + '/' + fileName);

    InputFile file = lookup(groupName, filePath);

    if (file == null && fileName.endsWith(".kt")) {
      file = kotlinFileLocator.getInputFile(packagePath, fileName);
    }
    return file;
  }

  @CheckForNull
  protected abstract InputFile lookup(@Nullable String groupName, String filePath);
}
