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

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.config.Configuration;

/**
 * The context necessary to enable the aggregate sensor connect a SourceFile to a module.
 */
public class ModuleCoverageContext {
  public final String name;
  public final Path baseDir;
  public final List<Path> sources;

  ModuleCoverageContext(String name, Path baseDir, List<Path> sourceDirectories) {
    this.name = name;
    this.baseDir = baseDir;
    this.sources = sourceDirectories;
  }

  static ModuleCoverageContext from(SensorContext sensorContext) {
    Configuration config = sensorContext.config();
    String moduleKey = extractModuleKey(config);
    Path baseDir = Path.of(config.get("sonar.projectBaseDir").get());
    List<Path> sourceDirectories = Arrays.asList(config.getStringArray("sonar.sources"))
            .stream()
            .map(Path::of)
            .map(dir -> dir.isAbsolute() ? dir : baseDir.resolve(dir))
            .collect(Collectors.toList());
    return new ModuleCoverageContext(moduleKey, baseDir, sourceDirectories);
  }

  static String extractModuleKey(Configuration configuration) {
    Optional<String> module = configuration.get("sonar.moduleKey");
    String moduleKey = module.isPresent()
            ? module.get()
            : configuration.get("sonar.projectKey").get();
    // If the module key contains a colon, we are looking at a subproject and should use the text that follows the last colon in the module key
    int indexOfLastColon = moduleKey.lastIndexOf(':');
    if (indexOfLastColon == -1 || moduleKey.length() <= (indexOfLastColon + 1)) {
      return moduleKey;
    }
    return moduleKey.substring(indexOfLastColon + 1);
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ModuleCoverageContext)) return false;
    ModuleCoverageContext that = (ModuleCoverageContext) o;
    return Objects.equals(name, that.name) &&
            Objects.equals(baseDir, that.baseDir) &&
            Objects.equals(sources, that.sources);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, baseDir, sources);
  }

  @Override
  public String toString() {
    return "ModuleCoverageContext{" +
            "name='" + name + '\'' +
            ", baseDir=" + baseDir +
            ", sourceDirectories=" + sources +
            '}';
  }
}
