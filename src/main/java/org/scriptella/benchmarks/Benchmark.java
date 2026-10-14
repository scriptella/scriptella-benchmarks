package org.scriptella.benchmarks;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.HexFormat;
import java.util.Properties;
import java.util.TimeZone;
import java.util.UUID;
import java.util.jar.JarFile;

/** Environment preparation only. No performance measurements are recorded here. */
public final class Benchmark {
    // Increment when implementation or methodology changes affect comparability.
    static final String BENCHMARK_VERSION = "v3";
    static final int ROWS = 1_000_000;
    static final LocalDateTime EPOCH = LocalDateTime.of(2020, 1, 1, 0, 0);
    static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String HEADER = "id,name,category,amount,created_at\n";
    static final String INSERT = "INSERT INTO csv_import (id,name,category,amount,created_at) VALUES (?,?,?,?,?)";

    private Benchmark() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 2 && args[0].equals("aggregate")) {
            Aggregate.run(Path.of(args[1]));
            return;
        }
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: prepare | smoke | run | aggregate <results-directory>");
        }
        switch (args[0]) {
            case "prepare" -> prepare("preparation");
            case "smoke" -> Experiment.run(false);
            case "run" -> Experiment.run(true);
            default -> throw new IllegalArgumentException("Usage: prepare | smoke | run | aggregate <results-directory>");
        }
    }

    record Prepared(Path output, Path fixture, Properties metadata) { }

    static Prepared prepare(String phase) throws Exception {
        String runId = phase + "-" + DateTimeFormatter.ofPattern("uuuuMMdd'T'HHmmss'Z'")
                .withZone(ZoneOffset.UTC).format(Instant.now()) + "-" + UUID.randomUUID();
        Path output = Path.of("/results", runId);
        Files.createDirectories(output);
        Properties metadata = new Properties();
        metadata.setProperty("phase", "preparation-only");
        metadata.setProperty("timestamp_utc", Instant.now().toString());
        metadata.setProperty("environment_id", env("ENVIRONMENT_ID"));
        metadata.setProperty("environment_description", env("ENVIRONMENT_DESCRIPTION"));
        metadata.setProperty("benchmark_version", BENCHMARK_VERSION);
        String revision = System.getenv("BENCHMARK_REVISION");
        metadata.setProperty("benchmark_revision", revision == null || revision.isBlank() ? "n/a" : revision);
        metadata.setProperty("run_id", runId);
        metadata.setProperty("java_version", System.getProperty("java.runtime.version"));
        metadata.setProperty("java_flags", "-Duser.timezone=UTC; JVM-selected heap and collector defaults");
        metadata.setProperty("architecture", System.getProperty("os.arch"));
        metadata.setProperty("scriptella_version", env("SCRIPTELLA_VERSION"));
        metadata.setProperty("postgres_image", "postgres:17.11-alpine3.24");
        metadata.setProperty("mariadb_image", "mariadb:11.8.8");
        metadata.setProperty("jdbc_batch_options", "driver defaults; no URL batch overrides");
        try {
            inspectArtifact(metadata, "scriptella-core", env("SCRIPTELLA_VERSION"));
            inspectArtifact(metadata, "scriptella-drivers", env("SCRIPTELLA_VERSION"));
            inspectArtifact(metadata, "postgresql", "42.7.13");
            inspectArtifact(metadata, "mariadb-java-client", "3.5.7");
            inspectArtifact(metadata, "commons-csv", "1.14.1");
            Process versions = new ProcessBuilder("python", "-c",
                    "from importlib.metadata import version; import platform; print('python_version='+platform.python_version()); print('agate_version='+version('agate')); print('agate_sql_version='+version('agate-sql')); print('csvkit_version='+version('csvkit')); print('sqlalchemy_version='+version('SQLAlchemy')); print('postgres_dbapi_driver=psycopg2'); print('postgres_dbapi_driver_version='+version('psycopg2-binary')); print('mariadb_dbapi_driver=PyMySQL'); print('mariadb_dbapi_driver_version='+version('PyMySQL'))")
                    .redirectError(ProcessBuilder.Redirect.INHERIT).start();
            metadata.load(versions.getInputStream());
            if (versions.waitFor() != 0) throw new IllegalStateException("Python dependency inspection failed");
            metadata.setProperty("csvsql_invocation", "csvsql --db <SQLAlchemy URL> --tables csv_import --no-create --insert --chunk-size 1000 <fixture>; inference and driver defaults");
            metadata.setProperty("csvsql_documentation", "https://csvkit.readthedocs.io/en/2.2.0/scripts/csvsql.html");
            storeMetadata(output, metadata);
            Path fixture = Path.of("/work/fixture.csv");
            Files.createDirectories(fixture.getParent());
            generateFixture(fixture);
            metadata.setProperty("fixture_rows", Integer.toString(ROWS));
            metadata.setProperty("fixture_sha256", sha256(fixture));
            metadata.setProperty("fixture_bytes", Long.toString(Files.size(fixture)));
            storeMetadata(output, metadata);
            System.out.printf("Fixture: %,d rows, %,d bytes, SHA-256 %s%n", ROWS,
                    Files.size(fixture), metadata.getProperty("fixture_sha256"));
            prepareDatabase("postgres", env("POSTGRES_URL"), metadata);
            storeMetadata(output, metadata);
            prepareDatabase("mariadb", env("MARIADB_URL"), metadata);
            metadata.setProperty("status", "success");
            storeMetadata(output, metadata);
            System.out.println("Preparation complete; no performance measurements recorded. Metadata: " + output);
            return new Prepared(output, fixture, metadata);
        } catch (Exception failure) {
            metadata.setProperty("status", "failed");
            metadata.setProperty("failure", failure.toString());
            storeMetadata(output, metadata);
            throw failure;
        }
    }

    static String env(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing environment variable: " + name);
        }
        return value;
    }

    private static void inspectArtifact(Properties metadata, String artifact, String expected) throws Exception {
        Path jar = Path.of("/app/target/dependency", artifact + "-" + expected + ".jar");
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("Missing requested artifact " + artifact + ":" + expected);
        }
        // Validate the version inside the binary rather than trusting its filename.
        try (JarFile archive = new JarFile(jar.toFile())) {
            var entries = archive.entries();
            String actual = null;
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (entry.getName().endsWith("/" + artifact + "/pom.properties")) {
                    Properties pom = new Properties();
                    try (InputStream input = archive.getInputStream(entry)) {
                        pom.load(input);
                    }
                    actual = pom.getProperty("version");
                    break;
                }
            }
            if (actual == null && archive.getManifest() != null) {
                actual = archive.getManifest().getMainAttributes().getValue("Bundle-Version");
            }
            if (!expected.equals(actual)) {
                throw new IllegalStateException("Artifact version mismatch for " + artifact
                        + ": expected " + expected + ", found " + actual);
            }
            metadata.setProperty(artifact + "_version", actual);
        }
        metadata.setProperty(artifact + "_sha256", sha256(jar));
    }

    private static String sha256(Path file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = new DigestInputStream(Files.newInputStream(file), digest)) {
            input.transferTo(java.io.OutputStream.nullOutputStream());
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void generateFixture(Path fixture) throws Exception {
        try (BufferedWriter writer = Files.newBufferedWriter(fixture, StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            for (int id = 1; id <= ROWS; id++) {
                int cents = id % 100_000;
                writer.write(id + ",row_" + id + ",category_" + (id % 10) + ","
                        + (cents / 100) + "." + (cents % 100 < 10 ? "0" : "") + (cents % 100)
                        + "," + EPOCH.plusSeconds(id).format(CSV_TIME) + "\n");
            }
        }
    }

    private static void prepareDatabase(String database, String url, Properties metadata) throws Exception {
        try (Connection connection = DriverManager.getConnection(url, env("DB_USER"), env("DB_PASSWORD"))) {
            try (var statement = connection.createStatement()) {
                statement.execute(database.equals("postgres") ? "SET TIME ZONE 'UTC'" : "SET time_zone = '+00:00'");
                String timestampType = database.equals("postgres") ? "TIMESTAMP WITHOUT TIME ZONE" : "DATETIME";
                statement.execute("CREATE TABLE IF NOT EXISTS csv_import (id BIGINT NOT NULL,"
                        + "name VARCHAR(32) NOT NULL, category VARCHAR(16) NOT NULL,"
                        + "amount DECIMAL(12,2) NOT NULL, created_at " + timestampType + " NOT NULL)"
                        + (database.equals("mariadb") ? " ENGINE=InnoDB" : ""));
                statement.execute("TRUNCATE TABLE csv_import");
                metadata.setProperty(database + "_server_version", connection.getMetaData().getDatabaseProductVersion());
                if (database.equals("postgres")) {
                    metadata.setProperty("postgres_timezone", scalar(connection, "SHOW TimeZone"));
                    metadata.setProperty("postgres_synchronous_commit", scalar(connection, "SHOW synchronous_commit"));
                    metadata.setProperty("postgres_fsync", scalar(connection, "SHOW fsync"));
                } else {
                    metadata.setProperty("mariadb_timezone", scalar(connection, "SELECT @@session.time_zone"));
                    metadata.setProperty("mariadb_innodb_flush_log_at_trx_commit",
                            scalar(connection, "SELECT @@innodb_flush_log_at_trx_commit"));
                }
            }
            connection.setAutoCommit(false);
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
            try (PreparedStatement insert = connection.prepareStatement(INSERT)) {
                for (int id : new int[] {1, ROWS / 2, ROWS}) {
                    insert.setLong(1, id);
                    insert.setString(2, "row_" + id);
                    insert.setString(3, "category_" + (id % 10));
                    insert.setBigDecimal(4, BigDecimal.valueOf(id % 100_000, 2));
                    insert.setTimestamp(5, Timestamp.valueOf(EPOCH.plusSeconds(id)), utc);
                    insert.executeUpdate();
                }
            }
            connection.commit();
            if (!scalar(connection, "SELECT COUNT(*) FROM csv_import").equals("3")) {
                throw new IllegalStateException(database + ": probe row count mismatch");
            }
            try (var statement = connection.createStatement();
                 ResultSet rows = statement.executeQuery("SELECT id,name,category,amount,created_at FROM csv_import ORDER BY id")) {
                for (int id : new int[] {1, ROWS / 2, ROWS}) {
                    if (!rows.next() || rows.getLong(1) != id || !rows.getString(2).equals("row_" + id)
                            || !rows.getString(3).equals("category_" + (id % 10))
                            || rows.getBigDecimal(4).compareTo(BigDecimal.valueOf(id % 100_000, 2)) != 0
                            || !rows.getTimestamp(5, utc).toLocalDateTime().equals(EPOCH.plusSeconds(id))) {
                        throw new IllegalStateException(database + ": probe value mismatch at row " + id);
                    }
                }
            }
            connection.commit();
            connection.setAutoCommit(true);
            try (var statement = connection.createStatement()) {
                statement.execute("TRUNCATE TABLE csv_import");
            }
            if (!scalar(connection, "SELECT COUNT(*) FROM csv_import").equals("0")) {
                throw new IllegalStateException(database + ": table reset failed");
            }
            metadata.setProperty(database + "_probe", "3 typed rows verified; table reset and empty");
            System.out.println(database + ": connected, UTC session, schema ready, typed probes verified, table empty");
        }
    }

    static String scalar(Connection connection, String sql) throws Exception {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            if (!result.next()) {
                throw new IllegalStateException("No result for " + sql);
            }
            return result.getString(1);
        }
    }

    static void storeMetadata(Path output, Properties metadata) throws Exception {
        try (var writer = Files.newBufferedWriter(output.resolve("preparation.properties"), StandardCharsets.UTF_8)) {
            metadata.store(writer, "Environment and execution status; timings are in measurements.csv when present");
        }
    }
}
