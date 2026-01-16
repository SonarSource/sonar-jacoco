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

import java.util.LinkedHashMap;
import java.util.Map;
import org.sonar.api.batch.fs.InputFile;

public class ReversePathTree {
  private Node root = new Node();

  public void index(InputFile inputFile, String[] path) {
    Node currentNode = root;
    for (int i = path.length - 1; i >= 0; i--) {
      currentNode = currentNode.children.computeIfAbsent(path[i], e -> new Node());
    }
    currentNode.file = inputFile;
  }

  public InputFile getFileWithSuffix(String[] path) {
    Node currentNode = root;

    for (int i = path.length - 1; i >= 0; i--) {
      currentNode = currentNode.children.get(path[i]);
      if (currentNode == null) {
        return null;
      }
    }
    return getFirstLeaf(currentNode);
  }

  public InputFile getFileWithSuffix(String module, String[] path) {
    Node currentNode = root;
    for (int i = path.length - 1; i >= 0; i--) {
      currentNode = currentNode.children.get(path[i]);
      if (currentNode == null) {
        return null;
      }
    }

    while (!currentNode.children.isEmpty()) {
      if (currentNode.children.size() == 1) {
        currentNode = currentNode.children.values().iterator().next();
      } else {
        for (String candidate : currentNode.children.keySet()) {
          if (module.equals(candidate)) {
             return currentNode.children.get(candidate).file;
          }
        }
        return null;
      }
    }
    return null;
  }

  private static InputFile getFirstLeaf(Node node) {
    while (!node.children.isEmpty()) {
      node = node.children.values().iterator().next();
    }
    return node.file;
  }

  static class Node {
    Map<String, Node> children = new LinkedHashMap<>();
    InputFile file = null;
  }
}
