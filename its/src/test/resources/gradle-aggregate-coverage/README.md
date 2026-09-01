# Gradle aggregate coverage reproducer

This multi-module project generates one JaCoCo XML report with the repository's Gradle wrapper and imports it through
`sonar.coverage.jacoco.aggregateXmlReportPaths`.

The normal `sonar.coverage.jacoco.xmlReportPaths` property is explicitly cleared so the test exercises only the
project-level aggregate importer. The generated report has no JaCoCo `<group>` elements.

The `alpha` and `beta` modules have unique package/file paths. They reproduce the sonar-jacoco 1.5.1 null-group bug:
the project-level locator failed to resolve even unambiguous entries from a group-less Gradle report, resulting in
near-total coverage loss. The null-group fallback added with JACOCO-175 fixed that case.

The `collision-a` and `collision-b` modules both contain `org/example/shared/Shared.java`, with different classes
inside those files. Gradle merges their coverage into one `sourcefile` entry, so the importer cannot determine
which physical source owns each line. It skips that ambiguous entry and logs a warning instead of assigning
potentially incorrect coverage.
