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

variable "gcp_service_list" {
  description ="The list of apis necessary for the project"
  type = list(string)
  default = [
    "compute.googleapis.com", 
    "dataflow.googleapis.com", 
    "spanner.googleapis.com", 
    "artifactregistry.googleapis.com", 
    "cloudbuild.googleapis.com", 
    "run.googleapis.com", 
    "orgpolicy.googleapis.com"
  ]
}

resource "google_project_service" "gcp_services" {
  for_each = toset(var.gcp_service_list)
  project = var.project
  service = each.key
}

resource "time_sleep" "wait_30_seconds" {
  depends_on = [google_project_service.gcp_services]
  create_duration = "30s"
}

module "network" {
  depends_on = [time_sleep.wait_30_seconds]
  project    = var.project
  region     = var.region
  source     = "./network"
}

module "storage" {
  depends_on = [time_sleep.wait_30_seconds]
  project    = var.project
  source     = "./storage"
  region     = var.region
}

module "spanner" {
  depends_on = [time_sleep.wait_30_seconds]
  project    = var.project
  source     = "./spanner"
  region     = var.region
}

module "registry" {
  depends_on = [time_sleep.wait_30_seconds]
  source     = "./registry"
  region     = var.region
}

data "google_compute_default_service_account" "default" {
}

resource "google_project_iam_member" "cgs_role" {
  project = var.project
  role = "roles/storage.admin"
  member = "serviceAccount:${data.google_compute_default_service_account.default.email}"
}

resource "google_project_iam_member" "spanner_role" {
  project = var.project
  role = "roles/spanner.databaseUser"
  member = "serviceAccount:${data.google_compute_default_service_account.default.email}"
}

resource "google_project_iam_member" "dataflow_role" {
  project = var.project
  role = "roles/dataflow.worker"
  member = "serviceAccount:${data.google_compute_default_service_account.default.email}"
}
