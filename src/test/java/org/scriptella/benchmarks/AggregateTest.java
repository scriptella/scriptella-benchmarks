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
    @Test void v5PandasRatiosAndDependencyCompatibility() throws Exception {
        Map<String, String> v5 = Map.of("benchmark_version", "v5", "pandas_version", "2.3.3", "numpy_version", "2.3.4");
        sample("a", "1.6", 1000, v5);
        var pandas = new HashMap<>(v5);
        pandas.put("implementation", "pandas");
        pandas.put("mode", "chunk_1000");
        sample("b", "1.6", 1800, pandas);
        Aggregate.run(directory);
        String report = Files.readString(directory.resolve("report.md"));
        assertTrue(report.contains("Pandas + SQLAlchemy chunk_1000"));
        assertTrue(report.contains("vs pandas (batch_1000): 1.800×"));
        assertFalse(report.contains("vs csvsql"));
        pandas.put("numpy_version", "other");
        sample("c", "1.6", 1800, pandas);
        assertThrows(IllegalArgumentException.class, () -> Aggregate.run(directory));
    }

    @Test void v4AndV5CannotBePooled() throws Exception {
        sample("a", "1.6", 1000, Map.of("benchmark_version", "v4"));
        sample("b", "1.6", 1000, Map.of("benchmark_version", "v5", "pandas_version", "2.3.3", "numpy_version", "2.3.4"));
        assertThrows(IllegalArgumentException.class, () -> Aggregate.run(directory));
    }

    @Test void differentCaseSelectionsCannotBePooled() throws Exception {
        sample("a", "1.6", 1000, Map.of("case_definition", "default-seven"));
        sample("b", "1.6", 1000, Map.of("case_definition", "opt-in-eleven"));
        assertThrows(IllegalArgumentException.class, () -> Aggregate.run(directory));
    }

    private Map<String, String> finalMetadata() {
        var values = new HashMap<String, String>();
        values.put("benchmark_version", "v5");
        values.put("pandas_version", "2.3.3");
        values.put("numpy_version", "2.3.4");
        values.put("case_definition", Experiment.CASE_DEFINITION);
        Experiment.LOAD_METADATA.forEach(k -> values.put(k, "same"));
        return values;
    }

    @Test void finalEightReportsNativeLoadAndRejectsChangedSettings() throws Exception {
        var values = finalMetadata();
        values.put("database", "mariadb");
        sample("a", "1.6", 1000, values);
        values.put("implementation", "load_data");
        values.put("mode", "native_reference");
        sample("b", "1.6", 250, values);
        Aggregate.run(directory);
        String report = Files.readString(directory.resolve("report.md"));
        assertTrue(report.contains("MariaDB native LOAD DATA LOCAL INFILE reference"));
        assertTrue(report.contains("LOAD DATA vs Scriptella 1.6 (batch_1000): 4.000×"));
        assertTrue(report.contains("| Database | Scriptella | JDBC | Pandas | Native load |"));
        values.put("mariadb_local_infile", "0");
        sample("c", "1.6", 250, values);
        assertThrows(IllegalArgumentException.class, () -> Aggregate.run(directory));
    }

    @Test void reportEscapesControlsWithoutChangingRawMetadata() throws Exception {
        var values = finalMetadata();
        String sql = "LOAD DATA LOCAL INFILE 'fixture.csv'\nLINES TERMINATED BY '\n'";
        values.put("mariadb_load_sql", sql);
        values.put("environment_description", "line1\r\nline2\ttab|literal\\n");
        sample("a", "1.6", 1000, values);
        Path raw = directory.resolve("a/measurements.csv");
        byte[] original = Files.readAllBytes(raw);
        Aggregate.run(directory);
        String report = Files.readString(directory.resolve("report.md"));
        assertTrue(report.contains("LINES TERMINATED BY '\\n'"));
        assertTrue(report.contains("line1\\r\\nline2\\ttab\\|literal\\\\n"));
        assertFalse(report.contains("LINES TERMINATED BY ' '"));
        assertArrayEquals(original, Files.readAllBytes(raw));
        try (var reader = Files.newBufferedReader(raw);
             var csv = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
            assertEquals(sql, csv.iterator().next().get("mariadb_load_sql"));
        }
    }

    @Test void finalEightCannotPoolEarlierV5AndRejectsUnbatched() throws Exception {
        sample("a", "1.6", 1000, finalMetadata());
        var earlier = finalMetadata();
        earlier.put("case_definition", "v5-earlier-seven");
        sample("b", "1.6", 1000, earlier);
        assertThrows(IllegalArgumentException.class, () -> Aggregate.run(directory));
        earlier.put("case_definition", Experiment.CASE_DEFINITION);
        earlier.put("mode", "unbatched");
        sample("b", "1.6", 1000, earlier);
        assertThrows(IllegalArgumentException.class, () -> Aggregate.run(directory));
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
