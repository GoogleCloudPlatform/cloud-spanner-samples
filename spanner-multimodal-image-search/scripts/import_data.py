#!/usr/bin/env python3
# Copyright 2026 Google LLC
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

"""
import_data.py
Enterprise Multimodal Vector Search in Google Cloud Spanner
Idempotent Seeding Script for Products, Inventory, Categories, and Customer Reviews.
"""
import datetime
import os
import sys
from decimal import Decimal
from google.cloud import storage
from google.cloud import spanner
from google.cloud import spanner

# Disable Cloud Monitoring metric export warnings in Cloud Shell / headless environments
os.environ["SPANNER_DISABLE_BUILTIN_METRICS"] = "true"


def main():
    project_id = os.environ.get("PROJECT_ID")
    instance_id = os.environ.get("INSTANCE_ID")
    database_id = os.environ.get("DATABASE_ID")
    bucket_name = os.environ.get("BUCKET_NAME")

    if not all([project_id, instance_id, database_id, bucket_name]):
        print("Error: Ensure PROJECT_ID, INSTANCE_ID, DATABASE_ID, and BUCKET_NAME are exported in env.")
        sys.exit(1)

    print(f"Connecting to Cloud Spanner database: {database_id} on instance {instance_id}...")
    spanner_client = spanner.Client(project=project_id, disable_builtin_metrics=True)
    instance = spanner_client.instance(instance_id)
    database = instance.database(database_id)

    print(f"Inspecting staged assets in Cloud Storage bucket: gs://{bucket_name}/ecomm-retail/...")
    storage_client = storage.Client(project=project_id)

    blobs = list(storage_client.list_blobs(bucket_name, prefix="ecomm-retail/product-images-generic/"))
    image_blobs = [b for b in blobs if b.name.lower().endswith(('.png', '.jpg', '.jpeg'))]

    if not image_blobs:
        print(f"Error: No images found in gs://{bucket_name}/ecomm-retail/product-images-generic/! Please run the Step 2 GCS copy command first.")
        sys.exit(1)

    print(f"Discovered {len(image_blobs)} catalog media files in GCS.")

    # Ensure 00003E3B9E5336685200AE85D21B4F5E.png is prod_001 for consistent queries
    target_crop_pant = [b for b in image_blobs if "00003E3B9E5336685200AE85D21B4F5E" in b.name]
    other_blobs = [b for b in image_blobs if "00003E3B9E5336685200AE85D21B4F5E" not in b.name]
    sorted_image_blobs = target_crop_pant + sorted(other_blobs, key=lambda x: x.name)

    # Reference Taxonomies
    brands = [
        (1, "Joan Ellis", "Comfort", "United States"),
        (2, "Dean Sterling", "Luxury", "United Kingdom"),
        (3, "KIMODE", "Everyday", "Japan"),
        (4, "Honest Virtue", "Denim", "United States"),
        (5, "Commonwealth", "Classic", "United States"),
        (6, "Riff Depot", "Streetwear", "Canada"),
        (7, "Sterling Standard", "Denim", "United States"),
        (8, "Willowbend", "Denim", "United States")
    ]
    categories = [
        (1, "Pants & Capris", "Apparel"),
        (2, "Fashion Hoodies & Sweatshirts", "Apparel"),
        (3, "Suits & Sport Coats", "Apparel"),
        (4, "Sweaters & Knits", "Apparel"),
        (5, "Jeans & Denim", "Apparel"),
        (6, "Tops & Tees", "Apparel")
    ]

    sku_metadata = {
        "00003E3B9E5336685200AE85D21B4F5E": (1, 1, "Joan Ellis Women's Crop Pant", "Versatile cropped casual pant tailored with breathable fabric for comfort and everyday style.", Decimal("99.00")),
        "0004D0B59E19461FF126E3A08A814C33": (6, 2, "Hampton Tradinghouse Women's Hoodie", "Comfortable fleece hoodie designed for warmth and casual weekend styling.", Decimal("79.95")),
        "00053F5E11D1FE4E49A221165B39ABC9": (2, 3, "Dean Sterling Double-Breasted Suit Jacket", "Premium twill weave double-breasted suit jacket tailored for formal elegance.", Decimal("219.50")),
        "0006AABE0BA47A35C0B0BF6596F85159": (3, 4, "KIMODE Men's Essential V-Neck Sweater", "Classic lightweight knit sweater with a clean V-neck profile.", Decimal("50.00")),
        "000871C1FC726F0B52DC86A4EEB027DE": (4, 5, "Honest Virtue Women's Cameron Boyfriend Jean", "Relaxed fit distressed boyfriend denim crafted with premium organic cotton.", Decimal("235.00")),
        "00126B47D5502DFB7D01F750AD23D813": (5, 6, "Commonwealth Plaid Long-Sleeve Shirt", "Classic medium plaid pattern woven shirt for all-season versatility.", Decimal("29.99")),
        "0014FCB3DB4C8459D26309B177005B10": (6, 6, "Riff Depot Ribbed Fold Beanie", "Soft ribbed knit fold-over beanie providing essential warmth.", Decimal("15.99")),
        "001AB2FA029C064A45E41F8B2644A292": (7, 5, "Sterling Standard Straight Leg Jean", "Timeless straight leg blue denim with contrast stitching.", Decimal("119.00")),
        "001B8E3CF76F4E64CBE5BE9882DB4AA0": (1, 6, "Jenson Metallic Halter Swimwear", "Vibrant halter swimwear crafted with fast-drying stretch blend.", Decimal("19.99")),
        "001C728A3046207C685F7F478F4BB41B": (8, 5, "Willowbend Classic Rise Straight Jean", "Durable rugged denim featuring reinforced pockets and relaxed leg cut.", Decimal("123.94")),
    }

    colors = ["Natural Olive", "Classic Navy", "Charcoal Gray", "Warm Camel", "Midnight Black", "Heather Indigo"]
    products_rows = []
    inventory_rows = []
    reviews_rows = []

    for idx, blob in enumerate(sorted_image_blobs[:52], start=1):
        pid = f"prod_{idx:03d}"
        fname = blob.name.split("/")[-1]
        sku = fname.rsplit(".", 1)[0]
        if sku in sku_metadata:
            bid, cid, title, desc, price = sku_metadata[sku]
        else:
            bid = (idx % len(brands)) + 1
            cid = (idx % len(categories)) + 1
            cat_name = categories[cid - 1][1]
            brand_name = brands[bid - 1][1]
            title = f"{brand_name} {cat_name} {idx:03d}"
            desc = f"Premium quality {cat_name.lower()} crafted for comfort, style, and everyday wear."
            price = Decimal(f"{49.99 + (idx * 2):.2f}")

        color = colors[idx % len(colors)]
        gcs_uri = f"gs://{bucket_name}/{blob.name}"
        products_rows.append((pid, bid, cid, title, desc, color, price, gcs_uri, None))

        # Child records: Inventory
        inventory_rows.append((pid, "store_nyc", 25 + (idx % 20), "2026-09-01T08:00:00Z"))
        inventory_rows.append((pid, "store_sf", 15 + (idx % 12), "2026-09-02T12:30:00Z"))

        # Child records: CustomerReviews
        reviews_rows.append((pid, f"rev_{idx}_1", 5 - (idx % 2), "Outstanding fit, high quality fabric, and very stylish!", "2026-09-10"))
        if idx % 3 == 0:
            reviews_rows.append((pid, f"rev_{idx}_2", 4, "Great durable material and fast delivery.", "2026-09-12"))

    print(f"Writing mutations to Cloud Spanner using idempotent batch mutations ({len(products_rows)} products)...")
    with database.batch() as batch:
        batch.insert_or_update(
            "Brands",
            columns=["brand_id", "brand_name", "tier", "country_of_origin"],
            values=brands,
        )
        batch.insert_or_update(
            "ProductCategories",
            columns=["category_id", "category_name", "department"],
            values=categories,
        )
        batch.insert_or_update(
            "Products",
            columns=["product_id", "brand_id", "category_id", "name", "description", "color", "list_price", "image_gcs_uri", "image_embedding"],
            values=products_rows,
        )
        batch.insert_or_update(
            "Inventory",
            columns=["product_id", "store_id", "stock_count", "last_restock_timestamp"],
            values=inventory_rows,
        )
        batch.insert_or_update(
            "CustomerReviews",
            columns=["product_id", "review_id", "rating", "review_text", "review_date"],
            values=reviews_rows,
        )

    print(f"Database seeding successfully completed! {len(products_rows)} products populated with verified GCS URIs.")


if __name__ == "__main__":
    main()
