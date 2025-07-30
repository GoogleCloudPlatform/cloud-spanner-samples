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