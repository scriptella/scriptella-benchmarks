| Database | Implementation | Rewrite | Round 1 (s) | Round 2 (s) | Round 3 (s) | Median (s) |
|---|---|---|---:|---:|---:|---:|
| postgres | scriptella | false | 17.214 | 15.298 | 15.995 | 15.995 |
| postgres | scriptella | true | 9.409 | 9.262 | 9.482 | 9.409 |
| postgres | jdbc | false | 12.845 | 11.333 | 10.952 | 11.333 |
| postgres | jdbc | true | 7.290 | 7.348 | 7.165 | 7.290 |
| mariadb | scriptella | default | 8.852 | 8.688 | 8.602 | 8.688 |
| mariadb | jdbc | default | 5.571 | 5.694 | 5.526 | 5.571 |

scriptella: rewriting speedup 1.700x; elapsed reduction 41.2%; PG/MariaDB duration ratio 1.841x -> 1.083x; PG minus MariaDB 7.307s -> 0.722s.
jdbc: rewriting speedup 1.555x; elapsed reduction 35.7%; PG/MariaDB duration ratio 2.034x -> 1.309x; PG minus MariaDB 5.762s -> 1.719s.
