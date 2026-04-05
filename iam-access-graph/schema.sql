-- Copyright 2026 Google LLC
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     https://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- Node tables

CREATE TABLE Identities (
  identity_id INT64 NOT NULL,
  name STRING(MAX),
  email STRING(MAX) NOT NULL,
  type STRING(MAX),
  risk_score FLOAT64
) PRIMARY KEY (identity_id);

CREATE TABLE UserGroups (
  group_id INT64 NOT NULL,
  email STRING(MAX) NOT NULL,
  name STRING(MAX),
  category STRING(MAX)
) PRIMARY KEY (group_id);

CREATE UNIQUE INDEX UserGroupsByEmail ON UserGroups(email);

CREATE TABLE Resources (
  resource_id INT64 NOT NULL,
  name STRING(MAX),
  sensitivity STRING(MAX)
) PRIMARY KEY (resource_id);

-- Edge tables

CREATE TABLE Membership (
  identity_id INT64 NOT NULL,
  group_id INT64 NOT NULL,
  CONSTRAINT FK_Membership_Identity FOREIGN KEY (identity_id) REFERENCES Identities (identity_id),
  CONSTRAINT FK_Membership_Group FOREIGN KEY (group_id) REFERENCES UserGroups (group_id)
) PRIMARY KEY (identity_id, group_id),
  INTERLEAVE IN PARENT Identities ON DELETE CASCADE;

CREATE TABLE GroupNesting (
  group_id INT64 NOT NULL,
  child_group_id INT64 NOT NULL,
  CONSTRAINT FK_ChildGroup FOREIGN KEY (child_group_id) REFERENCES UserGroups (group_id)
) PRIMARY KEY (group_id, child_group_id),
  INTERLEAVE IN PARENT UserGroups ON DELETE CASCADE;

CREATE TABLE Permissions (
  group_id INT64 NOT NULL,
  resource_id INT64 NOT NULL,
  role STRING(MAX) NOT NULL,
  CONSTRAINT FK_Permissions_Group FOREIGN KEY (group_id) REFERENCES UserGroups (group_id),
  CONSTRAINT FK_Permissions_Resource FOREIGN KEY (resource_id) REFERENCES Resources (resource_id)
) PRIMARY KEY (group_id, resource_id, role);

CREATE TABLE DirectAccess (
  identity_id INT64 NOT NULL,
  resource_id INT64 NOT NULL,
  expires_at TIMESTAMP,
  revoked BOOL,
  CONSTRAINT FK_DirectAccess_Identity FOREIGN KEY (identity_id) REFERENCES Identities (identity_id),
  CONSTRAINT FK_DirectAccess_Resource FOREIGN KEY (resource_id) REFERENCES Resources (resource_id)
) PRIMARY KEY (identity_id, resource_id);

-- Property Graph

CREATE PROPERTY GRAPH SecurityGraph
  NODE TABLES (Identities, Resources, UserGroups)
  EDGE TABLES (
    Membership
      SOURCE KEY (identity_id) REFERENCES Identities (identity_id)
      DESTINATION KEY (group_id) REFERENCES UserGroups (group_id)
      LABEL IS_MEMBER,
    GroupNesting
      SOURCE KEY (child_group_id) REFERENCES UserGroups (group_id)
      DESTINATION KEY (group_id) REFERENCES UserGroups (group_id)
      LABEL NESTED_IN,
    Permissions
      SOURCE KEY (group_id) REFERENCES UserGroups (group_id)
      DESTINATION KEY (resource_id) REFERENCES Resources (resource_id)
      LABEL HAS_PERMISSION,
    DirectAccess
      SOURCE KEY (identity_id) REFERENCES Identities (identity_id)
      DESTINATION KEY (resource_id) REFERENCES Resources (resource_id)
      LABEL HAS_JIT_ACCESS
  );
