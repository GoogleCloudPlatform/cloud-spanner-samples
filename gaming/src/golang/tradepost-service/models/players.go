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
	"google.golang.org/api/iterator"
)

type Player struct {
	PlayerUUID     string    `json:"playerUUID" binding:"required,uuid4"`
	Updated        time.Time `json:"updated"`
	AccountBalance big.Rat   `json:"account_balance" spanner:"account_balance"`
	CurrentGame    string    `json:"current_game" binding:"omitempty,uuid4" spanner:"current_game"`
}

type PlayerLedger struct {
	PlayerUUID  string  `json:"playerUUID" binding:"required,uuid4"`
	Amount      big.Rat `json:"amount"`
	GameSession string  `json:"game_session" spanner:"game_session"`
	Source      string  `json:"source"`
}

// Get a player's game session
func GetPlayerSession(ctx context.Context, txn *spanner.ReadWriteTransaction, playerUUID string) (string, error) {
	var session string

	row, err := txn.ReadRow(ctx, "players", spanner.Key{playerUUID}, []string{"current_game"})
	if err != nil {
		return "", err
	}

	err = row.Columns(&session)
	if err != nil {
		return "", err
	}

	// Session is empty. That's an error
	if session == "" {
		errorMsg := fmt.Sprintf("Player '%s' isn't in a game currently.", playerUUID)
		return "", errors.New(errorMsg)
	}

	return session, nil
}

// Retrieve a player of an open game. We only care about the Current_game and playerUUID attributes.
func GetRandomPlayer(ctx context.Context, client spanner.Client, excludePlayerUUID string, minBalance big.Rat) (Player, error) {
	var p Player

	query := fmt.Sprintf("SELECT playerUUID, current_game, account_balance "+
		" FROM (SELECT playerUUID, current_game, account_balance FROM players WHERE current_game IS NOT NULL AND playerUUID!='%s' AND account_balance > %s LIMIT 10000)"+
		" TABLESAMPLE RESERVOIR (%d ROWS)", excludePlayerUUID, minBalance.FloatString(2), 1)
	stmt := spanner.Statement{SQL: query}

	iter := client.Single().Query(ctx, stmt)
	defer iter.Stop()
	for {
		row, err := iter.Next()
		if err == iterator.Done {
			break
		}
		if err != nil {
			return Player{}, err
		}

		if err := row.ToStruct(&p); err != nil {
			return Player{}, err
		}
	}
	return p, nil
}

func (p *Player) GetBalance(ctx context.Context, txn *spanner.ReadWriteTransaction) error {
	row, err := txn.ReadRow(ctx, "players", spanner.Key{p.PlayerUUID}, []string{"account_balance", "current_game"})
	if err != nil {
		return err
	}

	err = row.Columns(&p.AccountBalance, &p.CurrentGame)
	if err != nil {
		return err
	}

	return nil
}

// Update a player's balance, and add an entry into the player ledger
func (p *Player) UpdateBalance(ctx context.Context, txn *spanner.ReadWriteTransaction, newAmount big.Rat) error {
	// This modifies player's AccountBalance, which is used to update the player entry
	p.AccountBalance.Add(&p.AccountBalance, &newAmount)

	txn.BufferWrite([]*spanner.Mutation{
		spanner.Update("players", []string{"playerUUID", "account_balance"}, []interface{}{p.PlayerUUID, p.AccountBalance}),
		spanner.Insert("player_ledger_entries", []string{"playerUUID", "amount", "game_session", "source", "entryDate"},
			[]interface{}{p.PlayerUUID, newAmount, p.CurrentGame, "tradepost", spanner.CommitTimestamp}),
	})
	return nil
}
