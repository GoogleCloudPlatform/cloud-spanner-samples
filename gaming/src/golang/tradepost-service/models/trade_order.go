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
	"errors"
	"fmt"
	"math/big"
	"time"

	"cloud.google.com/go/spanner"
	"github.com/google/uuid"
	"google.golang.org/api/iterator"
)

type TradeOrder struct {
	OrderUUID      string           `json:"orderUUID" binding:"omitempty,uuid4"`
	Lister         string           `json:"lister" binding:"omitempty,uuid4"`
	Buyer          string           `json:"buyer" binding:"omitempty,uuid4"`
	PlayerItemUUID string           `json:"playerItemUUID" binding:"omitempty,uuid4"`
	TradeType      string           `json:"trade_type"`
	ListPrice      big.Rat          `json:"list_price" spanner:"list_price"`
	Created        time.Time        `json:"created"`
	Ended          spanner.NullTime `json:"ended"`
	Expires        time.Time        `json:"expires"`
	Active         bool             `json:"active"`
	Cancelled      bool             `json:"cancelled"`
	Filled         bool             `json:"filled"`
	Expired        bool             `json:"expired"`
}

func generateUUID() string {
	return uuid.NewString()
}

// Helper function to read rows from Spanner.
func readRows(iter *spanner.RowIterator) ([]spanner.Row, error) {
	var rows []spanner.Row
	defer iter.Stop()

	for {
		row, err := iter.Next()
		if err == iterator.Done {
			break
		}

		if err != nil {
			return nil, err
		}

		rows = append(rows, *row)
	}

	return rows, nil
}

// Validate that the order can be placed: Item is visible and not expired
// TODO: Add logging to explain why sell order is invalid
func validateSellOrder(pi PlayerItem) bool {
	// Item is not visible, can't be listed
	if !pi.Visible {
		return false
	}

	// item is expired. can't be listed
	if !pi.ExpiresTime.IsNull() && pi.ExpiresTime.Time.Before(time.Now()) {
		return false
	}

	// All validation passed. Item can be listed
	return true
}

// Validate that the order can be fille: Order is active and not expired
// TODO: Add logging to explain why purchase is invalid
func validatePurchase(o TradeOrder) bool {
	// Order is not active
	if !o.Active {
		return false
	}

	// order is expired. can't be filled
	if !o.Expires.IsZero() && o.Expires.Before(time.Now()) {
		return false
	}

	// All validation passed. Order can be filled
	return true
}

// Validate that a buyer can buy this item.
// TODO: Add logging to explain why buyer is invalid
func validateBuyer(b Player, o TradeOrder) bool {
	// Lister can't be the same as buyer
	if b.PlayerUUID == o.Lister {
		return false
	}

	// Big.rat returns -1 if Account_balance is less than price
	if b.AccountBalance.Cmp(&o.ListPrice) == -1 {
		return false
	}

	return true
}

func (o *TradeOrder) getOrderDetails(ctx context.Context, txn *spanner.ReadWriteTransaction) error {
	row, err := txn.ReadRow(ctx, "trade_orders", spanner.Key{o.OrderUUID}, []string{"lister", "playerItemUUID", "active", "expires", "list_price"})
	if err != nil {
		return err
	}

	err = row.ToStruct(o)
	if err != nil {
		return err
	}

	return nil
}

// Retrieve an order that can be filled
func GetRandomOpenOrder(ctx context.Context, client spanner.Client) (TradeOrder, error) {
	var order TradeOrder

	query := fmt.Sprintf("SELECT orderUUID, lister, list_price FROM (SELECT orderUUID, lister, list_price FROM trade_orders WHERE active = True AND expires > CURRENT_TIMESTAMP()) TABLESAMPLE RESERVOIR (%d ROWS)", 1)
	stmt := spanner.Statement{SQL: query}

	iter := client.Single().Query(ctx, stmt)

	defer iter.Stop()
	for {
		row, err := iter.Next()
		if err == iterator.Done {
			break
		}
		if err != nil {
			return TradeOrder{}, err
		}

		if err := row.ToStruct(&order); err != nil {
			return TradeOrder{}, err
		}
	}

	return order, nil
}

