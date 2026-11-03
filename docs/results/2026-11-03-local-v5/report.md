# Exploratory CSV import comparison

Environment: intel-colima-release-4cpu-6gib — Intel Mac Colima x86_64 4 CPUs 6 GiB

Fixture: 1000000 rows; SHA-256 bd323d366297efd6895e11c0feac212dadc3ea37c9db8ccadc3e96409cb393cc

| Metadata | Value |
|---|---|
| benchmark_version | v5 |
| environment_id | intel-colima-release-4cpu-6gib |
| architecture | amd64 |
| fixture_sha256 | bd323d366297efd6895e11c0feac212dadc3ea37c9db8ccadc3e96409cb393cc |
| rows | 1000000 |
| java_version | 17.0.17+10 |
| postgres_image | postgres:17.11-alpine3.24 |
| mariadb_image | mariadb:11.8.8 |
| postgres_jdbc_version | 42.7.13 |
| mariadb_jdbc_version | 3.5.7 |
| commons_csv_version | 1.14.1 |
| python_version | 3.12.3 |
| sqlalchemy_version | 2.0.46 |
| postgres_dbapi_driver | psycopg2 |
| postgres_dbapi_driver_version | 2.9.11 |
| mariadb_dbapi_driver | PyMySQL |
| mariadb_dbapi_driver_version | 1.1.2 |
| postgres_synchronous_commit | on |
| postgres_fsync | on |
| mariadb_innodb_flush_log_at_trx_commit | 1 |
| timing_boundary | fresh_process_start_to_exit |
| case_definition | v5-final-eight-pg-rewrite-true-warmup20000-pandas-read10000-write1000-local-infile-v1 |
| pandas_version | 2.3.3 |
| numpy_version | 2.3.4 |
| mariadb_local_infile | 1 |
| mariadb_sql_mode | IGNORE_SPACE,STRICT_TRANS_TABLES,ERROR_FOR_DIVISION_BY_ZERO,NO_AUTO_CREATE_USER,NO_ENGINE_SUBSTITUTION |
| mariadb_log_bin | 0 |
| mariadb_binlog_format | MIXED |
| mariadb_sync_binlog | 0 |
| mariadb_load_client_settings | MariaDB Connector/J 3.5.7; allowLocalInfile=true; bound InputStream; autocommit=false; time_zone=+00:00; warnings fatal |
| mariadb_load_sql | LOAD DATA LOCAL INFILE 'benchmark-stream.csv' INTO TABLE csv_import\nCHARACTER SET utf8mb4\nFIELDS TERMINATED BY ',' OPTIONALLY ENCLOSED BY '"' ESCAPED BY ''\nLINES TERMINATED BY '\n'\nIGNORE 1 LINES\n(id,name,category,amount,created_at)\n |
| mariadb_load_transaction_probe | InnoDB: in_transaction=1; rows invisible before commit; rollback empty |

| Database | Case | Scriptella release | Median duration (s) | Median rows/s | Samples |
|---|---|---|---:|---:|---:|
| postgres | scriptella batch_1000 | 1.7.0 | 8.747 | 114354 | 2 |
| postgres | jdbc batch_1000 | — | 7.642 | 130862 | 2 |
| postgres | Pandas + SQLAlchemy chunk_1000 | — | 31.551 | 31695 | 2 |
| postgres | PostgreSQL native COPY reference native_reference | — | 1.750 | 576853 | 2 |
| mariadb | scriptella batch_1000 | 1.7.0 | 7.950 | 125912 | 2 |
| mariadb | jdbc batch_1000 | — | 5.755 | 174063 | 2 |
| mariadb | Pandas + SQLAlchemy chunk_1000 | — | 20.377 | 49087 | 2 |
| mariadb | MariaDB native LOAD DATA LOCAL INFILE reference native_reference | — | 3.287 | 304412 | 2 |

Median seconds; complete individual measurements follow below.

| Database | Scriptella | JDBC | Pandas | Native load |
|---|---:|---:|---:|---:|
| postgres (Scriptella 1.7.0) | 8.747 | 7.642 | 31.551 | 1.750 (COPY) |
| mariadb (Scriptella 1.7.0) | 7.950 | 5.755 | 20.377 | 3.287 (LOAD DATA LOCAL) |

Ratios of median durations; above 1 means the first named implementation/mode is faster. Missing cases are omitted.

