import uuid
import os
from google.cloud import spanner

# [START spanner_query_with_secure_parameters]
def query_with_user_id(database, user_id: str):
    """Queries a parameterized view using secure context parameters."""
    with database.snapshot() as snapshot:
        results = snapshot.execute_sql(
            "SELECT * FROM MyUserData",
            request_options={
                "client_context": {
                    "secure_context": {"user_id": user_id}
                }
            },
        )
        for row in results:
            print(f"UserID: {row[0]}, UserName: {row[1]}, SecretData: {row[2]}")
# [END spanner_query_with_secure_parameters]


def main():
    project_id = os.environ.get("GOOGLE_CLOUD_PROJECT")
    instance_id = os.environ.get("SPANNER_INSTANCE_ID")
    if not project_id or not instance_id:
        print("Error: GOOGLE_CLOUD_PROJECT and SPANNER_INSTANCE_ID environment variables must be set.")
        return

    database_id = f"psv-python-{os.environ.get('USER', 'default')}-{uuid.uuid4().hex[:8]}"

    spanner_client = spanner.Client(project=project_id)
    instance = spanner_client.instance(instance_id)
    database = instance.database(database_id, ddl_statements=[
        "CREATE TABLE UserData (UserID INT64 NOT NULL, UserName STRING(MAX), SecretData STRING(MAX)) PRIMARY KEY (UserID)",
        "CREATE VIEW MyUserData SQL SECURITY DEFINER AS SELECT UserData.UserID, UserData.UserName, UserData.SecretData FROM UserData WHERE UserData.UserID = CAST(SECURE_CONTEXT('user_id') AS INT64)",
        "CREATE ROLE psv_user",
        "GRANT SELECT ON VIEW MyUserData TO ROLE psv_user",
        "REVOKE SELECT ON TABLE UserData FROM ROLE psv_user"
    ])

    print(f"Creating database {database_id}...")
    try:
        operation = database.create()
        operation.result()
        print("Database setup complete.")

        print("Inserting initial data...")
        def insert_users(transaction):
            transaction.execute_update(
                "INSERT INTO UserData (UserID, UserName, SecretData) VALUES "
                "(1, 'Alice', 'Alice secret'), (2, 'Bob', 'Bob secret')"
            )
        database.run_in_transaction(insert_users)

        for user_id in ["1", "2"]:
            print(f"\nQuerying as user {user_id}...")
            query_with_user_id(database, user_id)

    except Exception as e:
        print(f"Error during execution: {e}")
    finally:
        print(f"Cleaning up database {database_id}...")
        try:
            database.drop()
            print("Database cleaned up.")
        except Exception as e:
            print(f"Failed to cleanup database: {e}")

if __name__ == "__main__":
    main()
