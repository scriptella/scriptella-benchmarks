package org.scriptella.benchmarks;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

/** Fixed public comparison. Preparation and verification stay outside timing. */
final class Experiment {
    private static final int SMOKE_ROWS = 2001; // exercises a final partial batch
    private static final int WARMUP_ROWS = 20_000;
    private static final String[] COLUMNS = {
        "run_id", "timestamp_utc", "environment_id", "environment_description", "architecture", "benchmark_version", "benchmark_revision", "database", "implementation", "mode",
        "timing_boundary", "case_definition", "scriptella_version", "core_artifact_version", "drivers_artifact_version",
        "rows", "duration_ms", "rows_per_second", "fixture_sha256", "java_version", "postgres_image", "mariadb_image",
        "postgres_jdbc_version", "mariadb_jdbc_version", "commons_csv_version", "python_version", "agate_version", "agate_sql_version", "csvkit_version", "sqlalchemy_version",
        "postgres_dbapi_driver", "postgres_dbapi_driver_version", "mariadb_dbapi_driver", "mariadb_dbapi_driver_version",
        "postgres_synchronous_commit", "postgres_fsync", "mariadb_innodb_flush_log_at_trx_commit"
    };

    private record Case(String database, String implementation, int batch) {
        String mode() { if (implementation.equals("csvsql")) return "chunk_1000"; if (implementation.equals("copy")) return "native_reference"; return batch == 0 ? "unbatched" : "batch_1000"; }
        String key() { return database + "-" + implementation + "-" + mode(); }
    }

    private Experiment() { }

