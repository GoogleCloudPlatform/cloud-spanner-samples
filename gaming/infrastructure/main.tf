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

variable "gcp_project" {
  type = string
}

variable "spanner_config" {
  type = object({
    instance_name     = string
    database_name     = string
    configuration     = string
    display_name      = string
    processing_units  = number
    environment       = string
  })
  description = "The configuration specifications for the Spanner instance"

  validation {
    condition     = length(var.spanner_config.display_name) >= 4 && length(var.spanner_config.display_name) <= "30"
    error_message = "Display name must be between 4-30 characters long."
  }

  validation {
    condition     = (var.spanner_config.processing_units <= 1000) && (var.spanner_config.processing_units%100) == 0
    error_message = "Processing units must be 1000 or less, and be a multiple of 100."
  }
}

provider "google" {
  project = var.gcp_project
}

resource "google_spanner_instance" "instance" {
  name             = var.spanner_config.instance_name  # << be careful changing this in production
  config           = var.spanner_config.configuration
  display_name     = var.spanner_config.display_name
  processing_units = var.spanner_config.processing_units
  labels           = { "env" = var.spanner_config.environment }
}

resource "google_spanner_database" "database" {
  instance = google_spanner_instance.instance.name
  name     = var.spanner_config.database_name
  deletion_protection = false
}

# Create IAM service account for locked down cloud run container to access Spanner service
resource "google_service_account" "profile_backend_sa" {
  account_id   = "profile-backend"
  display_name = "Player profile backend service"
}

resource "google_spanner_database_iam_binding" "profile_iam_spanner" {
  instance = google_spanner_instance.instance.name
  database = google_spanner_database.database.name
  role     = "roles/spanner.databaseUser"

  members = [
    "serviceAccount:${google_service_account.profile_backend_sa.email}",
  ]
}

# Create user frontend IAM service account and appropriate service
# resource "google_service_account" "user_frontend_sa" {
#   account_id   = "user-frontend"
#   display_name = "User Frontend Service"
# }

# resource "google_project_iam_binding" "user_frontend_iam" {
#   project = var.gcp_project
#   role     = "roles/run.invoker"

#   members = [
#     "serviceAccount:${google_service_account.user_frontend_sa.email}",
#   ]
# }
