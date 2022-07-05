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
	"fmt"
	"math/big"
	"time"

	"cloud.google.com/go/spanner"
	"google.golang.org/api/iterator"
)

type PlayerItem struct {
	PlayerItemUUID string           `json:"playerItemUUID" binding:"omitempty,uuid4"`
	PlayerUUID     string           `json:"playerUUID" binding:"required,uuid4"`
	ItemUUID       string           `json:"itemUUID" binding:"required,uuid4"`
	Source         string           `json:"source"`
	GameSession    string           `json:"game_session" binding:"omitempty,uuid4"`
	Price          big.Rat          `json:"price"`
	AcquireTime    time.Time        `json:"acquire_time" spanner:"acquire_time"`
	ExpiresTime    spanner.NullTime `json:"expires_time" spanner:"expires_time"`
	Visible        bool             `json:"visible"`
}

// Used for load generation to get a PlayerItem from players that are actively playing in a game.
func GetRandomPlayerItem(ctx context.Context, client spanner.Client) (PlayerItem, error) {
	var pi PlayerItem

	query := fmt.Sprintf("SELECT playerItemUUID, playerUUID, itemUUID, price FROM ("+
		"	SELECT pi.playerItemUUID, pi.playerUUID, itemUUID, price"+
		"	FROM players"+
		"	INNER JOIN player_items pi ON players.playerUUID = pi.playerUUID"+
		"	WHERE current_game IS NOT NULL AND expires_time IS NULL AND visible = true LIMIT 10000"+
		") TABLESAMPLE RESERVOIR (%d ROWS)", 1)
	stmt := spanner.Statement{SQL: query}

	iter := client.Single().Query(ctx, stmt)
	defer iter.Stop()
	for {
		row, err := iter.Next()
		if err == iterator.Done {
			break
		}
		if err != nil {
			return PlayerItem{}, err
		}

		if err := row.ToStruct(&pi); err != nil {
			return PlayerItem{}, err
		}
	}
	return pi, nil
}

// When given a playerUUID and itemUUID, return the player item data.
// Uses mutations, so should not used to read-after-write
func GetPlayerItem(ctx context.Context, txn *spanner.ReadWriteTransaction, playerUUID string, playerItemUUID string) (PlayerItem, error) {
	var pi PlayerItem

	row, err := txn.ReadRow(ctx, "player_items", spanner.Key{playerUUID, playerItemUUID},
		[]string{"playerItemUUID", "playerUUID", "itemUUID", "price", "acquire_time", "expires_time", "visible"})
	if err != nil {
		return PlayerItem{}, err
	}

	err = row.ToStruct(&pi)
	if err != nil {
		return PlayerItem{}, err
	}

	return pi, nil
}

// Move an item to a new player, removes the item entry from the old player
func (pi *PlayerItem) MoveItem(ctx context.Context, txn *spanner.ReadWriteTransaction, toPlayer string) error {
	fmt.Printf("Buyer: %s", toPlayer)
	txn.BufferWrite([]*spanner.Mutation{
		spanner.Insert("player_items", []string{"playerItemUUID", "playerUUID", "itemUUID", "price", "source", "game_session"},
			[]interface{}{pi.PlayerItemUUID, toPlayer, pi.ItemUUID, pi.Price, pi.Source, pi.GameSession}),
		spanner.Delete("player_items", spanner.Key{pi.PlayerUUID, pi.PlayerItemUUID}),
	})

	return nil
}
