package org.scriptella.benchmarks;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import org.apache.commons.csv.CSVFormat;
import scriptella.execution.EtlExecutor;

/** Each invocation performs exactly one import and exits after commit and cleanup. */
public final class Importer {
    private Importer() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: <postgres|mariadb> <scriptella|jdbc|copy|load_data> <1000|0-native> <fixture>");
        }
        String database = args[0];
        int batchSize = Integer.parseInt(args[2]);
        validateCase(database, args[1], batchSize);
        switch (args[1]) {
            case "scriptella" -> scriptella(database, batchSize, Path.of(args[3]));
            case "copy" -> copy(database, Path.of(args[3]));
            case "load_data" -> loadData(Path.of(args[3]));
            case "jdbc" -> jdbc(database, batchSize, Path.of(args[3]));
            default -> throw new IllegalArgumentException("Unsupported implementation: " + args[1]);
        }
    }

    static void validateCase(String database, String implementation, int batchSize) {
        boolean valid = switch (implementation) {
            case "scriptella", "jdbc" -> (database.equals("postgres") || database.equals("mariadb")) && batchSize == 1000;
            case "copy" -> database.equals("postgres") && batchSize == 0;
            case "load_data" -> database.equals("mariadb") && batchSize == 0;
            default -> false;
        };
        if (!valid) throw new IllegalArgumentException("Unsupported database, implementation or batch size");
    }

    // SQL literals use actual LF and double quote characters; escaping is disabled
    // because the deterministic CSV contains no backslash escapes.
    static final String LOAD_DATA_SQL = """
            LOAD DATA LOCAL INFILE 'benchmark-stream.csv' INTO TABLE csv_import
            CHARACTER SET utf8mb4
            FIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"' ESCAPED BY ''
            LINES TERMINATED BY '\n'
            IGNORE 1 LINES
            (id,name,category,amount,created_at)
            """;

    private static Connection loadConnection() throws Exception {
        Properties properties = new Properties();
        properties.setProperty("user", Benchmark.env("DB_USER"));
        properties.setProperty("password", Benchmark.env("DB_PASSWORD"));
        properties.setProperty("allowLocalInfile", "true");
        Connection connection = DriverManager.getConnection(url("mariadb"), properties);
        try (var statement = connection.createStatement()) {
            statement.execute("SET time_zone = '+00:00'");
        }
        connection.setAutoCommit(false);
        return connection;
    }

    static void executeLoadData(Connection connection, Path fixture) throws Exception {
        try (var input = Files.newInputStream(fixture);
             var statement = connection.createStatement()) {
            statement.unwrap(org.mariadb.jdbc.Statement.class).setLocalInfileInputStream(input);
            statement.executeUpdate(LOAD_DATA_SQL);
            if (statement.getWarnings() != null) {
                throw new IllegalStateException("LOAD DATA warning: " + statement.getWarnings());
            }
        }
    }

    private static void loadData(Path fixture) throws Exception {
        try (var connection = loadConnection()) {
            try {
                executeLoadData(connection, fixture);
                connection.commit();
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    static void verifyLoadDataTransaction(Path fixture, int rows) throws Exception {
        try (var connection = loadConnection()) {
            try {
                executeLoadData(connection, fixture);
                if (!Benchmark.scalar(connection, "SELECT @@in_transaction").equals("1")
                        || !Benchmark.scalar(connection, "SELECT COUNT(*) FROM csv_import").equals(Integer.toString(rows))) {
                    throw new IllegalStateException("LOAD DATA did not remain in the expected transaction");
                }
                try (var observer = connect("mariadb")) {
                    if (!Benchmark.scalar(observer, "SELECT COUNT(*) FROM csv_import").equals("0")) {
                        throw new IllegalStateException("LOAD DATA rows visible before commit");
                    }
                }
            } finally {
                connection.rollback();
            }
        }
        try (var observer = connect("mariadb")) {
            if (!Benchmark.scalar(observer, "SELECT COUNT(*) FROM csv_import").equals("0")) {
                throw new IllegalStateException("LOAD DATA rollback did not empty table");
            }
        }
        System.out.println("LOAD DATA transaction probe passed: uncommitted rows invisible; rollback empty");
    }

    static String url(String database) {
        return Benchmark.env(database.equals("postgres") ? "POSTGRES_URL" : "MARIADB_URL");
    }

    static Connection connect(String database) throws Exception {
        Properties properties = database.equals("postgres")
                ? postgresProperties(url(database), Benchmark.env("POSTGRES_REWRITE_BATCHED_INSERTS"))
                : new Properties();
        properties.setProperty("user", Benchmark.env("DB_USER"));
        properties.setProperty("password", Benchmark.env("DB_PASSWORD"));
        return DriverManager.getConnection(url(database), properties);
    }

    static Properties postgresProperties(String url, String rewrite) {
        Properties urlProperties = org.postgresql.Driver.parseURL(url, new Properties());
        if (urlProperties == null || urlProperties.containsKey("reWriteBatchedInserts")) {
            throw new IllegalArgumentException("Use a plain PostgreSQL URL; set POSTGRES_REWRITE_BATCHED_INSERTS separately");
        }
        if (!rewrite.equals("true") && !rewrite.equals("false")) {
            throw new IllegalArgumentException("POSTGRES_REWRITE_BATCHED_INSERTS must be true or false");
        }
        Properties properties = new Properties();
        properties.setProperty("reWriteBatchedInserts", rewrite);
        return properties;
    }

    private static void copy(String database, Path fixture) throws Exception {
        if (!database.equals("postgres")) throw new IllegalArgumentException("COPY is PostgreSQL only");
        try (var connection = connect(database); var input = Files.newInputStream(fixture)) {
            connection.setAutoCommit(false);
            connection.unwrap(org.postgresql.PGConnection.class).getCopyAPI().copyIn(
                    "COPY csv_import (id,name,category,amount,created_at) FROM STDIN WITH (FORMAT CSV, HEADER TRUE, ENCODING 'UTF8')", input);
            connection.commit();
        }
    }

    private static void jdbc(String database, int batchSize, Path fixture) throws Exception {
        CSVFormat format = CSVFormat.RFC4180.builder().setHeader().setSkipHeaderRecord(true)
                .setTrim(false).setIgnoreEmptyLines(false).get();
        try (var connection = connect(database);
             var reader = Files.newBufferedReader(fixture, StandardCharsets.UTF_8);
             var csv = format.parse(reader);
             var insert = connection.prepareStatement(Benchmark.INSERT)) {
            connection.setAutoCommit(false);
            Calendar utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
            int pending = 0;
            for (var row : csv) {
                insert.setLong(1, Long.parseLong(row.get("id")));
                insert.setString(2, row.get("name"));
                insert.setString(3, row.get("category"));
                insert.setBigDecimal(4, new BigDecimal(row.get("amount")));
                insert.setTimestamp(5, Timestamp.valueOf(java.time.LocalDateTime.parse(row.get("created_at"), Benchmark.CSV_TIME)), utc);
                insert.addBatch();
                if (++pending == batchSize) {
                    insert.executeBatch();
                    insert.clearBatch();
                    pending = 0;
                }
            }
            if (pending > 0) {
                insert.executeBatch();
            }
            connection.commit();
        }
    }

    private static void scriptella(String database, int batchSize, Path fixture) throws Exception {
        String rewriteProperty = database.equals("postgres")
                ? "reWriteBatchedInserts=" + postgresProperties(url(database),
                        Benchmark.env("POSTGRES_REWRITE_BATCHED_INSERTS")).getProperty("reWriteBatchedInserts")
                : "";
        String xml = """
                <!DOCTYPE etl SYSTEM "http://scriptella.org/dtd/etl.dtd">
                <etl>
                  <connection id="db" driver="%s" url="%s" user="%s" password="%s">
                    autocommit=false
                    statement.batchSize=%d
                    %s
                  </connection>
                  <connection id="csv" driver="csv" url="%s">
                    encoding=UTF-8
                    separator=,
                    quote="
                    headers=true
                    trim=false
                    format.locale=en_US
                    format.id.type=number
                    format.amount.type=number
                    format.created_at.type=timestamp
                  </connection>
                  <query connection-id="csv">
                    <script connection-id="db">
                      INSERT INTO csv_import (id,name,category,amount,created_at)
                      VALUES (?id, ?name, ?category, ?amount, ?created_at);
                    </script>
                  </query>
                </etl>
                """.formatted(database.equals("postgres") ? "postgresql" : "mariadb",
                xml(url(database)), xml(Benchmark.env("DB_USER")), xml(Benchmark.env("DB_PASSWORD")),
                batchSize, rewriteProperty, xml(fixture.toUri().toString()));
        Path config = Files.createTempFile("scriptella-import-", ".etl.xml");
        try {
            Files.writeString(config, xml, StandardCharsets.UTF_8);
            EtlExecutor.newExecutor(config.toFile()).execute();
        } finally {
            Files.deleteIfExists(config);
        }
    }

    private static String xml(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }

}
