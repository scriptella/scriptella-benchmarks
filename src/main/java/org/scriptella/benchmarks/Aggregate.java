package org.scriptella.benchmarks;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.apache.commons.csv.CSVFormat;

/** Small, strict aggregator: incompatible inputs fail instead of being silently pooled. */
final class Aggregate {
    static final List<String> COMPATIBILITY = List.of("benchmark_version", "environment_id", "architecture",
            "fixture_sha256", "rows", "java_version", "postgres_image", "mariadb_image", "postgres_jdbc_version",
            "mariadb_jdbc_version", "commons_csv_version", "python_version", "agate_version", "agate_sql_version", "csvkit_version", "sqlalchemy_version",
            "postgres_dbapi_driver", "postgres_dbapi_driver_version", "mariadb_dbapi_driver", "mariadb_dbapi_driver_version",
            "postgres_synchronous_commit", "postgres_fsync", "mariadb_innodb_flush_log_at_trx_commit", "timing_boundary", "case_definition");
    record Key(String database, String implementation, String mode, String version) { }
    record Sample(String run, double duration, double throughput) { }
    static void run(Path directory) throws Exception {
        List<Path> files;
        try (var paths = Files.walk(directory)) {
            files = paths.filter(p -> p.getFileName().toString().equals("measurements.csv")).sorted().toList();
        }
        if (files.isEmpty()) throw new IllegalArgumentException("No measurements.csv files in " + directory);
        Map<String, String> baseline = null;
        Map<Key, List<Sample>> groups = new LinkedHashMap<>();
        Set<String> duplicates = new HashSet<>();
        for (Path file : files) {
            try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
                 var csv = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get().parse(reader)) {
                for (var row : csv) {
                    Map<String, String> values = row.toMap();
                    for (String column : COMPATIBILITY) {
                        if (!values.containsKey(column) || values.get(column).isBlank())
                            throw new IllegalArgumentException(file + ": missing compatibility field " + column + " (legacy runs need external attribution)");
                        if (baseline != null && !baseline.get(column).equals(values.get(column)))
                            throw new IllegalArgumentException(file + ": incompatible " + column);
                    }
                    if (values.get("benchmark_version").equals("n/a")) throw new IllegalArgumentException("Unversioned run in " + file);
                    if (baseline == null) baseline = values;
                    String implementation = row.get("implementation");
                    String mode = row.get("mode");
                    String database = row.get("database");
                    boolean valid = Set.of("postgres", "mariadb").contains(database) && switch (implementation) {
                        case "scriptella", "jdbc" -> Set.of("unbatched", "batch_1000").contains(mode);
                        case "csvsql" -> mode.equals("chunk_1000");
                        case "copy" -> database.equals("postgres") && mode.equals("native_reference");
                        default -> false;
                    };
                    if (!valid) throw new IllegalArgumentException("Unknown case in " + file);
                    String version = implementation.equals("scriptella") ? row.get("scriptella_version") : "—";
                    if (implementation.equals("scriptella") && (!version.equals(row.get("core_artifact_version"))
                            || !version.equals(row.get("drivers_artifact_version")))) throw new IllegalArgumentException("Mismatched Scriptella artifacts");
                    Key key = new Key(row.get("database"), implementation, row.get("mode"), version);
                    if (!duplicates.add(row.get("run_id") + key)) throw new IllegalArgumentException("Duplicate run/case: " + key);
                    double duration = Double.parseDouble(row.get("duration_ms"));
                    double throughput = Double.parseDouble(row.get("rows_per_second"));
                    if (!Double.isFinite(duration) || duration <= 0 || !Double.isFinite(throughput) || throughput <= 0)
                        throw new IllegalArgumentException("Invalid measurement in " + file);
                    double expectedThroughput = Double.parseDouble(row.get("rows")) * 1000 / duration;
                    if (Math.abs(expectedThroughput - throughput) > Math.max(1, expectedThroughput * 0.00001))
                        throw new IllegalArgumentException("Inconsistent throughput in " + file);
                    groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new Sample(row.get("run_id"), duration, throughput));
                }
            }
        }
        if (baseline == null) throw new IllegalArgumentException("No measured rows");
        StringBuilder report = new StringBuilder("# Exploratory CSV import comparison\n\n");
        report.append("Environment: ").append(escape(baseline.get("environment_id"))).append(" — ")
                .append(escape(baseline.getOrDefault("environment_description", "n/a"))).append("\n\n");
        report.append("Fixture: ").append(baseline.get("rows")).append(" rows; SHA-256 ").append(baseline.get("fixture_sha256")).append("\n\n");
        // Full metadata is also retained as a readable table.
        report.append("| Metadata | Value |\n|---|---|\n");
        for (String column : COMPATIBILITY) report.append("| ").append(column).append(" | ").append(escape(baseline.get(column))).append(" |\n");
        report.append("\n| Database | Case | Scriptella release | Median duration (s) | Median rows/s | Samples |\n|---|---|---|---:|---:|---:|\n");
        Map<Key, Double> medians = new LinkedHashMap<>();
        for (var entry : groups.entrySet()) {
            Key key = entry.getKey();
            double duration = median(entry.getValue().stream().mapToDouble(Sample::duration).toArray());
            medians.put(key, duration);
            report.append(String.format(Locale.ROOT, "| %s | %s %s | %s | %.3f | %.0f | %d |%n", key.database,
                    label(key.implementation), key.mode, key.version, duration / 1000,
                    median(entry.getValue().stream().mapToDouble(Sample::throughput).toArray()), entry.getValue().size()));
        }
        report.append("\nRatios of median durations; above 1 means the first named implementation/mode is faster. Missing cases are omitted.\n\n");
        Set<String> releases = new TreeSet<>();
        groups.keySet().stream().filter(k -> k.implementation.equals("scriptella")).forEach(k -> releases.add(k.version));
        for (String db : List.of("postgres", "mariadb")) {
            for (String version : releases) {
                for (String mode : List.of("unbatched", "batch_1000")) {
                    Key scriptella = new Key(db, "scriptella", mode, version);
                    ratio(report, medians, db + " Scriptella " + version + " vs JDBC (" + mode + ")", new Key(db, "jdbc", mode, "—"), scriptella);
                    ratio(report, medians, db + " Scriptella " + version + " vs csvsql (" + mode + ")", new Key(db, "csvsql", "chunk_1000", "—"), scriptella);
                    ratio(report, medians, db + " COPY vs Scriptella " + version + " (" + mode + ")", scriptella, new Key(db, "copy", "native_reference", "—"));
                }
                ratio(report, medians, db + " Scriptella " + version + " batching speedup", new Key(db, "scriptella", "unbatched", version), new Key(db, "scriptella", "batch_1000", version));
            }
            ratio(report, medians, db + " JDBC batching speedup", new Key(db, "jdbc", "unbatched", "—"), new Key(db, "jdbc", "batch_1000", "—"));
        }
        report.append("\n| Run | Database | Case | Release | Duration (s) | Rows/s |\n|---|---|---|---|---:|---:|\n");
        groups.forEach((key, samples) -> samples.forEach(sample -> report.append(String.format(Locale.ROOT,
                "| %s | %s | %s %s | %s | %.3f | %.0f |%n", escape(sample.run), key.database, label(key.implementation), key.mode, key.version, sample.duration / 1000, sample.throughput))));
        report.append("\nDirectional engineering experiment; machine, caches, fixed order and fresh process startup affect results. csvsql chunking differs from JDBC batching. PostgreSQL native COPY is a separate reference. Repeated runs do not make results universal.\n");
        Path output = directory.resolve("report.md");
        Files.writeString(output, report, StandardCharsets.UTF_8);
        System.out.println("Aggregate report: " + output);
    }
    private static String label(String implementation) { return implementation.equals("copy") ? "PostgreSQL native COPY reference" : implementation; }
    private static String escape(String value) { return value.replace("|", "\\|").replace("\n", " ").replace("\r", " "); }
    private static double median(double[] values) { Arrays.sort(values); int n = values.length; return n % 2 == 1 ? values[n / 2] : (values[n / 2 - 1] + values[n / 2]) / 2; }
    private static void ratio(StringBuilder report, Map<Key, Double> medians, String label, Key numerator, Key denominator) {
        if (medians.containsKey(numerator) && medians.containsKey(denominator))
            report.append(String.format(Locale.ROOT, "- %s: %.3f× (%s %s duration / %s %s duration)\n", label, medians.get(numerator) / medians.get(denominator), numerator.implementation, numerator.mode, denominator.implementation, denominator.mode));
    }
}
