CREATE TABLE players (
	playerUUID STRING(36) NOT NULL,
	player_name STRING(64) NOT NULL,
	email STRING(MAX) NOT NULL,
	password_hash BYTES(60) NOT NULL,
	created TIMESTAMP,
	updated TIMESTAMP,
	stats JSON,
	account_balance NUMERIC,
	is_logged_in BOOL,
	last_login TIMESTAMP,
	valid_email BOOL,
	current_game STRING(36)
) PRIMARY KEY (playerUUID);

CREATE UNIQUE INDEX PlayerAuthentication ON players(email) STORING(password_hash);
CREATE UNIQUE INDEX PlayerName ON players(player_name);
CREATE INDEX PlayerGame ON players(current_game);
