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
	"context"
	"log"
	"net/http"

	spanner "cloud.google.com/go/spanner"
	"github.com/gin-gonic/gin"
	"github.com/googlecloudplatform/cloud-spanner-samples/gaming-matchmaking-service/config"
	"github.com/googlecloudplatform/cloud-spanner-samples/gaming-matchmaking-service/models"
)

// Mutator to create spanner context and client, and set them in gin
func setSpannerConnection(c config.Config) gin.HandlerFunc {
	ctx := context.Background()
	client, err := spanner.NewClient(ctx, c.Spanner.DB())

	if err != nil {
		log.Fatal(err)
	}

	return func(c *gin.Context) {
		c.Set("spanner_client", *client)
		c.Set("spanner_context", ctx)
		c.Next()
	}
}

// Helper function to retrieve spanner client and context
func getSpannerConnection(c *gin.Context) (context.Context, spanner.Client) {
	return c.MustGet("spanner_context").(context.Context),
		c.MustGet("spanner_client").(spanner.Client)
}

// Creating a game assigns a list of players not currently playing a game
func createGame(c *gin.Context) {
	var game models.Game

	ctx, client := getSpannerConnection(c)
	err := game.CreateGame(ctx, client)
	if err != nil {
		c.AbortWithError(http.StatusBadRequest, err)
		return
	}

	c.IndentedJSON(http.StatusCreated, game.GameUUID)
}

func closeGame(c *gin.Context) {
	var game models.Game

	if err := c.BindJSON(&game); err != nil {
		c.AbortWithError(http.StatusBadRequest, err)
		return
	}

	ctx, client := getSpannerConnection(c)
	err := game.CloseGame(ctx, client)
	if err != nil {
		c.AbortWithError(http.StatusBadRequest, err)
		return
	}

	c.IndentedJSON(http.StatusOK, game.Winner)
}

func getOpenGame(c *gin.Context) {
	ctx, client := getSpannerConnection(c)
	game, err := models.GetOpenGame(ctx, client)
	if err != nil {
		c.AbortWithError(http.StatusBadRequest, err)
		return
	}

	c.IndentedJSON(http.StatusOK, game)
}

func main() {
	configuration, _ := config.NewConfig()

	router := gin.Default()
	// TODO: Better configuration of trusted proxy
	router.SetTrustedProxies(nil)

	router.Use(setSpannerConnection(configuration))

	router.GET("/games/open", getOpenGame)
	router.POST("/games/create", createGame)
	router.PUT("/games/close", closeGame)

	router.Run(configuration.Server.URL())
}
