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
	"time"

	spanner "cloud.google.com/go/spanner"
	"github.com/gin-gonic/gin"
	"github.com/googlecloudplatform/cloud-spanner-samples/gaming-item-service/config"
	"github.com/googlecloudplatform/cloud-spanner-samples/gaming-item-service/models"
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

func createItem(c *gin.Context) {
	var item models.GameItem

	if err := c.BindJSON(&item); err != nil {
		c.AbortWithError(http.StatusBadRequest, err)
		return
	}

	ctx, client := getSpannerConnection(c)
	err := item.Create(ctx, client)
	if err != nil {
		c.AbortWithError(http.StatusBadRequest, err)
		return
	}

	c.IndentedJSON(http.StatusCreated, item.ItemUUID)
}

// TODO: used by game server to generate load. Should not be called by other entities,
//  so restrictions should be implemented
func getItemUUIDs(c *gin.Context) {
	ctx, client := getSpannerConnection(c)

	items, err := models.GetItemUUIDs(ctx, client)
	if err != nil {
		c.IndentedJSON(http.StatusNotFound, gin.H{"message": "No items exist"})
		return
	}

	c.IndentedJSON(http.StatusOK, items)
}

func getItem(c *gin.Context) {
	var itemUUID = c.Param("id")

	ctx, client := getSpannerConnection(c)

	item, err := models.GetItemByUUID(ctx, client, itemUUID)
	if err != nil {
		c.IndentedJSON(http.StatusNotFound, gin.H{"message": "item not found"})
		return
	}

	type ReturnGameItem struct {
		ItemUUID, Item_name, Item_value string
		Available_time                  time.Time
		duration                        int64
	}

	gi := ReturnGameItem{ItemUUID: item.ItemUUID, Item_name: item.Item_name, Item_value: item.Item_value.FloatString(2),
		Available_time: item.Available_time, duration: item.Duration}

	c.IndentedJSON(http.StatusOK, gi)
}

// Update a player balance with a provided amount. Result is a JSON object that contains PlayerUUID and AccountBalance
func updatePlayerBalance(c *gin.Context) {
	var player models.Player
	var ledger models.PlayerLedger

	if err := c.BindJSON(&ledger); err != nil {
		c.AbortWithError(http.StatusBadRequest, err)
		return
	}

	ctx, client := getSpannerConnection(c)
	err := ledger.UpdateBalance(ctx, client, &player)
	if err != nil {
		c.AbortWithError(http.StatusBadRequest, err)
		return
	}

	type PlayerBalance struct {
		PlayerUUID, AccountBalance string
	}

	balance := PlayerBalance{PlayerUUID: player.PlayerUUID, AccountBalance: player.Account_balance.FloatString(2)}
	c.IndentedJSON(http.StatusOK, balance)
}

func getPlayer(c *gin.Context) {
	ctx, client := getSpannerConnection(c)
	player, err := models.GetPlayer(ctx, client)
	if err != nil {
		c.AbortWithError(http.StatusBadRequest, err)
		return
	}

	c.IndentedJSON(http.StatusOK, player)
}

func addPlayerItem(c *gin.Context) {
	var playerItem models.PlayerItem

	if err := c.BindJSON(&playerItem); err != nil {
		c.AbortWithError(http.StatusBadRequest, err)
		return
	}

	ctx, client := getSpannerConnection(c)
	err := playerItem.Add(ctx, client)
	if err != nil {
		c.AbortWithError(http.StatusBadRequest, err)
		return
	}

	c.IndentedJSON(http.StatusCreated, playerItem)
}

func main() {
	configuration, _ := config.NewConfig()

	router := gin.Default()
	// TODO: Better configuration of trusted proxy
	router.SetTrustedProxies(nil)

	router.Use(setSpannerConnection(configuration))

	router.GET("/items", getItemUUIDs)
	router.POST("/items", createItem)
	router.GET("/items/:id", getItem)
	router.PUT("/players/balance", updatePlayerBalance) // TODO: leverage profile service instead
	router.GET("/players", getPlayer)
	router.POST("/players/items", addPlayerItem)

	router.Run(configuration.Server.URL())
}
