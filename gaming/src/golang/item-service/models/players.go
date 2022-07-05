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
	PlayerUUID      string    `json:"playerUUID" binding:"required,uuid4"`
	Updated         time.Time `json:"updated"`
	Account_balance big.Rat   `json:"account_balance"`
	Current_game    string    `json:"current_game"`
}

type PlayerLedger struct {
	PlayerUUID   string  `json:"playerUUID" binding:"required,uuid4"`
	Amount       big.Rat `json:"amount"`
	Game_session string  `json:"game_session"`
	Source       string  `json:"source"`
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
func GetPlayer(ctx context.Context, client spanner.Client) (Player, error) {
	var p Player

	query := fmt.Sprintf("SELECT playerUUID, current_game FROM (SELECT playerUUID, current_game FROM players WHERE current_game IS NOT NULL LIMIT 10000) TABLESAMPLE RESERVOIR (%d ROWS)", 1)
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

func (l *PlayerLedger) UpdateBalance(ctx context.Context, client spanner.Client, p *Player) error {
	// Update balance with new amount
	_, err := client.ReadWriteTransaction(ctx, func(ctx context.Context, txn *spanner.ReadWriteTransaction) error {
		p.PlayerUUID = l.PlayerUUID
		stmt := spanner.Statement{
			SQL: `UPDATE players SET account_balance = (account_balance + @amount) WHERE playerUUID = @playerUUID`,
			Params: map[string]interface{}{
				"amount":     l.Amount,
				"playerUUID": p.PlayerUUID,
			},
		}
		numRows, err := txn.Update(ctx, stmt)

		if err != nil {
			return err
		}

		// No rows modified. That's an error
		if numRows == 0 {
			errorMsg := fmt.Sprintf("Account balance for player '%s' could not be updated", p.PlayerUUID)
			return errors.New(errorMsg)
		}

		// Get player's new balance (read after write)
		stmt = spanner.Statement{
			SQL: `SELECT account_balance, current_game FROM players WHERE playerUUID = @playerUUID`,
			Params: map[string]interface{}{
				"playerUUID": p.PlayerUUID,
			},
		}
		iter := txn.Query(ctx, stmt)
		defer iter.Stop()
		for {
			row, err := iter.Next()
			if err == iterator.Done {
				break
			}
			if err != nil {
				return err
			}
			var accountBalance big.Rat
			var gameSession string
			// TODO: Better error message if current_game is empty
			if err := row.Columns(&accountBalance, &gameSession); err != nil {
				return err
			}
			p.Account_balance = accountBalance
			l.Game_session = gameSession
		}

		stmt = spanner.Statement{
			SQL: `INSERT INTO player_ledger_entries (playerUUID, amount, game_session, source, entryDate)
				VALUES (@playerUUID, @amount, @game, @source, PENDING_COMMIT_TIMESTAMP())`,
			Params: map[string]interface{}{
				"playerUUID": l.PlayerUUID,
				"amount":     l.Amount,
				"game":       l.Game_session,
				"source":     l.Source,
			},
		}
		numRows, err = txn.Update(ctx, stmt)
		if err != nil {
			return err
		}

		return nil
	})

	if err != nil {
		return err
	}

	return nil
}