    static void run(boolean measured) throws Exception {
        if (measured && Benchmark.env("ENVIRONMENT_ID").equals("local-unspecified")) {
            throw new IllegalArgumentException("Set ENVIRONMENT_ID to a named machine/runtime before recording measurements");
        }
        var prepared = Benchmark.prepare(measured ? "run" : "smoke");
        Path output = prepared.output();
        Properties metadata = prepared.metadata();
        metadata.setProperty("phase", measured ? "phase-3-public-comparison" : "smoke-only");
        metadata.setProperty("status", "running");
        Benchmark.storeMetadata(output, metadata);
        List<Case> cases = new ArrayList<>();
        for (String database : List.of("postgres", "mariadb")) {
            for (String implementation : List.of("scriptella", "jdbc")) {
                for (int batch : new int[] {0, 1000}) {
                    cases.add(new Case(database, implementation, batch));
                }
            }
            cases.add(new Case(database, "csvsql", 1000));
            if (database.equals("postgres")) cases.add(new Case(database, "copy", 0));
        }
        try {
            Path smoke = prefix(prepared.fixture(), "smoke.csv", SMOKE_ROWS);
            Path warmup = prefix(prepared.fixture(), "warmup.csv", WARMUP_ROWS);
            // Every actual case is smoke-checked before any long measured import.
            for (Case test : cases) {
                reset(test.database());
                launch(test, smoke, output.resolve("smoke-" + test.key() + ".log"));
                verify(test.database(), SMOKE_ROWS);
                reset(test.database());
                System.out.println("Smoke passed: " + test.key() + " (" + SMOKE_ROWS + " rows)");
            }
            if (!measured) {
                metadata.setProperty("status", "success");
                Benchmark.storeMetadata(output, metadata);
                System.out.println("All eleven cases passed; smoke data is not measurement data.");
                return;
            }
            Map<Case, Double> durations = new LinkedHashMap<>();
            try (var writer = Files.newBufferedWriter(output.resolve("measurements.csv"), StandardCharsets.UTF_8);
                 var csv = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(COLUMNS).setRecordSeparator("\n").get())) {
                System.out.printf("%-10s %-12s %-12s %12s %14s%n", "Database", "Case", "Mode", "Duration", "Rows/s");
                for (Case test : cases) {
                    reset(test.database());
                    launch(test, warmup, output.resolve("warmup-" + test.key() + ".log"));
                    verify(test.database(), WARMUP_ROWS);
                    reset(test.database());
                    System.out.println("Measuring: " + test.key());
                    double durationMs = launch(test, prepared.fixture(), output.resolve("measured-" + test.key() + ".log"));
                    verify(test.database(), Benchmark.ROWS);
                    writeMeasurement(csv, metadata, test, durationMs);
                    csv.flush(); // preserve each independently verified result
                    durations.put(test, durationMs);
                    System.out.printf(Locale.ROOT, "%-10s %-12s %-12s %10.3f s %14.0f%n", test.database(),
                            test.implementation(), test.mode(), durationMs / 1000, Benchmark.ROWS * 1000.0 / durationMs);
                    reset(test.database());
                }
            }
            printRatios(durations);
            metadata.setProperty("status", "success");
            Benchmark.storeMetadata(output, metadata);
            System.out.println("Public comparison complete. Results: " + output.resolve("measurements.csv"));
        } catch (Exception failure) {
            metadata.setProperty("status", "failed");
            metadata.setProperty("failure", failure.toString());
            Benchmark.storeMetadata(output, metadata);
            throw failure;
        }
    }

    private static Path prefix(Path fixture, String name, int rows) throws Exception {
        Path result = fixture.resolveSibling(name);
        try (var reader = Files.newBufferedReader(fixture, StandardCharsets.UTF_8);
             var writer = Files.newBufferedWriter(result, StandardCharsets.UTF_8)) {
            for (int line = 0; line <= rows; line++) {
                String value = reader.readLine();
                if (value == null) {
                    throw new IllegalStateException("Fixture too short for prefix");
                }
                writer.write(value);
                writer.write('\n');
            }
        }
        return result;
    }

    private static double launch(Case test, Path fixture, Path log) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("java", "-Duser.timezone=UTC", "-cp",
                System.getProperty("java.class.path"), Importer.class.getName(), test.database(), test.implementation(),
                Integer.toString(test.batch()), fixture.toString());
        if (test.implementation().equals("csvsql")) {
            var uri = java.net.URI.create(Importer.url(test.database()).substring(5));
            String dialect = test.database().equals("postgres") ? "postgresql+psycopg2" : "mysql+pymysql";
            String user = java.net.URLEncoder.encode(Benchmark.env("DB_USER"), StandardCharsets.UTF_8);
            String password = java.net.URLEncoder.encode(Benchmark.env("DB_PASSWORD"), StandardCharsets.UTF_8);
            String url = dialect + "://" + user + ":" + password + "@" + uri.getHost() + ":" + uri.getPort() + uri.getPath();
            builder = new ProcessBuilder("csvsql", "--db", url, "--tables", "csv_import", "--no-create", "--insert", "--chunk-size", "1000", fixture.toString());
        }
        builder.redirectErrorStream(true).redirectOutput(log.toFile());
        long start = System.nanoTime();
        Process process = builder.start();
        int exit = process.waitFor();
        double durationMs = (System.nanoTime() - start) / 1_000_000.0;
        if (exit != 0) {
            // Child diagnostics are retained, while failed imports never become CSV result rows.
            System.err.println(Files.readString(log));
            throw new IllegalStateException(test.key() + " exited " + exit + "; diagnostics: " + log);
        }
        return durationMs;
    }

    private static void reset(String database) throws Exception {
        try (var connection = Importer.connect(database); var statement = connection.createStatement()) {
            statement.execute("TRUNCATE TABLE csv_import");
        }
    }

    private static void verify(String database, int count) throws Exception {
        try (var connection = Importer.connect(database)) {
            if (!Benchmark.scalar(connection, "SELECT COUNT(*) FROM csv_import").equals(Integer.toString(count))) {
                throw new IllegalStateException(database + ": imported row count mismatch; expected " + count);
            }
            int[] samples = {1, count / 2, count};
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT id,name,category,amount,created_at FROM csv_import WHERE id IN ("
                         + samples[0] + "," + samples[1] + "," + samples[2] + ") ORDER BY id")) {
                for (int id : samples) {
                    if (!result.next() || result.getLong(1) != id || !result.getString(2).equals("row_" + id)
                            || !result.getString(3).equals("category_" + (id % 10))
                            || result.getBigDecimal(4).compareTo(BigDecimal.valueOf(id % 100_000, 2)) != 0
                            || !result.getTimestamp(5, utc).equals(Timestamp.valueOf(Benchmark.EPOCH.plusSeconds(id)))) {
                        throw new IllegalStateException(database + ": imported value mismatch at row " + id);
                    }
                }
                if (result.next()) {
                    throw new IllegalStateException(database + ": duplicate sampled rows");
                }
            }
        }
    }

    private static void writeMeasurement(CSVPrinter csv, Properties metadata, Case test, double durationMs) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : metadata.stringPropertyNames()) {
            values.put(key, metadata.getProperty(key));
        }
        values.put("timestamp_utc", Instant.now().toString());
        values.put("database", test.database());
        values.put("implementation", test.implementation());
        values.put("mode", test.mode());
        values.put("timing_boundary", "fresh_process_start_to_exit");
        values.put("case_definition", "phase3-v2-fixed-order-warmup20000-csvsql-defaults-chunk1000-copy");
        values.put("core_artifact_version", metadata.getProperty("scriptella-core_version"));
        values.put("drivers_artifact_version", metadata.getProperty("scriptella-drivers_version"));
        values.put("postgres_jdbc_version", metadata.getProperty("postgresql_version"));
        values.put("mariadb_jdbc_version", metadata.getProperty("mariadb-java-client_version"));
        values.put("commons_csv_version", metadata.getProperty("commons-csv_version"));
        values.put("rows", Integer.toString(Benchmark.ROWS));
        values.put("duration_ms", String.format(Locale.ROOT, "%.3f", durationMs));
        values.put("rows_per_second", String.format(Locale.ROOT, "%.3f", Benchmark.ROWS * 1000.0 / durationMs));
        List<String> record = new ArrayList<>();
        for (String column : COLUMNS) {
            record.add(values.getOrDefault(column, "n/a"));
        }
        csv.printRecord(record);
    }

    private static void printRatios(Map<Case, Double> durations) {
        System.out.println("Ratios from this run (above 1 means the first named implementation/mode is faster):");
        for (String database : List.of("postgres", "mariadb")) {
            for (int batch : new int[] {0, 1000}) {
                var scriptella = new Case(database, "scriptella", batch);
                var jdbc = new Case(database, "jdbc", batch);
                System.out.printf(Locale.ROOT, "%s Scriptella vs JDBC (%s): %.3fx (JDBC duration / Scriptella duration)%n",
                        database, scriptella.mode(), durations.get(jdbc) / durations.get(scriptella));
            }
            for (int batch : new int[] {0, 1000}) {
                double scriptella = durations.get(new Case(database, "scriptella", batch));
                System.out.printf(Locale.ROOT, "%s Scriptella vs csvsql (%s): %.3fx (csvsql / Scriptella)%n", database,
                        new Case(database, "scriptella", batch).mode(), durations.get(new Case(database, "csvsql", 1000)) / scriptella);
                if (database.equals("postgres")) System.out.printf(Locale.ROOT, "%s COPY vs Scriptella (%s): %.3fx (Scriptella / COPY)%n", database,
                        new Case(database, "scriptella", batch).mode(), scriptella / durations.get(new Case(database, "copy", 0)));
            }
            for (String implementation : List.of("scriptella", "jdbc")) {
                System.out.printf(Locale.ROOT, "%s %s batching speedup (batch-1000 vs unbatched): %.3fx (unbatched duration / batch duration)%n",
                        database, implementation, durations.get(new Case(database, implementation, 0))
                                / durations.get(new Case(database, implementation, 1000)));
            }
        }
    }
}
