/*
 * SonarQube JaCoCo Plugin
 * Copyright (C) 2018-2020 SonarSource SA
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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.sonar.api.internal.google.common.annotations.VisibleForTesting;
import org.sonar.api.utils.WildcardPattern;

public class FileSystemWildcardPatternScanner {

  private static final int SEARCH_MAX_DEPTH = 64;

  private static final String PATH_MATCHER_SPECIAL_CHAR = "*?";

  private final Path knownPathPrefix;
  @Nullable
  private final String patternPathSuffix;

  private FileSystemWildcardPatternScanner(String patternPath) {
    String unixLikePatternPath = toUnixLikePath(patternPath);
    int specialCharIndex = indexOfMatcherSpecialChar(unixLikePatternPath);
    if (specialCharIndex == -1) {
      knownPathPrefix = toFileSystemPath(unixLikePatternPath);
      patternPathSuffix = null;
    } else {
      int knownFolderEnd = unixLikePatternPath.lastIndexOf('/', specialCharIndex);
      if (knownFolderEnd != -1) {
        knownPathPrefix = toFileSystemPath(unixLikePatternPath.substring(0, knownFolderEnd + 1));
        patternPathSuffix = unixLikePatternPath.substring(knownFolderEnd + 1);
      } else {
        knownPathPrefix = Paths.get(".");
        patternPathSuffix = unixLikePatternPath;
      }
    }
  }

  public static FileSystemWildcardPatternScanner of(String patternPath) {
    return new FileSystemWildcardPatternScanner(patternPath);
  }

  public void scan(Path baseDirectory, Consumer<Path> pathConsumer, Consumer<String> errorHandler) {
    scan(baseDirectory, path -> true, pathConsumer, errorHandler);
  }

  public void scan(Path baseDirectory, Predicate<Path> preFilter, Consumer<Path> pathConsumer, Consumer<String> errorHandler) {
    if (patternPathSuffix == null) {
      Path path = baseDirectory.resolve(knownPathPrefix);
      if (Files.exists(path) && preFilter.test(path)) {
        pathConsumer.accept(path);
      }
      return;
    }
    Path scanBaseFolder = knownPathPrefix.isAbsolute() ? knownPathPrefix : baseDirectory.resolve(knownPathPrefix);
    if (!Files.exists(scanBaseFolder)) {
      return;
    }
    try {
      Path absoluteScanBaseFolder = scanBaseFolder.toRealPath();
      if (absoluteScanBaseFolder.equals(absoluteScanBaseFolder.getRoot())) {
        errorHandler.accept("For performance reason, wildcard pattern search is not possible from filesystem root" +
          " '" + absoluteScanBaseFolder + "' (pattern: '" + patternPathSuffix + "')");
        return;
      }
      WildcardPattern matcher = WildcardPattern.create(toUnixLikePath(absoluteScanBaseFolder.toString()) + "/" + patternPathSuffix);
      try (Stream<Path> stream = Files.walk(absoluteScanBaseFolder, SEARCH_MAX_DEPTH)) {
        stream
          .filter(preFilter)
          .filter(path -> matcher.match(toUnixLikePath(path.toString())))
          .forEach(pathConsumer);
      }
    } catch (IOException | RuntimeException e) {
      errorHandler.accept("Scanning '" + scanBaseFolder + "' with pattern '" + patternPathSuffix + "'" +
        " threw a " + e.getClass().getSimpleName() + ": " + e.getMessage());
    }
  }

  @VisibleForTesting
  static String toUnixLikePath(String path) {
    return path.indexOf('\\') != -1 ? path.replace('\\', '/') : path;
  }

  @VisibleForTesting
  static Path toFileSystemPath(String unixLikePath) {
    return Paths.get(unixLikePath.replace('/', File.separatorChar));
  }

  @VisibleForTesting
  static int indexOfMatcherSpecialChar(String path) {
    for (int i = 0; i < path.length(); i++) {
      if (PATH_MATCHER_SPECIAL_CHAR.indexOf(path.charAt(i)) != -1) {
        return i;
      }
    }
    return -1;
  }

}