// Create a trade order for an item.
// TODO: allow buy orders
func (o *TradeOrder) Create(ctx context.Context, client spanner.Client) error {
	// insert into spanner
	_, err := client.ReadWriteTransaction(ctx, func(ctx context.Context, txn *spanner.ReadWriteTransaction) error {
		// get the Item to be listed
		pi, err := GetPlayerItem(ctx, txn, o.Lister, o.PlayerItemUUID)
		if err != nil {
			return err
		}

		// Set expires to 1 day by default
		if o.Expires.IsZero() {
			currentTime := time.Now()
			o.Expires = currentTime.Add(time.Hour * 24)
		}

		// Item is not visible or expired, so it can't be listed. That's an error
		if !validateSellOrder(pi) {
			errorMsg := fmt.Sprintf("Item (%s, %s) cannot be listed.", o.Lister, o.PlayerItemUUID)
			return errors.New(errorMsg)
		}

		// Initialize order values
		o.OrderUUID = generateUUID()
		o.Active = true // TODO: Have to set this by default since testing with emulator does not support 'DEFAULT' schema option

		// Insert the order
		var m []*spanner.Mutation
		cols := []string{"orderUUID", "playerItemUUID", "lister", "list_price", "trade_type", "expires", "active"}
		m = append(m, spanner.Insert("trade_orders", cols, []interface{}{o.OrderUUID, o.PlayerItemUUID, o.Lister, o.ListPrice, "sell", o.Expires, o.Active}))

		// Mark the item as invisible
		cols = []string{"playerUUID", "playerItemUUID", "visible"}
		m = append(m, spanner.Update("player_items", cols, []interface{}{o.Lister, o.PlayerItemUUID, false}))

		txn.BufferWrite(m)
		return nil
	})

	if err != nil {
		return err
	}

	// return empty error on success
	return nil
}

// Buy an order
func (o *TradeOrder) Buy(ctx context.Context, client spanner.Client) error {
	// Fulfil the order
	_, err := client.ReadWriteTransaction(ctx, func(ctx context.Context, txn *spanner.ReadWriteTransaction) error {
		// Get Order information
		err := o.getOrderDetails(ctx, txn)
		if err != nil {
			return err
		}

		// Validate order can be filled
		if !validatePurchase(*o) {
			errorMsg := fmt.Sprintf("Order (%s) cannot be filled.", o.OrderUUID)
			return errors.New(errorMsg)
		}

		// Validate buyer has the money
		buyer := Player{PlayerUUID: o.Buyer}
		err = buyer.GetBalance(ctx, txn)
		if err != nil {
			return err
		}

		if !validateBuyer(buyer, *o) {
			errorMsg := fmt.Sprintf("Buyer (%s) cannot purchase order (%s).", buyer.PlayerUUID, o.OrderUUID)
			return errors.New(errorMsg)
		}

		// Move money from buyer to seller (which includes ledger entries)
		var m []*spanner.Mutation
		lister := Player{PlayerUUID: o.Lister}
		err = lister.GetBalance(ctx, txn)
		if err != nil {
			return err
		}

		// Update seller's account balance
		lister.UpdateBalance(ctx, txn, o.ListPrice)

		// Update buyer's account balance
		negAmount := o.ListPrice.Neg(&o.ListPrice)
		buyer.UpdateBalance(ctx, txn, *negAmount)

		// Move item from seller to buyer, mark item as visible.
		pi, err := GetPlayerItem(ctx, txn, o.Lister, o.PlayerItemUUID)
		if err != nil {
			return err
		}
		pi.GameSession = buyer.CurrentGame

		// Moves the item from lister (current pi.PlayerUUID) to buyer
		pi.MoveItem(ctx, txn, o.Buyer)

		// Update order information
		cols := []string{"orderUUID", "active", "filled", "buyer", "ended"}
		m = append(m, spanner.Update("trade_orders", cols, []interface{}{o.OrderUUID, false, true, o.Buyer, time.Now()}))

		txn.BufferWrite(m)
		return nil
	})

	if err != nil {
		return err
	}

	// return empty error on success
	return nil
}

// TODO: handle cancelled items. Mark order as not active, make item visible again

// TODO: handle expired items. Mark order as not active, make item visible again
