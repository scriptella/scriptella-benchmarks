# Coarse memory observations

One-second Linux process VmRSS/VmHWM sampling started during MariaDB Scriptella unbatched. Values are observed whole-process resident high-water marks, not precise peak RAM, Java heap, database RAM, or total container memory. Late sampling can miss a peak near exit. This lightweight diagnostic was active during the remaining cases; PostgreSQL cases had already exited. No active profiling was added.

| Database | Case | Mode | Observed high-water (MiB) |
|---|---|---|---:|
| mariadb | scriptella | unbatched | 148.0 |
| mariadb | scriptella | batch_1000 | 187.9 |
| mariadb | jdbc | unbatched | 136.1 |
| mariadb | jdbc | batch_1000 | 146.1 |
| mariadb | csvsql | chunk_1000 | 894.6 |

Separate earlier PostgreSQL csvsql snapshot: 885.9 MiB resident (not peak), elapsed 8m44s, accumulated CPU 8m30s. Avoid comparing this snapshot with MariaDB high-water values as a matched quantitative ratio.

This is article context from a single exploratory run. Precise RAM benchmarking is not required for this initial version.
