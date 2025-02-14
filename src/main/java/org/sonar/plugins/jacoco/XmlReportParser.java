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
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

public class XmlReportParser {
  private final Path xmlReportPath;

  private static final String COLUMN = " column ";

  public XmlReportParser(Path xmlReportPath) {
    this.xmlReportPath = xmlReportPath;
  }

  public List<SourceFile> parse() {
    XMLInputFactory factory = XMLInputFactory.newInstance();
    factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, false);
    factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

    XMLStreamReader xmlStreamReaderParser = null;
    try (Reader reader = Files.newBufferedReader(xmlReportPath, StandardCharsets.UTF_8)) {
      xmlStreamReaderParser = factory.createXMLStreamReader(reader);
      // Need to be effectively final to be used in Supplier lambdas
      final XMLStreamReader parser = xmlStreamReaderParser;

      List<SourceFile> sourceFiles = new ArrayList<>();

      String packageName = null;
      String sourceFileName = null;

      while (true) {
        int event = parser.next();

        if (event == XMLStreamConstants.END_DOCUMENT) {
          parser.close();
          break;
        } else if (event == XMLStreamConstants.END_ELEMENT) {
          String element = parser.getLocalName();
          if (element.equals("package")) {
            packageName = null;
          } else if (element.equals("sourcefile")) {
            sourceFileName = null;
          }
        } else if (event == XMLStreamConstants.START_ELEMENT) {
          String element = parser.getLocalName();

          if (element.equals("package")) {
            packageName = getStringAttr(parser, "name", () -> "for a 'package' at line " + parser.getLocation().getLineNumber() + COLUMN + parser.getLocation().getColumnNumber());
          } else if (element.equals("sourcefile")) {
            if (packageName == null) {
              throw new IllegalStateException("Invalid report: expected to find 'sourcefile' within a 'package' at line "
                + parser.getLocation().getLineNumber() + COLUMN + parser.getLocation().getColumnNumber());
            }
            sourceFileName = getStringAttr(parser, "name", () -> "for a sourcefile at line "
              + parser.getLocation().getLineNumber() + COLUMN + parser.getLocation().getColumnNumber());
            sourceFiles.add(new SourceFile(packageName, sourceFileName));
          } else if (element.equals("line")) {
            if (sourceFileName == null) {
              throw new IllegalStateException("Invalid report: expected to find 'line' within a 'sourcefile' at line "
                + parser.getLocation().getLineNumber() + COLUMN + parser.getLocation().getColumnNumber());
            }
            SourceFile file = sourceFiles.get(sourceFiles.size() - 1);
            Supplier<String> errorCtx = () -> "for the sourcefile '" + file.name() + "' at line "
              + parser.getLocation().getLineNumber() + COLUMN + parser.getLocation().getColumnNumber();

            Line line = new Line(
              getIntAttr(parser, "nr", errorCtx),
              getOptionalIntAttr(parser, "mi", errorCtx),
              getOptionalIntAttr(parser, "ci", errorCtx),
              getOptionalIntAttr(parser, "mb", errorCtx),
              getOptionalIntAttr(parser, "cb", errorCtx));
            file.lines().add(line);
          }
        }
      }

      return sourceFiles;
    } catch (XMLStreamException | IOException e) {
      throw new IllegalStateException("Failed to parse JaCoCo XML report: " + xmlReportPath.toAbsolutePath(), e);
    } finally {
      if (xmlStreamReaderParser != null) {
        try {
          xmlStreamReaderParser.close();
        } catch (XMLStreamException e) {
          // do nothing - the stream used to read from will be closed by the try-with-resource
        }
      }
    }
  }

  private static String getStringAttr(XMLStreamReader parser, String name, Supplier<String> errorContext) {
    String value = parser.getAttributeValue(null, name);
    if (value == null) {
      throw new IllegalStateException("Invalid report: couldn't find the attribute '" + name + "' " + errorContext.get());
    }
    return value;
  }

  private static int getOptionalIntAttr(XMLStreamReader parser, String name, Supplier<String> errorContext) {
    String value = parser.getAttributeValue(null, name);
    if (value == null) {
      return 0;
    }
    try {
      return Integer.parseInt(value);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid report: failed to parse integer from the attribute '" + name + "' " + errorContext.get());
    }
  }

  private static int getIntAttr(XMLStreamReader parser, String name, Supplier<String> errorContext) {
    String value = getStringAttr(parser, name, errorContext);
    try {
      return Integer.parseInt(value);
    } catch (Exception e) {
      throw new IllegalStateException("Invalid report: failed to parse integer from the attribute '" + name + "' " + errorContext.get());
    }
  }

  static class SourceFile {
    private String name;
    private String packageName;
    private List<Line> lines = new ArrayList<>();

    SourceFile(String packageName, String name) {
      this.name = name;
      this.packageName = packageName;
    }

    public String name() {
      return name;
    }

    public String packageName() {
      return packageName;
    }

    public List<Line> lines() {
      return lines;
    }
  }

  static class Line {
    private int number;
    private int missedInstrs;
    private int coveredInstrs;
    private int missedBranches;
    private int coveredBranches;

    Line(int number, int mi, int ci, int mb, int cb) {
      this.number = number;
      this.missedInstrs = mi;
      this.coveredInstrs = ci;
      this.missedBranches = mb;
      this.coveredBranches = cb;
    }

    public int number() {
      return number;
    }

    public int missedInstrs() {
      return missedInstrs;
    }

    public int coveredInstrs() {
      return coveredInstrs;
    }

    public int missedBranches() {
      return missedBranches;
    }

    public int coveredBranches() {
      return coveredBranches;
    }
  }

}
