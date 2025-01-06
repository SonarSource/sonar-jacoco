/*
 * SonarQube JaCoCo Plugin
 * Copyright (C) 2018-2025 SonarSource SA
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
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.utils.log.Logger;
import org.sonar.api.utils.log.Loggers;

public class KotlinFileLocator {
  private static final Logger LOGGER = Loggers.get(KotlinFileLocator.class);
  private static final String SHEBANG_LINE = "#![^\n]*+";
  private static final String DELIMITED_COMMENT = "/\\*(?:(?!\\*/).)*+\\*/";
  private static final String LINE_COMMENT = "//[^\n]*+";
  private static final String STRING_LITERAL = "\"([^\"]|(?<=\\\\)\")*+\"";
  private static final String MULTILINE_STRING_LITERAL = "\"\"\"(?:(?!\"\"\").)*+\"\"\"";
  private static final String PRE_PACKAGE = "(?s)^(" + SHEBANG_LINE + ")?(" + DELIMITED_COMMENT + "|" + LINE_COMMENT + "|"
                                            + STRING_LITERAL + "|" + MULTILINE_STRING_LITERAL + "|" + "(?!package).)*+";
  private static final String HIDDEN = "(" + DELIMITED_COMMENT + "|" + LINE_COMMENT + "|[\\u0020\\u0009\\u000c]|\r?\n)*+";
  private static final String IDENTIFIER = "([\\p{Lu}\\p{Lo}\\p{Ll}\\p{Lt}\\p{Lm}_\\p{Nd}]++|`[^\r\n`]++`)";
  private static final String PACKAGE_DECLARATION_REGEX = "package" + HIDDEN + "(?<packageName>" + IDENTIFIER + "("
                                                          + HIDDEN + "\\." + HIDDEN + IDENTIFIER + ")*+)";
  private static final Pattern PACKAGE_REGEX = Pattern.compile(PRE_PACKAGE + PACKAGE_DECLARATION_REGEX);
  private static final Pattern FIRST_IDENTIFIER_REGEX = Pattern.compile(HIDDEN + "(?<firstIdentifier>" + IDENTIFIER + ")");
  private static final Pattern NEXT_IDENTIFIER_REGEX = Pattern.compile(HIDDEN + "\\." + HIDDEN + "(?<nextIdentifier>" + IDENTIFIER + ")");

  private final Map<String, InputFile> fqnToInputFile = new HashMap<>();
  private boolean populated = false;
  private final Stream<InputFile> inputFileStream;

  public KotlinFileLocator(Stream<InputFile> kotlinInputFileStream) {
    inputFileStream = kotlinInputFileStream;
  }

  public InputFile getInputFile(String packagePath, String fileName) {
    String fqn = packagePath.replace("/", ".") + "." + fileName;
    if (!populated) {
      populate();
    }
    return fqnToInputFile.get(fqn);
  }

  private void populate() {
    inputFileStream.forEach(f -> {
      try {
        String packageName = getPackage(f.contents());
        if (packageName != null) {
          String key = packageName + "." + f.filename();
          fqnToInputFile.put(key, f);
        }
      } catch (IOException e) {
        LOGGER.error(e.getMessage());
      }
    });
    populated = true;
  }

  /*
  The idea is to skip everything before the package declaration. 'package' is a keyword and can't be used as an identifier.
  However, 'package' can be inside comments, shebang line or string literals, so we need to match them explicitly.
  */
  private static String getPackage(String content) {
    Matcher matcher = PACKAGE_REGEX.matcher(content);
    if (matcher.find()) {
      StringBuilder resolvedPackage = new StringBuilder();
      String packageName = matcher.group("packageName");
      Matcher firstIdentifierMatcher = FIRST_IDENTIFIER_REGEX.matcher(packageName);
      // The find() invocation will always return true as we've already matched the big regular expression
      firstIdentifierMatcher.find();
      resolvedPackage.append(removeBackticks(firstIdentifierMatcher.group("firstIdentifier")));
      Matcher nextIdentifierMatcher = NEXT_IDENTIFIER_REGEX.matcher(packageName);
      while (nextIdentifierMatcher.find()) {
        resolvedPackage.append(".");
        resolvedPackage.append(removeBackticks(nextIdentifierMatcher.group("nextIdentifier")));
      }

      return resolvedPackage.toString();
    }
    return null;
  }

  private static String removeBackticks(String s) {
    if (s.startsWith("`") && s.endsWith("`")) {
      return s.substring(1, s.length() - 1);
    }
    return s;
  }
}
