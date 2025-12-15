package org.sonar.plugins.jacoco.its;

import com.sonar.orchestrator.junit4.OrchestratorRule;
import com.sonar.orchestrator.junit4.OrchestratorRuleBuilder;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.MavenBuild;
import com.sonar.orchestrator.build.SonarScanner;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.Location;
import com.sonar.orchestrator.locator.MavenLocation;
import com.sonar.orchestrator.locator.URLLocation;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonarqube.ws.WsMeasures;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;
import org.sonarqube.ws.client.measure.ComponentWsRequest;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class JacocoTest {
  private final static String PROJECT_KEY = "jacoco-test-project";
  private static final String FILE_KEY = "jacoco-test-project:src/main/java/org/sonarsource/test/Calc.java";
  private static final String KOTLIN_FILE_KEY = "org.sonarsource.it.projects:kotlin-jacoco-project:src/main/kotlin/CoverMe.kt";
  private static final String FILE_WITHOUT_COVERAGE_KEY = "jacoco-test-project:src/main/java/org/sonarsource/test/CalcNoCoverage.java";

  private static OrchestratorRule orchestrator;

  @TempDir
  Path temp;

  @BeforeAll
  static void beforeAll() {
    String defaultRuntimeVersion = "true".equals(System.getenv("SONARSOURCE_QA")) ? null : "LATEST_RELEASE";
    OrchestratorRuleBuilder builder = OrchestratorRule.builderEnv()
      .useDefaultAdminCredentialsForBuilds(true)
      .setOrchestratorProperty("orchestrator.workspaceDir", "build")
      .setSonarVersion(System.getProperty("sonar.runtimeVersion", defaultRuntimeVersion));

    String pluginVersion = System.getProperty("jacocoVersion");
    Location pluginLocation;
    if (StringUtils.isEmpty(pluginVersion) || pluginVersion.endsWith("-SNAPSHOT")) {
      pluginLocation = FileLocation.byWildcardMavenFilename(new File("../build/libs"), "sonar-jacoco-*.jar");
    } else {
      pluginLocation = MavenLocation.of("org.sonarsource.jacoco", "sonar-jacoco-plugin", pluginVersion);
    }
    builder.addPlugin(pluginLocation);
    try {
      // The versions of these 2 plugins were chosen because they have shipped with SQS 2025.1 and greater
      builder.addPlugin(URLLocation.create(new URL("https://binaries.sonarsource.com/Distribution/sonar-java-plugin/sonar-java-plugin-8.9.3.40136.jar")));
      builder.addPlugin(URLLocation.create(new URL("https://binaries.sonarsource.com/Distribution/sonar-kotlin-plugin/sonar-kotlin-plugin-2.22.1.6674.jar")));
    } catch (MalformedURLException e) {
      throw new IllegalStateException("Failed to download plugin", e);
    }
    orchestrator = builder.build();
    orchestrator.start();
  }

  @AfterAll
  static void afterAll() {
    orchestrator.stop();
  }

  @Test
  void should_import_coverage() throws IOException {
    SonarScanner build = SonarScanner.create()
      .setProjectKey(PROJECT_KEY)
      .setDebugLogs(true)
      .setSourceDirs("src/main")
      .setTestDirs("src/test")
      .setProperty("sonar.coverage.jacoco.xmlReportPaths", "jacoco.xml")
      .setProperty("sonar.java.binaries", ".")
      .setProjectDir(prepareProject("simple-project-jacoco"));
    orchestrator.executeBuild(build);

    checkCoveredFile();
    checkUncoveredFile();
  }

  @Test
  void should_import_coverage_from_one_of_default_locations() throws IOException {
    SonarScanner build = SonarScanner.create()
      .setProjectKey(PROJECT_KEY)
      .setDebugLogs(true)
      .setSourceDirs("src/main")
      .setTestDirs("src/test")
      .setProperty("sonar.java.binaries", ".")
      .setProjectDir(prepareProject("simple-project-jacoco"));
    orchestrator.executeBuild(build);

    checkCoveredFile();
    checkUncoveredFile();
  }

  @Test
  void should_import_coverage_even_when_java_also_imports() throws IOException {
    SonarScanner build = SonarScanner.create()
      .setProjectKey(PROJECT_KEY)
      .setDebugLogs(true)
      .setSourceDirs("src/main")
      .setTestDirs("src/test")
      .setProperty("sonar.coverage.jacoco.xmlReportPaths", "jacoco.xml")
      .setProperty("sonar.jacoco.reportPath", "jacoco.exec")
      .setProperty("sonar.java.binaries", ".")
      .setProjectDir(prepareProject("simple-project-jacoco"));
    orchestrator.executeBuild(build);

    checkCoveredFile();
    checkUncoveredFile();
  }

  @Test
  void should_give_warning_if_report_doesnt_exist() throws IOException {
    SonarScanner build = SonarScanner.create()
      .setProjectKey(PROJECT_KEY)
      .setDebugLogs(true)
      .setSourceDirs("src/main")
      .setTestDirs("src/test")
      .setProperty("sonar.coverage.jacoco.xmlReportPaths", "invalid_file.xml")
      .setProperty("sonar.java.binaries", ".")
      .setProjectDir(prepareProject("simple-project-jacoco"));
    BuildResult result = orchestrator.executeBuild(build);
    result.getLogs().contains("Report doesn't exist: ");
  }

  @Test
  void should_not_import_coverage_if_no_property_given() throws IOException {
    File baseDir = prepareProject("simple-project-jacoco");
    Files.delete(baseDir.toPath().resolve("target/site/jacoco-it/jacoco.xml"));
    SonarScanner build = SonarScanner.create()
      .setProjectKey(PROJECT_KEY)
      .setDebugLogs(true)
      .setSourceDirs("src/main")
      .setTestDirs("src/test")
      .setProperty("sonar.java.binaries", ".")
      .setProjectDir(baseDir);
    orchestrator.executeBuild(build);
    checkNoJacocoCoverage();
  }

  @Test
  void should_not_import_coverage_if_report_contains_files_that_cant_be_found() throws IOException {
    SonarScanner build = SonarScanner.create()
      .setProjectKey(PROJECT_KEY)
      .setDebugLogs(true)
      .setSourceDirs("src/main")
      .setTestDirs("src/test")
      .setProperty("sonar.java.binaries", ".")
      .setProperty("sonar.coverage.jacoco.xmlReportPaths", "jacoco-with-invalid-sources.xml")
      .setProjectDir(prepareProject("simple-project-jacoco"));
    orchestrator.executeBuild(build);
    checkNoJacocoCoverage();
  }

  @Test
  void no_failure_with_invalid_reports() throws IOException {
    SonarScanner build = SonarScanner.create()
      .setProjectKey(PROJECT_KEY)
      .setDebugLogs(true)
      .setSourceDirs("src/main")
      .setTestDirs("src/test")
      .setProperty("sonar.java.binaries", ".")
      .setProperty("sonar.coverage.jacoco.xmlReportPaths", "jacoco-with-invalid-lines.xml,jacoco-with-invalid-format.xml")
      .setProjectDir(prepareProject("simple-project-jacoco"));
    orchestrator.executeBuild(build);
    checkCoveredFile();

    // No coverage info from JaCoCo for second file
    Map<String, Double> measures = getCoverageMeasures(FILE_WITHOUT_COVERAGE_KEY);
    assertThat(measures.get("line_coverage")).isEqualTo(0.0);
    assertThat(measures.get("lines_to_cover")).isEqualTo(6);
    assertThat(measures.get("uncovered_lines")).isEqualTo(6.0);
    assertThat(measures.get("branch_coverage")).isNull();
    assertThat(measures.get("conditions_to_cover")).isNull();
    assertThat(measures.get("uncovered_conditions")).isNull();
    assertThat(measures.get("coverage")).isEqualTo(0.0);
  }

  @Test
  void aggregate_and_module_based_reports_complement_each_over_to_build_total_coverage() {
    Path project = Path.of("src", "test", "resources", "aggregate-and-module-based-mixed-coverage");
    Path rootPom = project.resolve("pom.xml");
    Path reportLocation = project.resolve("report")
            .resolve("target")
            .resolve("site")
            .resolve("jacoco-aggregate")
            .resolve("jacoco.xml");
    MavenBuild build = MavenBuild.create()
            .setPom(rootPom.toFile())
            .addGoal("clean verify")
            .addSonarGoal()
            .setProperty("sonar.coverage.jacoco.aggregateXmlReportPath", reportLocation.toAbsolutePath().toString());

    orchestrator.executeBuild(build, true);

    Map<String, Double> measuresForLibrary = getCoverageMeasures("org.example:aggregate-and-module-based-mixed-coverage:library/src/main/java/org/example/Library.java");
    assertThat(measuresForLibrary)
            .containsEntry("line_coverage", 100.0)
            .containsEntry("lines_to_cover", 4.0)
            .containsEntry("uncovered_lines", 0.0)
            .containsEntry("branch_coverage", 100.0)
            .containsEntry("conditions_to_cover", 2.0)
            .containsEntry("uncovered_conditions", 0.0)
            .containsEntry("coverage", 100.0);

    Map<String, Double> measuresForSquarer = getCoverageMeasures("org.example:aggregate-and-module-based-mixed-coverage:self-covered/src/main/java/org/example/Squarer.java");
    assertThat(measuresForSquarer)
            .containsEntry("line_coverage", 100.0)
            .containsEntry("lines_to_cover", 2.0)
            .containsEntry("uncovered_lines", 0.0)
            .containsEntry("coverage", 100.0);
  }

  @Test
  void kotlin_files_should_be_located_and_covered() {
    Path BASE_DIRECTORY = Paths.get("src/test/resources");
    MavenBuild build = MavenBuild.create()
            .setPom(new File(BASE_DIRECTORY.toFile(), "kotlin-jacoco-project/pom.xml"))
            .setGoals("clean install", "sonar:sonar");
    orchestrator.executeBuild(build);

    checkCoveredKotlinFile();
  }

  private void checkNoJacocoCoverage() {
    Map<String, Double> measures = getCoverageMeasures(FILE_KEY);
    assertThat(measures.get("line_coverage")).isEqualTo(0.0);
    // java doesn't consider the declaration of the constructor as executable line, so less one than with jacoco
    assertThat(measures.get("lines_to_cover")).isEqualTo(10.0);
    assertThat(measures.get("uncovered_lines")).isEqualTo(10.0);
    assertThat(measures.get("branch_coverage")).isNull();
    assertThat(measures.get("conditions_to_cover")).isNull();
    assertThat(measures.get("uncovered_conditions")).isNull();
    assertThat(measures.get("coverage")).isEqualTo(0.0);
  }

  private void checkUncoveredFile() {
    Map<String, Double> measures = getCoverageMeasures(FILE_WITHOUT_COVERAGE_KEY);
    assertThat(measures.get("line_coverage")).isEqualTo(0.0);
    assertThat(measures.get("lines_to_cover")).isEqualTo(7.0);
    assertThat(measures.get("uncovered_lines")).isEqualTo(7.0);
    assertThat(measures.get("branch_coverage")).isEqualTo(0.0);
    assertThat(measures.get("conditions_to_cover")).isEqualTo(2.0);
    assertThat(measures.get("uncovered_conditions")).isEqualTo(2.0);
    assertThat(measures.get("coverage")).isEqualTo(0.0);
  }

  private void checkCoveredFile() {
    Map<String, Double> measures = getCoverageMeasures(FILE_KEY);
    assertThat(measures.get("line_coverage")).isEqualTo(90.9);
    assertThat(measures.get("lines_to_cover")).isEqualTo(11.0);
    assertThat(measures.get("uncovered_lines")).isEqualTo(1.0);
    assertThat(measures.get("branch_coverage")).isEqualTo(75.0);
    assertThat(measures.get("conditions_to_cover")).isEqualTo(4.0);
    assertThat(measures.get("uncovered_conditions")).isEqualTo(1.0);
    assertThat(measures.get("coverage")).isEqualTo(86.7);
  }

  private void checkCoveredKotlinFile() {
    Map<String, Double> measures = getCoverageMeasures(KOTLIN_FILE_KEY);
    assertThat(measures.get("line_coverage")).isEqualTo(75);
    assertThat(measures.get("lines_to_cover")).isEqualTo(4.0);
    assertThat(measures.get("uncovered_lines")).isEqualTo(1.0);
    assertThat(measures.get("branch_coverage")).isEqualTo(50.0);
    assertThat(measures.get("conditions_to_cover")).isEqualTo(2.0);
    assertThat(measures.get("uncovered_conditions")).isEqualTo(1.0);
    assertThat(measures.get("coverage")).isEqualTo(66.7);
  }

  private Map<String, Double> getCoverageMeasures(String fileKey) {
    List<String> metricKeys = Arrays.asList("line_coverage", "lines_to_cover",
      "uncovered_lines", "branch_coverage",
      "conditions_to_cover", "uncovered_conditions", "coverage");

    return getWsClient().measures().component(new ComponentWsRequest()
      .setComponent(fileKey)
      .setMetricKeys(metricKeys))
      .getComponent().getMeasuresList()
      .stream()
      .collect(Collectors.toMap(WsMeasures.Measure::getMetric, m -> Double.parseDouble(m.getValue())));
  }

  private WsClient getWsClient() {
    return WsClientFactories.getDefault().newClient(HttpConnector.newBuilder()
      .url(orchestrator.getServer().getUrl())
      .build());
  }

  private File prepareProject(String name) throws IOException {
    Path projectRoot = Paths.get("src/test/resources").resolve(name);
    File targetDir = temp.resolve(name).toFile();
    FileUtils.copyDirectory(projectRoot.toFile(), targetDir);
    return targetDir;
  }
}
