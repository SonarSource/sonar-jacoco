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

import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.sonar.api.batch.fs.InputFile;

public class ModuleFileLocator extends FileLocator {

  public ModuleFileLocator(Iterable<InputFile> inputFiles, KotlinFileLocator kotlinFileLocator) {
    super(StreamSupport.stream(inputFiles.spliterator(), false).collect(Collectors.toList()), kotlinFileLocator);
  }

  /**
   * Convenience method to {@link FileLocator#getInputFile(String, String, String)}, visible for testing.
   */
  InputFile getInputFile(String packagePath, String fileName) {
    return getInputFile(null, packagePath, fileName);
  }

  @Override
  /**
   * Looks up a file in the indexed file tree, discarding the information about the group/subproject name.
   */
  public InputFile lookup(@Nullable String unusedGroupName, String filePath) {
    return tree.getFileWithSuffix(filePath.split(FileLocator.SEPARATOR_REGEX));
  }
}
