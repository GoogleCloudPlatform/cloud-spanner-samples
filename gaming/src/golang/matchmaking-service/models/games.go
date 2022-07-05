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
	"encoding/json"
	"errors"
	"fmt"
	"math/rand"
	"time"

	spanner "cloud.google.com/go/spanner"
	"github.com/google/uuid"
	iterator "google.golang.org/api/iterator"
)

type Game struct {
	GameUUID string           `json:"gameUUID"`
	Players  []string         `json:"players"`
	Winner   string           `json:"winner"`
	Created  time.Time        `json:"created"`
	Finished spanner.NullTime `json:"finished"`
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

// Provided a game UUID, determine the winner
// Current implementation is a random player from the list of players assigned to the game
func determineWinner(playerUUIDs []string) string {
	if len(playerUUIDs) == 0 {
		return ""
	}

	var winnerUUID string

	rand.Seed(time.Now().UnixNano())
	offset := rand.Intn(len(playerUUIDs))
	winnerUUID = playerUUIDs[offset]
	return winnerUUID
}

// Get players for a game
// We only care about the playerUUID and their stats, as this is intended to be used
// to modify players when a game is closed. We get the current_game to make sure later that the player is part of the game.
func (g Game) getGamePlayers(ctx context.Context, txn *spanner.ReadWriteTransaction) ([]string, []Player, error) {
	stmt := spanner.Statement{
		SQL: `SELECT PlayerUUID, Stats, Current_game FROM players
				INNER JOIN (
				SELECT pUUID FROM games g, UNNEST(g.Players) AS pUUID WHERE gameUUID=@game
				) AS gPlayers ON gPlayers.pUUID = players.PlayerUUID;`,
		Params: map[string]interface{}{
			"game": g.GameUUID,
		},
	}

	iter := txn.Query(ctx, stmt)
	playerRows, err := readRows(iter)
	if err != nil {
		return []string{}, []Player{}, err
	}

	var playerUUIDs []string
	var players []Player
	for _, row := range playerRows {
		var p Player

		if err := row.ToStruct(&p); err != nil {
			return []string{}, []Player{}, err
		}
		if p.Stats.IsNull() {
			// Initialize player stats
			p.Stats = spanner.NullJSON{Value: PlayerStats{
				Games_played: 0,
				Games_won:    0,
			}, Valid: true}
		}

		players = append(players, p)
		playerUUIDs = append(playerUUIDs, p.PlayerUUID)
	}

	return playerUUIDs, players, nil
}

// Retrieve an open game.
func GetOpenGame(ctx context.Context, client spanner.Client) (Game, error) {
	var g Game

	// Get player's new balance (read after write)
	query := fmt.Sprintf("SELECT gameUUID FROM (SELECT gameUUID FROM games WHERE finished IS NULL) TABLESAMPLE RESERVOIR (%d ROWS)", 1)
	stmt := spanner.Statement{SQL: query}

	iter := client.Single().Query(ctx, stmt)
	defer iter.Stop()
	for {
		row, err := iter.Next()
		if err == iterator.Done {
			break
		}
		if err != nil {
			return Game{}, err
		}

		if err := row.ToStruct(&g); err != nil {
			return Game{}, err
		}
	}
	return g, nil
}

// Given a list of players and a winner's UUID, update players of a game
// Updating players involves closing out the game (current_game = NULL) and
// updating their game stats. Specifically, we are incrementing games_played.
// If the player is the determined winner, then their games_won stat is incremented.
func (g Game) updateGamePlayers(ctx context.Context, players []Player, txn *spanner.ReadWriteTransaction) error {
	for _, p := range players {
		// Modify stats
		var pStats PlayerStats
		json.Unmarshal([]byte(p.Stats.String()), &pStats)

		pStats.Games_played = pStats.Games_played + 1

		if p.PlayerUUID == g.Winner {
			pStats.Games_won = pStats.Games_won + 1
		}
		updatedStats, _ := json.Marshal(pStats)
		p.Stats.UnmarshalJSON(updatedStats)

		// Update player
		// If player's current game isn't the same as this game, that's an error
		if p.Current_game != g.GameUUID {
			errorMsg := fmt.Sprintf("Player '%s' doesn't belong to game '%s'.", p.PlayerUUID, g.GameUUID)
			return errors.New(errorMsg)
		}

		cols := []string{"playerUUID", "current_game", "stats"}
		newGame := spanner.NullString{
			StringVal: "",
			Valid:     false,
		}

		txn.BufferWrite([]*spanner.Mutation{
			spanner.Update("players", cols, []interface{}{p.PlayerUUID, newGame, p.Stats}),
		})
	}

	return nil
}

// Create a new game and assign players
// Players that are not currently playing a game are eligble to be selected for the new game
// Current implementation allows for less than numPlayers to be placed in a game
func (g *Game) CreateGame(ctx context.Context, client spanner.Client) error {
	// Initialize game values
	g.GameUUID = generateUUID()

	numPlayers := 100

	// Create and assign
	_, err := client.ReadWriteTransaction(ctx, func(ctx context.Context, txn *spanner.ReadWriteTransaction) error {
		var m []*spanner.Mutation

		// get players
		query := fmt.Sprintf("SELECT playerUUID FROM (SELECT playerUUID FROM players WHERE current_game IS NULL LIMIT 10000) TABLESAMPLE RESERVOIR (%d ROWS)", numPlayers)
		stmt := spanner.Statement{SQL: query}
		iter := txn.Query(ctx, stmt)

		playerRows, err := readRows(iter)
		if err != nil {
			return err
		}

		var playerUUIDs []string

		for _, row := range playerRows {
			var pUUID string
			if err := row.Columns(&pUUID); err != nil {
				return err
			}

			playerUUIDs = append(playerUUIDs, pUUID)
		}

		// Create the game
		gCols := []string{"gameUUID", "players", "created"}
		m = append(m, spanner.Insert("games", gCols, []interface{}{g.GameUUID, playerUUIDs, time.Now()}))

		// Update players to lock into this game

		for _, p := range playerUUIDs {
			pCols := []string{"playerUUID", "current_game"}
			m = append(m, spanner.Update("players", pCols, []interface{}{p, g.GameUUID}))
		}

		txn.BufferWrite(m)

		return nil
	})

	if err != nil {
		return err
	}

	return nil
}

// Closing game. When provided a Game, chose a random winner and close out the game.
// A game is closed by setting the winner and finished time.
// Additionally all players' game stats are updated, and the current_game is set to null to allow
// them to be chosen for a new game.
func (g *Game) CloseGame(ctx context.Context, client spanner.Client) error {
	// Close game
	_, err := client.ReadWriteTransaction(ctx,
		func(ctx context.Context, txn *spanner.ReadWriteTransaction) error {
			// Get game players
			playerUUIDs, players, err := g.getGamePlayers(ctx, txn)

			if err != nil {
				return err
			}

			// Might be an issue if there are no players!
			if len(playerUUIDs) == 0 {
				errorMsg := fmt.Sprintf("No players found for game '%s'", g.GameUUID)
				return errors.New(errorMsg)
			}

			// Get random winner
			g.Winner = determineWinner(playerUUIDs)

			// Validate game finished time is null
			row, err := txn.ReadRow(ctx, "games", spanner.Key{g.GameUUID}, []string{"finished"})
			if err != nil {
				return err
			}

			if err := row.Column(0, &g.Finished); err != nil {
				return err
			}

			// If time is not null, then the game is already marked as finished. That's an error.
			if !g.Finished.IsNull() {
				errorMsg := fmt.Sprintf("Game '%s' is already finished.", g.GameUUID)
				return errors.New(errorMsg)
			}

			cols := []string{"gameUUID", "finished", "winner"}
			txn.BufferWrite([]*spanner.Mutation{
				spanner.Update("games", cols, []interface{}{g.GameUUID, time.Now(), g.Winner}),
			})

			// Update each player to increment stats.games_played (and stats.games_won if winner),
			// and set current_game to null so they can be chosen for a new game
			playerErr := g.updateGamePlayers(ctx, players, txn)
			if playerErr != nil {
				return playerErr
			}

			return nil
		})

	if err != nil {
		return err
	}

	return nil
}
