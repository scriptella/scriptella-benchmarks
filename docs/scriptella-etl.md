# Scriptella ETL specification — benchmark v3

The benchmark uses explicit settings for repeatability. Start with this shorter
ETL to see the basic flow: read CSV rows, parse the typed columns, and insert
them into a database table.

## Short starter ETL

```xml
<!DOCTYPE etl SYSTEM "http://scriptella.org/dtd/etl.dtd">
<etl>
  <connection id="db" driver="postgresql"
              url="jdbc:postgresql://localhost:5432/example"
              user="example" password="example"/>

  <connection id="csv" driver="csv" url="data.csv">
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
```

This assumes the table already exists and the CSV has a header row named
`id,name,category,amount,created_at`. The CSV driver's defaults provide comma
separation, double-quote quoting, and header handling, so those settings are
omitted here. `encoding` defaults to the JVM's system encoding; specify it when
the file encoding needs to be fixed. The format locale defaults to the JVM
locale, so this example pins `en_US` for decimal-point numbers.

## Reproducible benchmark ETL

The v3 benchmark makes relevant values explicit. `Importer` generates this ETL
document for each Scriptella run. The placeholders are filled with the selected
database driver and URL, credentials, batch size, and fixture file URL.

```xml
<!DOCTYPE etl SYSTEM "http://scriptella.org/dtd/etl.dtd">
<etl>
  <connection id="db" driver="@SCRIPTELLA_DRIVER@" url="@JDBC_URL@"
              user="@DB_USER@" password="@DB_PASSWORD@">
    autocommit=false
    statement.batchSize=@BATCH_SIZE@
  </connection>
  <connection id="csv" driver="csv" url="@FIXTURE_FILE_URL@">
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
```

`@SCRIPTELLA_DRIVER@` is `postgresql` or `mariadb`. These built-in Scriptella
adapters load the corresponding JDBC drivers; the JDBC dependencies are still
required.
`@BATCH_SIZE@` is `0` for unbatched imports and `1000` for batched imports.
`trim=false` overrides the driver's default trimming so it matches the JDBC
path. `encoding`, separator, quote, headers, and locale are pinned for
repeatability. Version 3 removes the benchmark-specific Java formatter classes
and uses the CSV driver's built-in number and timestamp formats for the three
typed columns; the remaining columns pass through as text. This simplifies the
configuration and makes no claim of improved performance. The query inserts
each CSV row into the pre-created table without transformations.

The numeric binding differs from the handwritten JDBC baseline: Scriptella's
built-in parser passes parsed `Number` values, while JDBC explicitly binds
`long` IDs and `BigDecimal` amounts. Both paths are checked against the exact
stored decimal for representative fractional inputs, including 1.23 and
999.99, in PostgreSQL and MariaDB.

The ETL document is assembled and parsed inside the timed Scriptella process.
See [`Importer.java`](../src/main/java/org/scriptella/benchmarks/Importer.java)
for the exact runtime substitutions and invocation.
