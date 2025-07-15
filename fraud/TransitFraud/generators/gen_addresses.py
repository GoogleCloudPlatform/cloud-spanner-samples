#!/usr/bin/env python

from faker import Faker
import csv

fake = Faker()

with open("../data/addresses.csv", "w") as csvfile:
    fieldnames = ["id", "address"]
    writer = csv.DictWriter(csvfile, fieldnames=fieldnames)

    writer.writeheader()
    for i in range(850):
        writer.writerow(
            {
                "id": i,
                "address": fake.street_address(),
            }
        )
