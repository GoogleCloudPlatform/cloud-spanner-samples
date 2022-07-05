CREATE TABLE trade_orders
(
  orderUUID STRING(36)  NOT NULL,
  lister STRING(36) NOT NULL,
  buyer STRING(36),
  playerItemUUID STRING(36) NOT NULL,
  trade_type STRING(5) NOT NULL,
  list_price NUMERIC NOT NULL,
  created TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP()),
  ended TIMESTAMP,
  expires TIMESTAMP NOT NULL DEFAULT (TIMESTAMP_ADD(CURRENT_TIMESTAMP(), interval 24 HOUR)),
  active BOOL NOT NULL DEFAULT (true),
  cancelled BOOL NOT NULL DEFAULT (false),
  filled BOOL NOT NULL DEFAULT (false),
  expired BOOL NOT NULL DEFAULT (false),
  FOREIGN KEY (playerItemUUID) REFERENCES player_items (playerItemUUID)
) PRIMARY KEY (orderUUID);

CREATE UNIQUE INDEX TradeItem ON trade_orders(playerItemUUID, active);
