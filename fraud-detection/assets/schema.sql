-- Copyright 2026 Google LLC
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- Cloud Spanner Graph Schema for Financial Fraud Detection (Database: fraud)
-- Instance: finance-workshop | Graph: FraudGraph

CREATE TABLE Person (
  person_id INT64 NOT NULL,
  full_name STRING(MAX),
  email STRING(MAX),
  phone STRING(MAX),
  address STRING(MAX),
  country STRING(MAX),
  kyc_tier STRING(MAX),
  risk_score FLOAT64,
  is_suspect BOOL,
  create_time TIMESTAMP,
) PRIMARY KEY(person_id);

CREATE INDEX PersonByEmail ON Person(email);
CREATE INDEX PersonByPhone ON Person(phone);

CREATE TABLE Account (
  account_id INT64 NOT NULL,
  account_type STRING(MAX),
  status STRING(MAX),
  balance FLOAT64,
  country STRING(MAX),
  is_fraud INT64,
  fraud_pattern STRING(MAX),
  create_time TIMESTAMP,
  embedding ARRAY<FLOAT64>(vector_length=>128),
) PRIMARY KEY(account_id);

CREATE INDEX AccountByFraudPattern ON Account(fraud_pattern) STORING (status, balance, is_fraud);

CREATE TABLE Device (
  device_id INT64 NOT NULL,
  device_type STRING(MAX),
  os_version STRING(MAX),
  fingerprint_hash STRING(MAX),
  ip_subnet STRING(MAX),
  is_vpn_or_proxy BOOL,
  risk_score FLOAT64,
  create_time TIMESTAMP,
) PRIMARY KEY(device_id);

CREATE INDEX DeviceByFingerprint ON Device(fingerprint_hash);

CREATE TABLE Owns (
  person_id INT64 NOT NULL,
  account_id INT64 NOT NULL,
  role STRING(MAX),
  create_time TIMESTAMP,
  CONSTRAINT FK_Owns_Account FOREIGN KEY (account_id) REFERENCES Account (account_id),
) PRIMARY KEY(person_id, account_id),
  INTERLEAVE IN PARENT Person ON DELETE CASCADE;

CREATE INDEX OwnsByAccount ON Owns(account_id) STORING (role, create_time);

CREATE TABLE UsesDevice (
  account_id INT64 NOT NULL,
  device_id INT64 NOT NULL,
  login_count INT64,
  ip_address STRING(MAX),
  last_used_time TIMESTAMP,
  CONSTRAINT FK_UsesDevice_Device FOREIGN KEY (device_id) REFERENCES Device (device_id),
) PRIMARY KEY(account_id, device_id),
  INTERLEAVE IN PARENT Account ON DELETE CASCADE;

CREATE INDEX UsesDeviceByDevice ON UsesDevice(device_id) STORING (login_count, ip_address, last_used_time);

CREATE TABLE Transfer (
  account_id INT64 NOT NULL,
  to_account_id INT64 NOT NULL,
  create_time TIMESTAMP NOT NULL,
  transfer_id STRING(64) NOT NULL,
  amount FLOAT64 NOT NULL,
  currency STRING(16),
  transfer_type STRING(64),
  step_num INT64,
  is_flagged_tx BOOL,
  fraud_pattern STRING(MAX),
  CONSTRAINT FK_Transfer_ToAccount FOREIGN KEY (to_account_id) REFERENCES Account (account_id),
) PRIMARY KEY(account_id, to_account_id, create_time, transfer_id),
  INTERLEAVE IN PARENT Account ON DELETE CASCADE;

CREATE INDEX TransferByToAccount ON Transfer(to_account_id, create_time) STORING (amount, currency, transfer_type, step_num, is_flagged_tx, fraud_pattern);

CREATE PROPERTY GRAPH FraudGraph
  NODE TABLES (
    Account
      KEY (account_id)
      LABEL Account PROPERTIES (
        account_id,
        account_type,
        status,
        balance,
        country,
        is_fraud,
        fraud_pattern,
        create_time,
        embedding
      ),
    Person
      KEY (person_id)
      LABEL Person PROPERTIES (
        person_id,
        full_name,
        email,
        phone,
        address,
        country,
        kyc_tier,
        risk_score,
        is_suspect,
        create_time
      ),
    Device
      KEY (device_id)
      LABEL Device PROPERTIES (
        device_id,
        device_type,
        os_version,
        fingerprint_hash,
        ip_subnet,
        is_vpn_or_proxy,
        risk_score,
        create_time
      )
  )
  EDGE TABLES (
    Transfer
      KEY (account_id, to_account_id, create_time, transfer_id)
      SOURCE KEY (account_id) REFERENCES Account (account_id)
      DESTINATION KEY (to_account_id) REFERENCES Account (account_id)
      LABEL Transfer PROPERTIES (
        transfer_id,
        account_id,
        to_account_id,
        amount,
        currency,
        transfer_type,
        create_time,
        step_num,
        is_flagged_tx,
        fraud_pattern
      ),
    Owns
      KEY (person_id, account_id)
      SOURCE KEY (person_id) REFERENCES Person (person_id)
      DESTINATION KEY (account_id) REFERENCES Account (account_id)
      LABEL Owns PROPERTIES (
        person_id,
        account_id,
        role,
        create_time
      ),
    UsesDevice
      KEY (account_id, device_id)
      SOURCE KEY (account_id) REFERENCES Account (account_id)
      DESTINATION KEY (device_id) REFERENCES Device (device_id)
      LABEL UsesDevice PROPERTIES (
        account_id,
        device_id,
        login_count,
        ip_address,
        last_used_time
      )
  );
