package org.scriptella.benchmarks;

import java.nio.file.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AggregateTest {
    @TempDir Path directory;
    private void sample(String run, String release, double duration, Map<String, String> overrides) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        Aggregate.COMPATIBILITY.forEach(c -> values.put(c, "same"));
        values.putAll(Map.of("rows", "1000000", "run_id", run, "database", "postgres", "implementation", "scriptella",
                "mode", "batch_1000", "scriptella_version", release, "core_artifact_version", release, "drivers_artifact_version", release));
        values.put("duration_ms", Double.toString(duration));
        values.put("rows_per_second", Double.toString(1_000_000_000 / duration));
        values.putAll(overrides);
        Path file = directory.resolve(run).resolve("measurements.csv");
        Files.createDirectories(file.getParent());
        try (var writer = Files.newBufferedWriter(file); var csv = new CSVPrinter(writer,
                CSVFormat.DEFAULT.builder().setHeader(values.keySet().toArray(String[]::new)).get())) {
            csv.printRecord(values.values());
        }
    }
    @Test void mediansAndReleasesAndTraceability() throws Exception {
        sample("a", "1.6", 1000, Map.of("benchmark_revision", "dirty"));
        sample("b", "1.6", 3000, Map.of("benchmark_revision", "n/a"));
        sample("c", "1.5", 5000, Map.of());
        Aggregate.run(directory);
        String report = Files.readString(directory.resolve("report.md"));
        assertTrue(report.contains("| 1.6 | 2.000 | 666667 | 2 |"));
        assertTrue(report.contains("| 1.5 | 5.000 | 200000 | 1 |"));
        assertTrue(report.contains("| a | postgres"));
        assertTrue(report.contains("| b | postgres"));
    }
    @Test void ratiosUseMedianDurationsAndOmitMissingCases() throws Exception {
        sample("a", "1.6", 1000, Map.of());
        sample("b", "1.6", 3000, Map.of());
        sample("c", "1.6", 6000, Map.of("implementation", "jdbc"));
        Aggregate.run(directory);
        String report = Files.readString(directory.resolve("report.md"));
        assertTrue(report.contains("Scriptella 1.6 vs JDBC (batch_1000): 3.000×"));
        assertFalse(report.contains("vs csvsql (batch_1000):"));
    }
    @Test void incompatibleEnvironmentFails() throws Exception {
        sample("a", "1.6", 1000, Map.of());
        sample("b", "1.6", 1000, Map.of("environment_id", "other"));
        assertThrows(IllegalArgumentException.class, () -> Aggregate.run(directory));
        assertFalse(Files.exists(directory.resolve("report.md")));
    }
    @Test void differentBenchmarkVersionsCannotBePooled() throws Exception {
        sample("v3-run", "1.6", 1000, Map.of("benchmark_version", "v3"));
        sample("v4-run", "1.6", 1000, Map.of("benchmark_version", "v4"));
        assertThrows(IllegalArgumentException.class, () -> Aggregate.run(directory));
        assertFalse(Files.exists(directory.resolve("report.md")));
    }
    @Test void legacyVersionFails() throws Exception {
        sample("a", "1.6", 1000, Map.of("benchmark_version", ""));
        assertThrows(IllegalArgumentException.class, () -> Aggregate.run(directory));
    }
    @Test void invalidDurationFails() throws Exception {
        sample("a", "1.6", Double.NaN, Map.of());
        assertThrows(IllegalArgumentException.class, () -> Aggregate.run(directory));
    }
    @Test void duplicateCaseFails() throws Exception {
        sample("a", "1.6", 1000, Map.of());
        sample("b", "1.6", 1000, Map.of("run_id", "a"));
        assertThrows(IllegalArgumentException.class, () -> Aggregate.run(directory));
    }
    @Test void unknownCaseFails() throws Exception {
        sample("a", "1.6", 1000, Map.of("implementation", "copy", "mode", "batch_1000"));
        assertThrows(IllegalArgumentException.class, () -> Aggregate.run(directory));
    }
}
