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
import java.util.TimeZone;
import org.apache.commons.csv.CSVFormat;
import scriptella.execution.EtlExecutor;

/** Each invocation performs exactly one import and exits after commit and cleanup. */
public final class Importer {
    private Importer() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            throw new IllegalArgumentException("Usage: <postgres|mariadb> <scriptella|jdbc> <0|1000> <fixture>");
        }
        String database = args[0];
        int batchSize = Integer.parseInt(args[2]);
        if ((!database.equals("postgres") && !database.equals("mariadb")) || (batchSize != 0 && batchSize != 1000)) {
            throw new IllegalArgumentException("Unsupported database or batch size");
        }
        switch (args[1]) {
            case "scriptella" -> scriptella(database, batchSize, Path.of(args[3]));
            case "copy" -> copy(database, Path.of(args[3]));
            case "jdbc" -> jdbc(database, batchSize, Path.of(args[3]));
            default -> throw new IllegalArgumentException("Unsupported implementation: " + args[1]);
        }
    }

    static String url(String database) {
        return Benchmark.env(database.equals("postgres") ? "POSTGRES_URL" : "MARIADB_URL");
    }

    static Connection connect(String database) throws Exception {
        // Compose sets both server/session defaults to UTC; measured clients use
        // the same unmodified URLs and the same UTC JVM timezone.
        return DriverManager.getConnection(url(database), Benchmark.env("DB_USER"), Benchmark.env("DB_PASSWORD"));
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
                if (batchSize == 0) {
                    insert.executeUpdate();
                } else {
                    insert.addBatch();
                    if (++pending == batchSize) {
                        insert.executeBatch();
                        insert.clearBatch();
                        pending = 0;
                    }
                }
            }
            if (pending > 0) {
                insert.executeBatch();
            }
            connection.commit();
        }
    }

    private static void scriptella(String database, int batchSize, Path fixture) throws Exception {
        // Driver URL/settings are the same as JDBC; server/session and JVM defaults are UTC.
        String xml = """
                <!DOCTYPE etl SYSTEM "http://scriptella.org/dtd/etl.dtd">
                <etl>
                  <connection id="db" driver="%s" url="%s" user="%s" password="%s">
                    autocommit=false
                    statement.batchSize=%d
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
                batchSize, xml(fixture.toUri().toString()));
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
