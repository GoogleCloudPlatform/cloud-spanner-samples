CREATE TABLE games (
  gameUUID STRING(36) NOT NULL,
  players ARRAY<STRING(36)> NOT NULL,
  winner STRING(36),
  created TIMESTAMP,
  finished TIMESTAMP,
) PRIMARY KEY(gameUUID);

CREATE TABLE players (
  playerUUID STRING(36) NOT NULL,
  player_name STRING(64) NOT NULL,
  email STRING(MAX) NOT NULL,
  password_hash BYTES(60) NOT NULL,
  created TIMESTAMP,
  updated TIMESTAMP,
  stats JSON,
  account_balance NUMERIC NOT NULL DEFAULT (0.00),
  is_logged_in BOOL,
  last_login TIMESTAMP,
  valid_email BOOL,
  current_game STRING(36),
  FOREIGN KEY (current_game) REFERENCES games (gameUUID),
) PRIMARY KEY(playerUUID);

CREATE UNIQUE INDEX PlayerAuthentication ON players(email) STORING (password_hash);

CREATE INDEX PlayerGame ON players(current_game);

CREATE UNIQUE INDEX PlayerName ON players(player_name)
