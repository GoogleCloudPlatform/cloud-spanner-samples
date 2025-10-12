# Copyright 2025 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

resource "google_spanner_instance" "graph-demo" {
  config       = "regional-${var.region}"
  name         = "graph-demo"
  display_name = "Spanner LoR Graph Demo"
  processing_units    = 100
  edition      = "ENTERPRISE"
}

resource "google_spanner_database" "lor_graph_db" {
  instance = google_spanner_instance.graph-demo.name
  name     = "lor_graph_db"
  ddl = [
    "CREATE TABLE Ontology (OntologyId STRING(1024) NOT NULL, Type STRING(1024), Label STRING(1024), FreqSum INT64, Subtype STRING(1024), Gender   STRING(1024)) PRIMARY KEY(OntologyId)",
    "CREATE TABLE Reference (IdSource STRING(1024) NOT NULL, IdTarget STRING(1024) NOT NULL, Times INT64, Type STRING(1024)) PRIMARY KEY(IdSource,IdTarget)",
    "CREATE TABLE Persons (Id STRING(1024) NOT NULL, Label STRING(1024), FreqSum INT64, Subtype STRING(1024), Gender STRING(1024), FOREIGN KEY (Id) REFERENCES Ontology(OntologyId)) PRIMARY KEY(Id)",
    "CREATE TABLE Places (Id STRING(1024) NOT NULL, Label STRING(1024), FreqSum INT64, FOREIGN KEY (Id) REFERENCES Ontology(OntologyId)) PRIMARY KEY(Id)",
    "CREATE TABLE PlacesPersons (IdPlace STRING(1024) NOT NULL, IdPerson STRING(1024) NOT NULL, FreqSum INT64, FOREIGN KEY (IdPlace) REFERENCES Places(Id), FOREIGN KEY (IdPerson) REFERENCES Persons(Id)) PRIMARY KEY(IdPlace, IdPerson)"
  ]
  deletion_protection = false
}