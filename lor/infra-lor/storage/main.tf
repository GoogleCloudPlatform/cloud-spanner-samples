resource "google_storage_bucket" "my-bucket" {
  name          = var.project
  storage_class = "REGIONAL"
  location      = var.region
  force_destroy = true
  uniform_bucket_level_access = true
}