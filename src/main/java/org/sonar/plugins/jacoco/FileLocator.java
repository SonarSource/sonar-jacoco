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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.sonar.api.batch.fs.InputFile;

public class FileLocator {
  private final ReversePathTree tree = new ReversePathTree();
  private final KotlinFileLocator kotlinFileLocator;
  private ProjectCoverageContext projectCoverageContext;

  public FileLocator(Iterable<InputFile> inputFiles, KotlinFileLocator kotlinFileLocator) {
    this(StreamSupport.stream(inputFiles.spliterator(), false).collect(Collectors.toList()), kotlinFileLocator);
  }

  void setProjectCoverageContext(ProjectCoverageContext projectCoverageContext) {
    this.projectCoverageContext = projectCoverageContext;
  }

  public FileLocator(List<InputFile> inputFiles, KotlinFileLocator kotlinFileLocator) {
    this.kotlinFileLocator = kotlinFileLocator;
    for (InputFile inputFile : inputFiles) {
      String[] path = inputFile.relativePath().split("/");
      tree.index(inputFile, path);
    }
  }

  @CheckForNull
  /* Visible for testing */
  public InputFile getInputFile(String packagePath, String fileName) {
    return getInputFile(null, packagePath, fileName);
  }

  @CheckForNull
  public InputFile getInputFile(@Nullable String groupName, String packagePath, String fileName) {
    String filePath = packagePath.isEmpty()
            ? fileName
            : (packagePath + '/' + fileName);
    String[] path = filePath.split("/");

    InputFile fileWithSuffix = groupName == null
            ? tree.getFileWithSuffix(path)
            : getInputFileForProject(groupName, filePath);

    if (fileWithSuffix == null && fileName.endsWith(".kt")) {
      fileWithSuffix = kotlinFileLocator.getInputFile(packagePath, fileName);
    }

    return fileWithSuffix;
  }

  @CheckForNull
  private InputFile getInputFileForProject(String groupName, String filePath) {
    // First, try to look up the file in the tree using the computed path
    String[] pathSegments = filePath.split("/");
    InputFile file = tree.getFileWithSuffix(groupName, pathSegments);
    if (file != null) {
      return file;
    }
    // If the file cannot be found by looking up the tree, due for instance to ambiguities between sub-projects with similar structures,
    // then we must rebuild the path by identifying the correct sub-project, and building the path from its known sources
    return projectCoverageContext.getModuleContexts()
            .stream()
            .filter(mcc -> groupName.equals(mcc.name))
            .findFirst()
            .map(mcc -> getInputFileForModule(mcc, filePath)).orElse(null);
  }

  @CheckForNull
  private InputFile getInputFileForModule(ModuleCoverageContext moduleCoverageContext, String filePath) {
    for (Path source : moduleCoverageContext.sources) {
      if (Files.isDirectory(source)) {
        Path relativePath = projectCoverageContext.getProjectBaseDir().relativize(source.resolve(Paths.get(filePath)));
        String[] segments = relativePath.toString().split("/");
        InputFile file = tree.getFileWithSuffix(segments);
        if (file != null) {
          return file;
        }
      } else if (source.endsWith(filePath)) {
        Path relativePah = projectCoverageContext.getProjectBaseDir().relativize(source);
        String[] segments = relativePah.toString().split("/");
        InputFile file = tree.getFileWithSuffix(segments);
        if (file != null) {
          return file;
        }
      }
    }
    return null;
  }
}
