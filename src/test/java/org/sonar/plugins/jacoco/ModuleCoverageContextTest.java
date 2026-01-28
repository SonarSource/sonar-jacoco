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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonar.api.batch.sensor.internal.SensorContextTester;
import org.sonar.api.config.internal.MapSettings;

import static org.assertj.core.api.Assertions.assertThat;

class ModuleCoverageContextTest {
  @TempDir
  Path temp;

  @Test
  void uses_the_project_key_as_the_name_when_module_key_is_not_defined() {
    SensorContextTester context = SensorContextTester.create(temp);
    MapSettings settings = new MapSettings();
    settings.setProperty("sonar.projectKey", "single-module-project");
    Path projectBaseDir = temp.toAbsolutePath();
    settings.setProperty("sonar.projectBaseDir", projectBaseDir.toString());
    settings.setProperty("sonar.sources", "src/main/kotlin");
    context.setSettings(settings);

    ModuleCoverageContext expected = new ModuleCoverageContext(
            "single-module-project",
            projectBaseDir,
            List.of(projectBaseDir.resolve("src").resolve("main").resolve("kotlin"))
    );

    assertThat(ModuleCoverageContext.from(context)).isEqualTo(expected);
  }

  @Test
  void extracts_information_for_multi_module_project() throws IOException {
    Path projectBaseDir = temp.toAbsolutePath();
    Path moduleBaseDir = temp.resolve("submodule");
    Files.createDirectories(moduleBaseDir);


    // Because the module prefix is systematically popped when analyzing a module, we can use the same keys for each submodule.
    MapSettings settings = new MapSettings()
            .setProperty("sonar.moduleKey", "org.example:submodule")
            .setProperty("sonar.projectBaseDir", projectBaseDir.toString())
            .setProperty("sonar.sources", "src/main/kotlin");

    SensorContextTester context = SensorContextTester.create(projectBaseDir);
    context.setSettings(settings);

    ModuleCoverageContext expected = new ModuleCoverageContext(
            "submodule",
            projectBaseDir,
            List.of(projectBaseDir.resolve("src").resolve("main").resolve("kotlin"))
    );

    assertThat(ModuleCoverageContext.from(context)).isEqualTo(expected);
  }

  @Test
  void equality() {
    var mcc = new ModuleCoverageContext("name", Path.of("name"), List.of(Path.of("src", "main", "java")));
    assertThat(mcc)
            .isEqualTo(mcc)
            .hasSameHashCodeAs(mcc);

    var otherName = new ModuleCoverageContext("other", Path.of("name"), List.of(Path.of("src", "main", "java")));
    var otherBaseDir = new ModuleCoverageContext("name", Path.of("other"), List.of(Path.of("src", "main", "java")));
    var otherSources = new ModuleCoverageContext("name", Path.of("name"), List.of(Path.of("src", "main", "kotlin")));
    assertThat(mcc)
            .isNotEqualTo(otherName)
            .isNotEqualTo(otherBaseDir)
            .isNotEqualTo(otherSources)
            .isNotEqualTo(new Object());
  }
}
