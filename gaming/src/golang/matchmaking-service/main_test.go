//go:build integration

// Copyright 2022 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package main

import (
	"bytes"
	"context"
	"encoding/json"
	"github.com/stretchr/testify/assert"

	"cloud.google.com/go/spanner"
	database "cloud.google.com/go/spanner/admin/database/apiv1"
	instance "cloud.google.com/go/spanner/admin/instance/apiv1"
	"embed"
	"fmt"
	"github.com/googlecloudplatform/cloud-spanner-samples/gaming-matchmaking-service/models"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/wait"
	databasepb "google.golang.org/genproto/googleapis/spanner/admin/database/v1"
	instancepb "google.golang.org/genproto/googleapis/spanner/admin/instance/v1"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"strings"
	"testing"
)

//go:embed test_data/schema.sql
var SCHEMAFILE embed.FS

var TESTNETWORK = "game-sample-test"

// These integration tests run against the Spanner emulator. The emulator
// must be running and accessible prior to integration tests running.

type Emulator struct {
	testcontainers.Container
	Endpoint string
	Project  string
	Instance string
	Database string
}

type Service struct {
	testcontainers.Container
	Endpoint string
}

func teardown(ctx context.Context, emulator *Emulator, service *Service) {
	emulator.Terminate(ctx)
	service.Terminate(ctx)
}

func setupSpannerEmulator(ctx context.Context) (*Emulator, error) {
	req := testcontainers.ContainerRequest{
		Image:        "gcr.io/cloud-spanner-emulator/emulator:latest",
		ExposedPorts: []string{"9010/tcp"},
		Networks: []string{
			TESTNETWORK,
		},
		NetworkAliases: map[string][]string{
			TESTNETWORK: []string{
				"emulator",
			},
		},
		Name:       "emulator",
		WaitingFor: wait.ForLog("gRPC server listening at"),
	}
	spannerEmulator, err := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{
		ContainerRequest: req,
		Started:          true,
	})
	if err != nil {
		return nil, err
	}

	// Retrieve the container IP
	ip, err := spannerEmulator.Host(ctx)
	if err != nil {
		return nil, err
	}

	// Retrieve the container port
	port, err := spannerEmulator.MappedPort(ctx, "9010")
	if err != nil {
		return nil, err
	}

	// OS environment needed for setting up instance and database
	os.Setenv("SPANNER_EMULATOR_HOST", fmt.Sprintf("%s:%d", ip, port.Int()))

	var ec = Emulator{
		Container: spannerEmulator,
		Endpoint:  "emulator:9010",
		Project:   "test-project",
		Instance:  "test-instance",
		Database:  "test-database",
	}

	// Create instance
	err = setupInstance(ctx, ec)
	if err != nil {
		return nil, err
	}

	// Define the database and schema
	err = setupDatabase(ctx, ec)
	if err != nil {
		return nil, err
	}

	// Load test data
	err = loadTestData(ctx, ec)
	if err != nil {
		return nil, err
	}

	return &ec, nil
}

func setupInstance(ctx context.Context, ec Emulator) error {
	instanceAdmin, err := instance.NewInstanceAdminClient(ctx)
	if err != nil {
		log.Fatal(err)
	}

	defer instanceAdmin.Close()

	op, err := instanceAdmin.CreateInstance(ctx, &instancepb.CreateInstanceRequest{
		Parent:     fmt.Sprintf("projects/%s", ec.Project),
		InstanceId: ec.Instance,
		Instance: &instancepb.Instance{
			Config:      fmt.Sprintf("projects/%s/instanceConfigs/%s", ec.Project, "emulator-config"),
			DisplayName: ec.Instance,
			NodeCount:   1,
		},
	})
	if err != nil {
		return fmt.Errorf("could not create instance %s: %v", fmt.Sprintf("projects/%s/instances/%s", ec.Project, ec.Instance), err)
	}
	// Wait for the instance creation to finish.
	i, err := op.Wait(ctx)
	if err != nil {
		return fmt.Errorf("waiting for instance creation to finish failed: %v", err)
	}

	// The instance may not be ready to serve yet.
	if i.State != instancepb.Instance_READY {
		fmt.Printf("instance state is not READY yet. Got state %v\n", i.State)
	}
	fmt.Printf("Created emulator instance [%s]\n", ec.Instance)

	return nil
}

func setupDatabase(ctx context.Context, ec Emulator) error {
	// get schema statements from file
	schema, _ := SCHEMAFILE.ReadFile("test_data/schema.sql")

	// Removing NOT NULL constraints for columns we don't care about in matchmaking tests
	schemaStringFix := strings.Replace(string(schema), "password_hash BYTES(60) NOT NULL,", "password_hash BYTES(60),", 1)
	schemaStringFix = strings.Replace(schemaStringFix, "account_balance NUMERIC NOT NULL DEFAULT (0.00),", "account_balance NUMERIC,", 1)

	schemaStatements := strings.Split(schemaStringFix, ";")

	adminClient, err := database.NewDatabaseAdminClient(ctx)
	if err != nil {
		return err
	}
	defer adminClient.Close()

	op, err := adminClient.CreateDatabase(ctx, &databasepb.CreateDatabaseRequest{
		Parent:          fmt.Sprintf("projects/%s/instances/%s", ec.Project, ec.Instance),
		CreateStatement: "CREATE DATABASE `" + ec.Database + "`",
		ExtraStatements: schemaStatements,
	})
	if err != nil {
		fmt.Printf("Error: [%s]", err)
		return err
	}
	if _, err := op.Wait(ctx); err != nil {
		fmt.Printf("Error: [%s]", err)
		return err
	}

	fmt.Printf("Created emulator database [%s]\n", ec.Database)
	return nil
}

