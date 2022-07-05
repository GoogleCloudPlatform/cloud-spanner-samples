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
	"math/big"

	"cloud.google.com/go/spanner"
	database "cloud.google.com/go/spanner/admin/database/apiv1"
	instance "cloud.google.com/go/spanner/admin/instance/apiv1"
	"embed"
	"fmt"
	"github.com/google/uuid"
	"github.com/googlecloudplatform/cloud-spanner-samples/gaming-item-service/models"
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/wait"
	databasepb "google.golang.org/genproto/googleapis/spanner/admin/database/v1"
	instancepb "google.golang.org/genproto/googleapis/spanner/admin/instance/v1"
	"io"
	"io/ioutil"
	"log"
	"net/http"
	"os"
	"strings"
	"testing"
	"time"
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

	// Removing NOT NULL constraints for columns we don't care about in item tests
	schemaStringFix := strings.Replace(string(schema), "password_hash BYTES(60) NOT NULL,", "password_hash BYTES(60),", 1)

	// TODO: Remove this when the Spanner Emulator supports 'DEFAULT' syntax; NOT NULL removed to avoid errors
	// and most of those columns we don't use at the moment
	schemaStringFix = strings.Replace(schemaStringFix, "account_balance NUMERIC NOT NULL DEFAULT (0.00),", "account_balance NUMERIC,", 1)
	schemaStringFix = strings.Replace(schemaStringFix, "acquire_time TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP()),", "acquire_time TIMESTAMP,", 1)
	schemaStringFix = strings.Replace(schemaStringFix, "created TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP()),", "created TIMESTAMP,", 1)
	schemaStringFix = strings.Replace(schemaStringFix, "expires TIMESTAMP NOT NULL DEFAULT (TIMESTAMP_ADD(CURRENT_TIMESTAMP(), interval 24 HOUR)),", "expires TIMESTAMP,", 1)
	schemaStringFix = strings.Replace(schemaStringFix, "visible BOOL NOT NULL DEFAULT(true),", "visible BOOL,", 1)
	schemaStringFix = strings.Replace(schemaStringFix, "active BOOL NOT NULL DEFAULT (true),", "active BOOL,", 1)
	schemaStringFix = strings.Replace(schemaStringFix, "cancelled BOOL NOT NULL DEFAULT (false),", "cancelled BOOL,", 1)
	schemaStringFix = strings.Replace(schemaStringFix, "filled BOOL NOT NULL DEFAULT (false),", "filled BOOL,", 1)
	schemaStringFix = strings.Replace(schemaStringFix, "expired BOOL NOT NULL DEFAULT (false),", "expired BOOL,", 1)

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

	playerColumns := []string{"playerUUID", "player_name", "email", "account_balance", "current_game"}
	gameColumns := []string{"gameUUID", "players", "created"}

	gameUUID := uuid.NewString()
	playerUUID := []string{uuid.NewString(), uuid.NewString(), uuid.NewString()}
	m := []*spanner.Mutation{
		spanner.Insert("games", gameColumns, []interface{}{gameUUID, []string{playerUUID[0], playerUUID[1], playerUUID[2]}, time.Now()}), // Adds 3 players to a game
		spanner.Insert("players", playerColumns, []interface{}{playerUUID[0], "player1", "player1@email.com", "0.00", gameUUID}),
		spanner.Insert("players", playerColumns, []interface{}{playerUUID[1], "player2", "player2@email.com", "0.00", gameUUID}),
		spanner.Insert("players", playerColumns, []interface{}{playerUUID[2], "player3", "player3@email.com", "0.00", gameUUID}),
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
	var service = "item-service"
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

func httpPUT(url string, data io.Reader) (*http.Response, error) {
	client := &http.Client{}
	req, err := http.NewRequest(http.MethodPut, url, data)
	if err != nil {
		return nil, err
	}
	// set the request header Content-Type for json
	req.Header.Set("Content-Type", "application/json")
	response, err := client.Do(req)
	if err != nil {
		return nil, err
	}

	return response, nil
}

func TestCreateGameItems(t *testing.T) {
	response, err := http.Post("http://localhost/items", "application/json", bytes.NewBuffer([]byte("{\"item_name\": \"test item\",\"item_value\": \"3.14\"}")))
	if err != nil {
		t.Fatal(err.Error())
	}

	assert.Equal(t, 201, response.StatusCode)
}

func TestPlayerAcquireItemsAndMoney(t *testing.T) {
	// Test getting a player "/players" endpoint
	response, err := http.Get("http://localhost/players")
	if err != nil {
		t.Fatal(err.Error())
	}
	assert.Equal(t, 200, response.StatusCode)

	body, err := ioutil.ReadAll(response.Body)
	if err != nil {
		t.Fatal(err.Error())
	}

	var pData models.Player
	json.Unmarshal(body, &pData)

	assert.NotEmpty(t, pData.PlayerUUID)
	assert.NotEmpty(t, pData.Current_game)

	// Have the player acquire money, test the '/players/balance' PUT endpoint
	type PlayerBalanceRequest struct {
		PlayerUUID, Source, Amount string
	}

	r := big.NewRat(1, 1)
	_, ok := r.SetString("4.30")
	assert.True(t, ok)

	testPB := PlayerBalanceRequest{PlayerUUID: pData.PlayerUUID, Source: "loot", Amount: r.FloatString(2)}
	pbJSON, _ := json.Marshal(testPB)

	response, err = httpPUT("http://localhost/players/balance", bytes.NewBuffer(pbJSON))
	if err != nil {
		t.Fatal(err.Error())
	}
	assert.Equal(t, 200, response.StatusCode)

	body, err = ioutil.ReadAll(response.Body)
	if err != nil {
		t.Fatal(err.Error())
	}

	type PlayerBalance struct {
		PlayerUUID, AccountBalance string
	}
	var pbData PlayerBalance
	json.Unmarshal(body, &pbData)
	assert.Equal(t, testPB.Amount, pbData.AccountBalance)

	// Get a list of items, test the '/items' endpoint works
	response, err = http.Get("http://localhost/items")
	if err != nil {
		t.Fatal(err.Error())
	}

	assert.Equal(t, 200, response.StatusCode)
	body, err = ioutil.ReadAll(response.Body)
	if err != nil {
		t.Fatal(err.Error())
	}

	var data []string
	json.Unmarshal(body, &data)
	assert.NotEqual(t, 0, len(data))

	// Now for the first item, validate data (assuming the result was not empty)
	if len(data) != 0 {
		giUUID := data[0]
		response, err := http.Get(fmt.Sprintf("http://localhost/items/%s", giUUID))
		if err != nil {
			t.Fatal(err.Error())
		}
		assert.Equal(t, 200, response.StatusCode)

		body, err := ioutil.ReadAll(response.Body)
		if err != nil {
			t.Fatal(err.Error())
		}

		var giData models.GameItem
		json.Unmarshal(body, &giData)

		assert.NotEmpty(t, giData.ItemUUID)
		assert.Equal(t, "3.14", giData.Item_value.FloatString(2))

		// Have the player acquire the item
		type PlayerItemRequest struct {
			PlayerUUID, ItemUUID, Source string
		}
		testPI := PlayerItemRequest{PlayerUUID: pData.PlayerUUID, ItemUUID: giData.ItemUUID, Source: "loot"}
		piJSON, _ := json.Marshal(testPI)

		response, err = http.Post("http://localhost/players/items", "application/json", bytes.NewBuffer(piJSON))
		if err != nil {
			t.Fatal(err.Error())
		}

		assert.Equal(t, 201, response.StatusCode)
	}
}
