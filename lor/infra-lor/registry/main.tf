resource "google_artifact_registry_repository" "my-repo" {
  location      = var.region
  repository_id = "repo-${var.region}"
  format        = "DOCKER"
}