func loadTestData(ctx context.Context, ec Emulator) error {
	dbString := fmt.Sprintf("projects/%s/instances/%s/databases/%s", ec.Project, ec.Instance, ec.Database)
	client, err := spanner.NewClient(ctx, dbString)
	if err != nil {
		return err
	}
	defer client.Close()

	playerColumns := []string{"playerUUID", "player_name", "email", "stats"}

	emptyStatsStruct, _ := json.Marshal(models.PlayerStats{})
	emptyStats := string(emptyStatsStruct)
	m := []*spanner.Mutation{
		spanner.Insert("players", playerColumns, []interface{}{1, "player1", "player1@email.com", emptyStats}),
		spanner.Insert("players", playerColumns, []interface{}{2, "player2", "player2@email.com", emptyStats}),
		spanner.Insert("players", playerColumns, []interface{}{3, "player3", "player3@email.com", emptyStats}),
		spanner.Insert("players", playerColumns, []interface{}{4, "player4", "player4@email.com", emptyStats}),
		spanner.Insert("players", playerColumns, []interface{}{5, "player5", "player5@email.com", emptyStats}),
	}
	_, err = client.Apply(ctx, m)
	if err != nil {
		fmt.Printf("Error adding test data: %s", err.Error())
		return err
	}

	fmt.Println("Successfully loaded test data.")
	return nil
}

func setupService(ctx context.Context, ec *Emulator) (*Service, error) {
	var service = "matchmaking-service"
	req := testcontainers.ContainerRequest{
		Image:        fmt.Sprintf("%s:latest", service),
		Name:         service,
		ExposedPorts: []string{"80:80/tcp"}, // Bind to 80 on localhost to avoid not knowing about the container port
		Networks:     []string{TESTNETWORK},
		NetworkAliases: map[string][]string{
			TESTNETWORK: []string{
				service,
			},
		},
		Env: map[string]string{
			"SPANNER_PROJECT_ID":    ec.Project,
			"SPANNER_INSTANCE_ID":   ec.Instance,
			"SPANNER_DATABASE_ID":   ec.Database,
			"SERVICE_HOST":          "0.0.0.0",
			"SERVICE_PORT":          "80",
			"SPANNER_EMULATOR_HOST": ec.Endpoint,
		},
		WaitingFor: wait.ForLog("Listening and serving HTTP on 0.0.0.0:80"),
	}
	serviceContainer, err := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{
		ContainerRequest: req,
		Started:          true,
	})
	if err != nil {
		return nil, err
	}

	// Retrieve the container endpoint
	endpoint, err := serviceContainer.Endpoint(ctx, "")
	if err != nil {
		return nil, err
	}

	return &Service{
		Container: serviceContainer,
		Endpoint:  endpoint,
	}, nil
}

func TestMain(m *testing.M) {
	ctx := context.Background()

	// Setup the docker network so containers can talk to each other
	nr := testcontainers.NetworkRequest{
		Name:       TESTNETWORK,
		Attachable: true,
	}
	_, err := testcontainers.GenericNetwork(ctx, testcontainers.GenericNetworkRequest{
		NetworkRequest: nr,
	})
	if err != nil {
		fmt.Printf("Error setting up docker test network: %s\n", err)
		os.Exit(1)
	}

	// Setup the emulator container and default instance/database
	spannerEmulator, err := setupSpannerEmulator(ctx)
	if err != nil {
		fmt.Printf("Error setting up emulator: %s\n", err)
		os.Exit(1)
	}

	// Run service
	service, err := setupService(ctx, spannerEmulator)
	if err != nil {
		fmt.Printf("Error setting up service: %s\n", err)
		os.Exit(1)
	}

	defer teardown(ctx, spannerEmulator, service)

	os.Exit(m.Run())
}

func TestCreateGames(t *testing.T) {
	response, err := http.Post("http://localhost/games/create", "application/json", bytes.NewBuffer([]byte{}))
	if err != nil {
		t.Fatal(err.Error())
	}

	assert.Equal(t, 201, response.StatusCode)
}

func TestModifyGames(t *testing.T) {
	// Get an open game
	response, err := http.Get("http://localhost/games/open")
	if err != nil {
		t.Fatal(err.Error())
	}

	body, err := ioutil.ReadAll(response.Body)
	if err != nil {
		t.Fatal(err.Error())
	}

	var gameData models.Game
	json.Unmarshal(body, &gameData)
	assert.NotEmpty(t, gameData.GameUUID)

	// Test close game
	if gameData.GameUUID != "" {
		gameJson, _ := json.Marshal(gameData)

		client := &http.Client{}
		req, err := http.NewRequest(http.MethodPut, "http://localhost/games/close", bytes.NewBuffer(gameJson))
		if err != nil {
			t.Fatal(err.Error())
		}
		// set the request header Content-Type for json
		req.Header.Set("Content-Type", "application/json")
		response, err := client.Do(req)
		if err != nil {
			t.Fatal(err.Error())
		}
		assert.Equal(t, 200, response.StatusCode)
	}
}
