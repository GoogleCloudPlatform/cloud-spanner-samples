resource "google_compute_network" "lor-network" {
  name                    = "lor-network"
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "lor-network-region" {
  name          = "lor-network-region"
  ip_cidr_range = "10.2.0.0/16"
  region        = var.region
  network       = google_compute_network.lor-network.id
  private_ip_google_access = true
}
