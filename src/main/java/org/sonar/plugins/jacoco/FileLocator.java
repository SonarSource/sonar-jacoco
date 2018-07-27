/*
 * SonarQube JaCoCo Plugin
 * Copyright (C) 2018-2018 SonarSource SA
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
  private final List<InputFile> inputFiles;

  public FileLocator(Iterable<InputFile> inputFiles) {
    this(StreamSupport.stream(inputFiles.spliterator(), false).collect(Collectors.toList()));
  }

  public FileLocator(List<InputFile> inputFiles) {
    this.inputFiles = inputFiles;
  }

  @CheckForNull
  public InputFile getInputFile(String packagePath, String fileName) {
    String filePath = packagePath + "/" + fileName;
    return inputFiles.stream().filter(f -> f.relativePath().endsWith(filePath)).findFirst().orElse(null);
  }
}
