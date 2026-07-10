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

def seed_foods(final_df: pd.DataFrame) -> None:
    engine = get_engine()
    
    db_columns = [
        'fdc_id', 'food_name', 'category', 'source', 'serving_size_g',
        'portion_description', 'energy_kcal', 'protein_g', 'total_fat_g',
        'saturated_fat_g', 'carbohydrates_g', 'dietary_fiber_g', 'sugars_g',
        'sodium_mg', 'potassium_mg', 'calcium_mg', 'iron_mg', 'magnesium_mg',
        'zinc_mg', 'vitamin_c_mg', 'vitamin_d_mcg', 'vitamin_b12_mcg'
    ]

    # reorder/select only the columns the table expects
    df_to_insert = final_df[db_columns].copy()

    # convert to list of dicts for SQLAlchemy insert
    records = df_to_insert.where(pd.notna(df_to_insert), None).to_dict(orient='records')

    with engine.begin() as conn:
        stmt = insert(get_foods_table(engine)).values(records)
        stmt = stmt.on_conflict_do_nothing(index_elements=['fdc_id'])
        result = conn.execute(stmt)

    sys.stdout.write(
        f"Seeding complete. "
        f"{result.rowcount} rows inserted, "
        f"{len(records) - result.rowcount} skipped (already existed).\n"
    )

def get_foods_table(engine):
    metadata = MetaData()
    metadata.reflect(bind=engine, only=['foods'])
    return metadata.tables['foods']


def main():
    parser = create_parser()
    args = parser.parse_args()

    final_df=pd.read_csv(args.file_path)
    seed_foods(final_df)

if __name__ == "__main__":
    sys.exit(main())