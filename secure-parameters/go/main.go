package main

import (
	"context"
	"fmt"
	"log"
	"os"
	"time"

	"cloud.google.com/go/spanner"
	admin "cloud.google.com/go/spanner/admin/database/apiv1"
	adminpb "cloud.google.com/go/spanner/admin/database/apiv1/databasepb"
	"cloud.google.com/go/spanner/apiv1/spannerpb"
	"google.golang.org/protobuf/types/known/structpb"
)

func main() {
	ctx := context.Background()
	projectID := os.Getenv("GOOGLE_CLOUD_PROJECT")
	instanceID := os.Getenv("SPANNER_INSTANCE_ID")
	if projectID == "" || instanceID == "" {
		log.Fatal("GOOGLE_CLOUD_PROJECT and SPANNER_INSTANCE_ID environment variables must be set.")
	}

	user := os.Getenv("USER")
	if user == "" {
		user = "default"
	}
	databaseID := fmt.Sprintf("psv-go-%s-%d", user, time.Now().Unix())
	dbPath := fmt.Sprintf("projects/%s/instances/%s/databases/%s", projectID, instanceID, databaseID)

	adminClient, err := admin.NewDatabaseAdminClient(ctx)
	if err != nil {
		log.Fatalf("Failed to create admin client: %v", err)
	}
	defer adminClient.Close()

	fmt.Printf("Setting up database %s...\n", databaseID)
	op, err := adminClient.CreateDatabase(ctx, &adminpb.CreateDatabaseRequest{
		Parent:          fmt.Sprintf("projects/%s/instances/%s", projectID, instanceID),
		CreateStatement: "CREATE DATABASE \"" + databaseID + "\"",
		DatabaseDialect: adminpb.DatabaseDialect_POSTGRESQL,
	})
	if err != nil {
		log.Fatalf("Failed to create database: %v", err)
	}
	if _, err := op.Wait(ctx); err != nil {
		log.Fatalf("Failed to wait for database creation: %v", err)
	}

	// Cleanup database on exit.
	defer func() {
		fmt.Printf("\nCleaning up database %s...\n", databaseID)
		err = adminClient.DropDatabase(ctx, &adminpb.DropDatabaseRequest{Database: dbPath})
		if err != nil {
			log.Printf("Failed to drop database: %v", err)
		} else {
			fmt.Println("Database cleaned up.")
		}
	}()

	opDdl, err := adminClient.UpdateDatabaseDdl(ctx, &adminpb.UpdateDatabaseDdlRequest{
		Database: dbPath,
		Statements: []string{
			"CREATE TABLE userdata (userid bigint NOT NULL PRIMARY KEY, username varchar, secretdata varchar)",
			"CREATE VIEW myuserdata SQL SECURITY DEFINER AS SELECT userdata.userid, userdata.username, userdata.secretdata FROM userdata WHERE userdata.userid = CAST(spanner.secure_context('user_id') AS bigint)",
			"CREATE ROLE psv_user",
			"GRANT SELECT ON myuserdata TO psv_user",
			"REVOKE SELECT ON userdata FROM psv_user",
		},
	})
	if err != nil {
		log.Fatalf("Failed to update DDL: %v", err)
	}
	if err := opDdl.Wait(ctx); err != nil {
		log.Fatalf("Failed to wait for DDL update: %v", err)
	}
	fmt.Println("Database setup complete.")

	client, err := spanner.NewClientWithConfig(ctx, dbPath, spanner.ClientConfig{
		SessionPoolConfig: spanner.DefaultSessionPoolConfig,
	})
	if err != nil {
		log.Fatalf("Failed to create client: %v", err)
	}
	defer client.Close()

	fmt.Println("Inserting initial data...")
	_, err = client.ReadWriteTransaction(ctx, func(ctx context.Context, txn *spanner.ReadWriteTransaction) error {
		stmt := spanner.Statement{SQL: "INSERT INTO userdata (userid, username, secretdata) VALUES (1, 'Alice', 'Alice secret'), (2, 'Bob', 'Bob secret')"}
		_, err := txn.Update(ctx, stmt)
		return err
	})
	if err != nil {
		log.Fatalf("Failed to insert data: %v", err)
	}

	for _, userID := range []string{"1", "2"} {
		fmt.Printf("\nQuerying view as user %s...\n", userID)
		stmt := spanner.Statement{SQL: "SELECT * FROM myuserdata"}
		iter := client.Single().QueryWithOptions(ctx, stmt, spanner.QueryOptions{
			ClientContext: &spannerpb.RequestOptions_ClientContext{
				SecureContext: map[string]*structpb.Value{"user_id": structpb.NewStringValue(userID)},
			},
		})

		rowCount := 0
		err := iter.Do(func(r *spanner.Row) error {
			var id int64
			var name, secret string
			if err := r.Columns(&id, &name, &secret); err != nil {
				return err
			}
			if fmt.Sprintf("%d", id) != userID {
				return fmt.Errorf("Unexpected UserID %d for user %s", id, userID)
			}
			fmt.Printf("UserID: %d, UserName: %s, SecretData: %s\n", id, name, secret)
			rowCount++
			return nil
		})
		if err != nil {
			log.Fatal(err)
		}
		if rowCount != 1 {
			log.Fatalf("Expected 1 row, got %d", rowCount)
		}
	}
}
