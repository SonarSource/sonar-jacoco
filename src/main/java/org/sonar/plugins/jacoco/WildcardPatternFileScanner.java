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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.sonar.api.internal.google.common.annotations.VisibleForTesting;
import org.sonar.api.utils.WildcardPattern;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public final class WildcardPatternFileScanner {

  private static final Logger LOG = Loggers.get(WildcardPatternFileScanner.class);

  private static final int SEARCH_MAX_DEPTH = 64;

  private static final String PATH_MATCHER_SPECIAL_CHAR = "*?";

  private WildcardPatternFileScanner() {
    // utility class
  }

  public static List<Path>  scan(Path baseDirectory, String patternPath) {
    String unixLikePatternPath = toUnixLikePath(patternPath);
    int specialCharIndex = indexOfMatcherSpecialChar(unixLikePatternPath);
    if (specialCharIndex == -1) {
      return scanNonWildcardPattern(baseDirectory, unixLikePatternPath);
    } else {
      int additionalBaseDirectoryPart = unixLikePatternPath.lastIndexOf('/', specialCharIndex);
      if (additionalBaseDirectoryPart != -1) {
        Path additionalBaseDirectory = toFileSystemPath(unixLikePatternPath.substring(0, additionalBaseDirectoryPart + 1));
        String remainingWildcardPart = unixLikePatternPath.substring(additionalBaseDirectoryPart + 1);
        Path moreSpecificBaseDirectory = baseDirectory.resolve(additionalBaseDirectory);
        return scanWildcardPattern(moreSpecificBaseDirectory, remainingWildcardPart);
      } else {
        return scanWildcardPattern(baseDirectory, unixLikePatternPath);
      }
    }
  }
  private static List<Path> scanNonWildcardPattern(Path baseDirectory, String unixLikePath) {
    Path path = baseDirectory.resolve(toFileSystemPath(unixLikePath));
    if (Files.isRegularFile(path)) {
      return Collections.singletonList(path);
    }
    return Collections.emptyList();
  }

  private static List<Path> scanWildcardPattern(Path baseDirectory, String unixLikePatternPath) {
    if (!Files.exists(baseDirectory)) {
      return Collections.emptyList();
    }
    try {
      Path absoluteBaseDirectory = baseDirectory.toRealPath();
      if (absoluteBaseDirectory.equals(absoluteBaseDirectory.getRoot())) {
        throw new IOException("For performance reason, wildcard pattern search is not possible from filesystem root");
      }
      List<Path> paths = new ArrayList<>();
      WildcardPattern matcher = WildcardPattern.create(toUnixLikePath(absoluteBaseDirectory.toString()) + "/" + unixLikePatternPath);
      try (Stream<Path> stream = Files.walk(absoluteBaseDirectory, SEARCH_MAX_DEPTH)) {
        stream
          .filter(Files::isRegularFile)
          .filter(path -> matcher.match(toUnixLikePath(path.toString())))
          .forEach(paths::add);
      }
      return paths;
    } catch (IOException | RuntimeException e) {
      LOG.error("Failed to get Jacoco report paths: Scanning '" + baseDirectory + "' with pattern '" + unixLikePatternPath + "'" +
        " threw a " + e.getClass().getSimpleName() + ": " + e.getMessage());
      return Collections.emptyList();
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
