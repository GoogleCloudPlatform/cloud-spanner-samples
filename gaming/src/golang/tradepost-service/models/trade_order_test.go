//go:build !integration

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
	"math/big"
	"testing"
	"time"

	"cloud.google.com/go/spanner"
	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
)

func TestGenerateUUID(t *testing.T) {
	id := generateUUID()
	_, err := uuid.Parse(id)

	assert.Nil(t, err)
}

func TestValidSellOrder(t *testing.T) {
	tests := []PlayerItem{
		{Visible: true},
		{Visible: true, ExpiresTime: spanner.NullTime{Time: time.Now().Add(time.Hour * 2), Valid: true}},
	}

	for _, test := range tests {
		res := validateSellOrder(test)
		assert.True(t, res)
	}

}

func TestInvalidSellOrder(t *testing.T) {
	tests := []PlayerItem{
		{Visible: false},
		{Visible: true, ExpiresTime: spanner.NullTime{Time: time.Now().Add(-time.Hour * 2), Valid: true}},
	}

	for _, test := range tests {
		res := validateSellOrder(test)
		assert.False(t, res)
	}
}

func TestValidPurchase(t *testing.T) {
	tests := []TradeOrder{
		{Active: true},
		{Active: true, Expires: time.Now().Add(time.Hour * 2)},
	}

	for _, test := range tests {
		res := validatePurchase(test)
		assert.True(t, res)
	}
}

func TestInvalidPurchase(t *testing.T) {
	tests := []TradeOrder{
		{Active: false},
		{Active: true, Expires: time.Now().Add(-time.Hour * 2)},
	}

	for _, test := range tests {
		res := validatePurchase(test)
		assert.False(t, res)
	}
}

func TestValidBuyers(t *testing.T) {
	type TestBuyer struct {
		Buyer Player
		Order TradeOrder
	}

	buyerBalance := big.NewRat(1, 1)
	buyerBalance.SetString("50.32")

	listPrice := big.NewRat(1, 1)
	listPrice.SetString("3.14")
	tests := []TestBuyer{
		{Buyer: Player{PlayerUUID: "00020b51-29e4-47c3-a3d9-57ceb144dd19", AccountBalance: *buyerBalance},
			Order: TradeOrder{Lister: "00035e6a-3af1-4d4f-8fc5-64666afca868", ListPrice: *listPrice}},
	}

	for _, test := range tests {
		res := validateBuyer(test.Buyer, test.Order)
		assert.True(t, res)
	}
}

func TestInvalidBuyers(t *testing.T) {
	type TestBuyer struct {
		Buyer Player
		Order TradeOrder
	}

	buyerBalance := big.NewRat(1, 1)
	buyerBalance.SetString("50.32")

	listPrice := big.NewRat(1, 1)
	listPrice.SetString("83.14")
	tests := []TestBuyer{
		{Buyer: Player{PlayerUUID: "00020b51-29e4-47c3-a3d9-57ceb144dd19"},
			Order: TradeOrder{Lister: "00020b51-29e4-47c3-a3d9-57ceb144dd19"}},
		{Buyer: Player{PlayerUUID: "00020b51-29e4-47c3-a3d9-57ceb144dd19", AccountBalance: *buyerBalance},
			Order: TradeOrder{Lister: "00035e6a-3af1-4d4f-8fc5-64666afca868", ListPrice: *listPrice}},
	}

	for _, test := range tests {
		res := validateBuyer(test.Buyer, test.Order)
		assert.False(t, res)
	}
}
