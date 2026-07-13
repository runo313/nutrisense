#!/usr/bin/env python3
import os
import pandas as pd
from sqlalchemy import create_engine, text
from sqlalchemy.dialects.postgresql import insert
from dotenv import load_dotenv
from sqlalchemy import Table, MetaData
import sys
import argparse

load_dotenv()

DB_HOST = os.getenv('DB_HOST', 'localhost')
DB_PORT = os.getenv('DB_PORT', '5432')
DB_NAME = os.getenv('POSTGRES_DB')
DB_USER = os.getenv('POSTGRES_USER')
DB_PASSWORD = os.getenv('POSTGRES_PASSWORD')

if not DB_PASSWORD:
    sys.stderr.write("Error: DB_PASSWORD not set in .env\n")
    sys.exit(1)

def create_parser()-> argparse.ArgumentParser: 
    parser = argparse.ArgumentParser()
    parser.add_argument("-p", "--file_path", type=str, required=True, help="provide csv file path")
    return parser

def get_engine():
    url = f"postgresql+psycopg2://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"
    return create_engine(url)

def seed_food_portions(final_df: pd.DataFrame) -> None:
    engine = get_engine()
    
    db_columns = ['id','fdc_id','amount','measure_unit_description','gram_weight']

    df_to_insert = final_df[db_columns].copy()

    # convert to list of dicts for SQLAlchemy insert
    records = df_to_insert.where(pd.notna(df_to_insert), None).to_dict(orient='records')

    with engine.begin() as conn:
        stmt = insert(get_food_portions_table(engine)).values(records)
        stmt = stmt.on_conflict_do_nothing(index_elements=['id'])
        result = conn.execute(stmt)

    sys.stdout.write(
        f"Seeding complete. "
        f"{result.rowcount} rows inserted, "
        f"{len(records) - result.rowcount} skipped (already existed).\n"
    )

def get_food_portions_table(engine):
    metadata = MetaData()
    metadata.reflect(bind=engine, only=['food_portions'])
    return metadata.tables['food_portions']


def main():
    parser = create_parser()
    args = parser.parse_args()

    final_df=pd.read_csv(args.file_path)
    seed_food_portions(final_df)

if __name__ == "__main__":
    sys.exit(main())
