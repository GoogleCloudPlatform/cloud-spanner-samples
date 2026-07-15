using System;
using System.Collections.Generic;
using System.Threading.Tasks;
using Google.Cloud.Spanner.Data;
using Google.Cloud.Spanner.Admin.Database.V1;
using Google.Protobuf.WellKnownTypes;

namespace PsvExample
{
    class Program
    {
        static async Task Main(string[] args)
        {
            string projectId = Environment.GetEnvironmentVariable("GOOGLE_CLOUD_PROJECT");
            string instanceId = Environment.GetEnvironmentVariable("SPANNER_INSTANCE_ID");
            if (string.IsNullOrEmpty(projectId) || string.IsNullOrEmpty(instanceId)) {
                Console.WriteLine("Error: GOOGLE_CLOUD_PROJECT and SPANNER_INSTANCE_ID environment variables must be set.");
                return;
            }
            
            string user = Environment.GetEnvironmentVariable("USER") ?? "default";
            string databaseId = $"psv-dotnet-{user}-{Guid.NewGuid().ToString("N").Substring(0, 8)}";
            
            string connectionString = $"Data Source=projects/{projectId}/instances/{instanceId}/databases/{databaseId}";

            var adminClient = new DatabaseAdminClientBuilder().Build();
            Console.WriteLine($"Setting up database {databaseId}...");
            try {
                var createOp = await adminClient.CreateDatabaseAsync(new CreateDatabaseRequest {
                    Parent = $"projects/{projectId}/instances/{instanceId}",
                    CreateStatement = $"CREATE DATABASE \"{databaseId}\"",
                    DatabaseDialect = DatabaseDialect.Postgresql
                });
                await createOp.PollUntilCompletedAsync();

                var ddlOp = await adminClient.UpdateDatabaseDdlAsync(new UpdateDatabaseDdlRequest {
                    Database = $"projects/{projectId}/instances/{instanceId}/databases/{databaseId}",
                    Statements = {
                        "CREATE TABLE userdata (userid bigint NOT NULL PRIMARY KEY, username varchar, secretdata varchar)",
                        "CREATE VIEW myuserdata SQL SECURITY DEFINER AS SELECT userdata.userid, userdata.username, userdata.secretdata FROM userdata WHERE userdata.userid = CAST(spanner.secure_context('user_id') AS bigint)",
                        "CREATE ROLE psv_user",
                        "GRANT SELECT ON myuserdata TO psv_user",
                        "REVOKE SELECT ON userdata FROM psv_user"
                    }
                });
                await ddlOp.PollUntilCompletedAsync();
                Console.WriteLine("Database setup complete.");

                Console.WriteLine("Inserting initial data...");
                using (var connection = new SpannerConnection(connectionString)) {
                    await connection.OpenAsync();
                    var cmd = connection.CreateDmlCommand("INSERT INTO userdata (userid, username, secretdata) VALUES (1, 'Alice', 'Alice secret'), (2, 'Bob', 'Bob secret')");
                    await cmd.ExecuteNonQueryAsync();
                }

                using (var connection = new SpannerConnection(connectionString))
                {
                    await connection.OpenAsync();

                    Console.WriteLine("\nQuerying as user 1...");
                    await QueryWithUserId(connection, "1");

                    Console.WriteLine("\nQuerying as user 2...");
                    await QueryWithUserId(connection, "2");
                }
            } catch (Exception e) {
                Console.WriteLine($"Error during execution: {e.Message}");
            } finally {
                Console.WriteLine($"Cleaning up database {databaseId}...");
                try {
                    await adminClient.DropDatabaseAsync(new DropDatabaseRequest {
                        Database = $"projects/{projectId}/instances/{instanceId}/databases/{databaseId}"
                    });
                    Console.WriteLine("Database cleaned up.");
                } catch (Exception e) {
                    Console.WriteLine($"Failed to cleanup database: {e.Message}");
                }
            }
        }

        // [START spanner_query_with_secure_parameters]
        static async Task QueryWithUserId(SpannerConnection connection, string userId)
        {
            string query = "SELECT * FROM myuserdata";
            using (var command = connection.CreateSelectCommand(query))
            {
                command.ClientContext = new Google.Cloud.Spanner.Data.ClientContext {
                    ClientContextModifier = (v1Context) => {
                        v1Context.SecureContext["user_id"] = Value.ForString(userId);
                    }
                };
                using (var reader = await command.ExecuteReaderAsync())
                {
                    int rowCount = 0;
                    while (await reader.ReadAsync())
                    {
                        Console.WriteLine($"UserID: {reader["userid"]}, UserName: {reader["username"]}, SecretData: {reader["secretdata"]}");
                        rowCount++;
                    }
                    if (rowCount != 1) throw new Exception($"Expected 1 row, got {rowCount}");
                }
            }
        }
        // [END spanner_query_with_secure_parameters]
    }
}
