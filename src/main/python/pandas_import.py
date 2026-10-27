"""Ordinary chunked Pandas import; the caller owns schema and verification."""
import sys
from decimal import Decimal

import pandas as pd
from sqlalchemy import BigInteger, DateTime, Numeric, String, create_engine, text


def main(url, fixture):
    engine = create_engine(url)
    types = {"id": BigInteger(), "name": String(32), "category": String(16),
             "amount": Numeric(12, 2), "created_at": DateTime()}
    try:
        with engine.begin() as connection:
            connection.execute(text("SET TIME ZONE 'UTC'" if engine.dialect.name == "postgresql"
                                    else "SET time_zone = '+00:00'"))
            with pd.read_csv(fixture, chunksize=10_000,
                             dtype={"id": "int64", "name": "string", "category": "string",
                                    "amount": "string", "created_at": "string"},
                             keep_default_na=False) as chunks:
                for frame in chunks:
                    frame["amount"] = frame["amount"].map(Decimal)
                    frame["created_at"] = pd.to_datetime(
                        frame["created_at"], format="%Y-%m-%d %H:%M:%S", errors="raise")
                    frame.to_sql("csv_import", connection, if_exists="append", index=False,
                                 chunksize=1000, dtype=types)
        # engine.begin commits only after every chunk succeeds.
    finally:
        engine.dispose()


if __name__ == "__main__":
    main(*sys.argv[1:])
