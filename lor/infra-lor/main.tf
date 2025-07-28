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
