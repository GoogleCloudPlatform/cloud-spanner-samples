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

package models

import (
	"context"
	"math/big"
	"time"

	"cloud.google.com/go/spanner"
)

type PlayerItem struct {
	PlayerItemUUID string           `json:"playerItemUUID" binding:"omitempty,uuid4"`
	PlayerUUID     string           `json:"playerUUID" binding:"required,uuid4"`
	ItemUUID       string           `json:"itemUUID" binding:"required,uuid4"`
	Source         string           `json:"source" binding:"required"`
	Game_session   string           `json:"game_session" binding:"omitempty,uuid4"`
	Price          big.Rat          `json:"price"`
	AcquireTime    time.Time        `json:"acquire_time"`
	ExpiresTime    spanner.NullTime `json:"expires_time"`
	Visible        bool             `json:"visible"`
}

// Function adds an item to a player. Stores the item's value as price at the time
// it was acquired. This allows item prices to change over time without impacting
// prices of previously acquired items.
func (pi *PlayerItem) Add(ctx context.Context, client spanner.Client) error {
	// insert into spanner
	_, err := client.ReadWriteTransaction(ctx, func(ctx context.Context, txn *spanner.ReadWriteTransaction) error {
		// Get item price at time of transaction
		price, err := GetItemPrice(ctx, txn, pi.ItemUUID)

		if err != nil {
			return err
		}

		pi.Price = price

		// Get Game session
		session, err := GetPlayerSession(ctx, txn, pi.PlayerUUID)
		if err != nil {
			return err
		}

		pi.Game_session = session

		pi.PlayerItemUUID = generateUUID()

		// Insert
		cols := []string{"playerItemUUID", "playerUUID", "itemUUID", "price", "source", "game_session"}

		txn.BufferWrite([]*spanner.Mutation{
			spanner.Insert("player_items", cols,
				[]interface{}{pi.PlayerItemUUID, pi.PlayerUUID, pi.ItemUUID, pi.Price, pi.Source, pi.Game_session}),
		})

		return nil
	})

	if err != nil {
		return err
	}

	// return empty error on success
	return nil
}
