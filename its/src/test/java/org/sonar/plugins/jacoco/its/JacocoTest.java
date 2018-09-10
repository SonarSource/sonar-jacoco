package org.sonar.plugins.jacoco.its;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.OrchestratorBuilder;
import com.sonar.orchestrator.build.BuildResult;
import com.sonar.orchestrator.build.SonarScanner;
import com.sonar.orchestrator.locator.FileLocation;
import com.sonar.orchestrator.locator.Location;
import com.sonar.orchestrator.locator.MavenLocation;
import com.sonar.orchestrator.locator.URLLocation;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.sonarqube.ws.WsMeasures;
import org.sonarqube.ws.client.HttpConnector;
import org.sonarqube.ws.client.WsClient;
import org.sonarqube.ws.client.WsClientFactories;
import org.sonarqube.ws.client.measure.ComponentWsRequest;

import static org.assertj.core.api.Assertions.assertThat;

public class JacocoTest {
  private final static String PROJECT_KEY = "jacoco-test-project";
  private static final String FILE_KEY = "jacoco-test-project:src/main/java/org/sonarsource/test/Calc.java";
  private static final String FILE_WITHOUT_COVERAGE_KEY = "jacoco-test-project:src/main/java/org/sonarsource/test/CalcNoCoverage.java";

  @ClassRule
  public static Orchestrator orchestrator;

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  static {
    String defaultRuntimeVersion = "true".equals(System.getenv("SONARSOURCE_QA")) ? null : "7.2-RC1";
    OrchestratorBuilder builder = Orchestrator.builderEnv()
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
      builder.addPlugin(URLLocation.create(new URL("https://sonarsource.bintray.com/Distribution/sonar-java-plugin/sonar-java-plugin-5.6.0.15032.jar")));
    } catch (MalformedURLException e) {
      throw new IllegalStateException("Failed to download plugin", e);
    }
    orchestrator = builder.build();
  }

  @Test
  public void should_import_coverage() throws IOException {
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
  public void should_import_coverage_even_when_java_also_imports() throws IOException {
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
  public void should_give_warning_if_report_doesnt_exist() throws IOException {
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
  public void should_not_import_coverage_if_no_property_given() throws IOException {
    SonarScanner build = SonarScanner.create()
      .setProjectKey(PROJECT_KEY)
      .setDebugLogs(true)
      .setSourceDirs("src/main")
      .setTestDirs("src/test")
      .setProperty("sonar.java.binaries", ".")
      .setProjectDir(prepareProject("simple-project-jacoco"));
    orchestrator.executeBuild(build);
    checkNoJacocoCoverage();
  }

  @Test
  public void should_not_import_coverage_if_report_contains_files_that_cant_be_found() throws IOException {
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
    File targetDir = temp.newFolder(name);
    FileUtils.copyDirectory(projectRoot.toFile(), targetDir);
    return targetDir;
  }
}
