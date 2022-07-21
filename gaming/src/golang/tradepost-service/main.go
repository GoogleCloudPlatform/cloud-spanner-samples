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
	"github.com/googlecloudplatform/cloud-spanner-samples/gaming-tradepost-service/config"
	"github.com/googlecloudplatform/cloud-spanner-samples/gaming-tradepost-service/models"
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

func getPlayerItem(c *gin.Context) {
	ctx, client := getSpannerConnection(c)

	item, err := models.GetRandomPlayerItem(ctx, client)
	if err != nil {
		c.IndentedJSON(http.StatusNotFound, gin.H{"message": "item not found"})
		return
	}

	type RandomItem struct {
		PlayerUUID, PlayerItemUUID, Price string
	}

	ri := RandomItem{PlayerUUID: item.PlayerUUID, PlayerItemUUID: item.PlayerItemUUID, Price: item.Price.FloatString(2)}

	c.IndentedJSON(http.StatusOK, ri)
}

// Get a random open order with a random buyer. Used in trade simulation
func getOpenOrder(c *gin.Context) {
	ctx, client := getSpannerConnection(c)

	// Get an order
	order, err := models.GetRandomOpenOrder(ctx, client)
	if err != nil {
		c.IndentedJSON(http.StatusNotFound, gin.H{"message": "order not found"})
		return
	}

	var buyer models.Player

	// Get a buyer; can't be the same player as the trade order's lister.
	// Only do this if an order exists, otherwise avoid a DB call
	if order.OrderUUID != "" {
		buyer, err = models.GetRandomPlayer(ctx, client, order.Lister, order.ListPrice)
		if err != nil {
			c.IndentedJSON(http.StatusNotFound, gin.H{"message": "order not found"})
			return
		}
	}

	type RandomOrder struct {
		OrderUUID, ListPrice, BuyerUUID, AccountBalance string
	}

	ro := RandomOrder{OrderUUID: order.OrderUUID, BuyerUUID: buyer.PlayerUUID, ListPrice: order.ListPrice.FloatString(2), AccountBalance: buyer.AccountBalance.FloatString(2)}

	c.IndentedJSON(http.StatusOK, ro)
}

// Create a sell order
// TODO: Enable buy orders
func createOrder(c *gin.Context) {
	var order models.TradeOrder

	if err := c.BindJSON(&order); err != nil {
		c.AbortWithError(http.StatusBadRequest, err)
		return
	}

	ctx, client := getSpannerConnection(c)
	err := order.Create(ctx, client)
	if err != nil {
		c.AbortWithError(http.StatusBadRequest, err)
		return
	}

	c.IndentedJSON(http.StatusCreated, order.OrderUUID)
}

func purchaseOrder(c *gin.Context) {
	var order models.TradeOrder

	if err := c.BindJSON(&order); err != nil {
		c.AbortWithError(http.StatusBadRequest, err)
		return
	}

	ctx, client := getSpannerConnection(c)
	err := order.Buy(ctx, client)
	if err != nil {
		c.AbortWithError(http.StatusBadRequest, err)
		return
	}

	c.IndentedJSON(http.StatusCreated, order.OrderUUID)
}

func main() {
	configuration, _ := config.NewConfig()

	router := gin.Default()
	// TODO: Better configuration of trusted proxy
	router.SetTrustedProxies(nil)

	router.Use(setSpannerConnection(configuration))

	router.GET("/trades/player_items", getPlayerItem)
	router.POST("/trades/sell", createOrder)
	router.GET("/trades/open", getOpenOrder)
	router.PUT("/trades/buy", purchaseOrder)

	router.Run(configuration.Server.URL())
}
