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

type GameItem struct {
	ItemUUID      string    `json:"itemUUID"`
	ItemName      string    `json:"item_name"`
	ItemValue     big.Rat   `json:"item_value"`
	AvailableTime time.Time `json:"available_time"`
	Duration      int64     `json:"duration"`
}

// Retrieve an item price
func GetItemPrice(ctx context.Context, txn *spanner.ReadWriteTransaction, itemUUID string) (big.Rat, error) {
	var price big.Rat

	row, err := txn.ReadRow(ctx, "game_items", spanner.Key{itemUUID}, []string{"item_value"})
	if err != nil {
		return price, err
	}

	err = row.Columns(&price)
	if err != nil {
		return price, err
	}

	return price, nil
}

func GetItemByUUID(ctx context.Context, client spanner.Client, itemUUID string) (GameItem, error) {
	row, err := client.Single().ReadRow(ctx, "game_items",
		spanner.Key{itemUUID}, []string{"item_name", "item_value", "available_time", "duration"})
	if err != nil {
		return GameItem{}, err
	}

	item := GameItem{}
	err = row.ToStruct(&item)

	if err != nil {
		return GameItem{}, err
	}
	return item, nil
}