- postgres Scriptella 1.7.0 vs JDBC (batch_1000): 0.874× (jdbc batch_1000 duration / scriptella batch_1000 duration)
- postgres Scriptella 1.7.0 vs pandas (batch_1000): 3.607× (pandas chunk_1000 duration / scriptella batch_1000 duration)
- postgres COPY vs Scriptella 1.7.0 (batch_1000): 4.998× (scriptella batch_1000 duration / copy native_reference duration)
- mariadb Scriptella 1.7.0 vs JDBC (batch_1000): 0.724× (jdbc batch_1000 duration / scriptella batch_1000 duration)
- mariadb Scriptella 1.7.0 vs pandas (batch_1000): 2.563× (pandas chunk_1000 duration / scriptella batch_1000 duration)
- mariadb LOAD DATA vs Scriptella 1.7.0 (batch_1000): 2.419× (scriptella batch_1000 duration / load_data native_reference duration)

| Run | Database | Case | Release | Duration (s) | Rows/s |
|---|---|---|---|---:|---:|
| run-20261103T195118Z-2b4c33da-d7ff-4529-a543-ced722a062e2 | postgres | scriptella batch_1000 | 1.7.0 | 8.893 | 112450 |
| run-20261103T195311Z-0306c6ec-8f2e-4e61-8dbe-917eeaa1ba71 | postgres | scriptella batch_1000 | 1.7.0 | 8.601 | 116259 |
| run-20261103T195118Z-2b4c33da-d7ff-4529-a543-ced722a062e2 | postgres | jdbc batch_1000 | — | 7.716 | 129609 |
| run-20261103T195311Z-0306c6ec-8f2e-4e61-8dbe-917eeaa1ba71 | postgres | jdbc batch_1000 | — | 7.569 | 132115 |
| run-20261103T195118Z-2b4c33da-d7ff-4529-a543-ced722a062e2 | postgres | Pandas + SQLAlchemy chunk_1000 | — | 31.578 | 31668 |
| run-20261103T195311Z-0306c6ec-8f2e-4e61-8dbe-917eeaa1ba71 | postgres | Pandas + SQLAlchemy chunk_1000 | — | 31.524 | 31722 |
| run-20261103T195118Z-2b4c33da-d7ff-4529-a543-ced722a062e2 | postgres | PostgreSQL native COPY reference native_reference | — | 1.921 | 520563 |
| run-20261103T195311Z-0306c6ec-8f2e-4e61-8dbe-917eeaa1ba71 | postgres | PostgreSQL native COPY reference native_reference | — | 1.579 | 633144 |
| run-20261103T195118Z-2b4c33da-d7ff-4529-a543-ced722a062e2 | mariadb | scriptella batch_1000 | 1.7.0 | 8.200 | 121953 |
| run-20261103T195311Z-0306c6ec-8f2e-4e61-8dbe-917eeaa1ba71 | mariadb | scriptella batch_1000 | 1.7.0 | 7.700 | 129872 |
| run-20261103T195118Z-2b4c33da-d7ff-4529-a543-ced722a062e2 | mariadb | jdbc batch_1000 | — | 5.998 | 166731 |
| run-20261103T195311Z-0306c6ec-8f2e-4e61-8dbe-917eeaa1ba71 | mariadb | jdbc batch_1000 | — | 5.513 | 181396 |
| run-20261103T195118Z-2b4c33da-d7ff-4529-a543-ced722a062e2 | mariadb | Pandas + SQLAlchemy chunk_1000 | — | 20.076 | 49812 |
| run-20261103T195311Z-0306c6ec-8f2e-4e61-8dbe-917eeaa1ba71 | mariadb | Pandas + SQLAlchemy chunk_1000 | — | 20.678 | 48361 |
| run-20261103T195118Z-2b4c33da-d7ff-4529-a543-ced722a062e2 | mariadb | MariaDB native LOAD DATA LOCAL INFILE reference native_reference | — | 3.204 | 312107 |
| run-20261103T195311Z-0306c6ec-8f2e-4e61-8dbe-917eeaa1ba71 | mariadb | MariaDB native LOAD DATA LOCAL INFILE reference native_reference | — | 3.370 | 296718 |

Directional engineering experiment; machine, caches, fixed order and fresh process startup affect results. Python chunking differs from JDBC batching. Native COPY and LOAD DATA are separate bulk-loading references with different parsing/error semantics. Repeated runs do not make results universal.
