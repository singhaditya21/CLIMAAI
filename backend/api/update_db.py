import asyncio
import sys
import os

# Add current directory to path
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from app.database import engine
from sqlalchemy import text

async def update_schema():
    print("Connecting to database...")
    try:
        async with engine.begin() as conn:
            print("Checking/Updating schema...")

            # Add reset_token
            # Note: IF NOT EXISTS is supported in Postgres 9.6+
            try:
                await conn.execute(text("ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_token VARCHAR(100)"))
                print("Added/Checked reset_token column")
            except Exception as e:
                print(f"Error adding reset_token: {e}")

            # Add reset_token_expires
            try:
                await conn.execute(text("ALTER TABLE users ADD COLUMN IF NOT EXISTS reset_token_expires TIMESTAMP WITH TIME ZONE"))
                print("Added/Checked reset_token_expires column")
            except Exception as e:
                print(f"Error adding reset_token_expires: {e}")

            # Add verification_token
            try:
                await conn.execute(text("ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_token VARCHAR(100)"))
                print("Added/Checked verification_token column")
            except Exception as e:
                print(f"Error adding verification_token: {e}")

            # Add verification_token_expires
            try:
                await conn.execute(text("ALTER TABLE users ADD COLUMN IF NOT EXISTS verification_token_expires TIMESTAMP WITH TIME ZONE"))
                print("Added/Checked verification_token_expires column")
            except Exception as e:
                print(f"Error adding verification_token_expires: {e}")

        print("Schema update complete")
    except Exception as e:
        print(f"Connection error: {e}")

if __name__ == "__main__":
    try:
        asyncio.run(update_schema())
    except Exception as e:
        print(f"Failed to run migration: {e}")
