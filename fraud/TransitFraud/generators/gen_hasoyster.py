#!/usr/bin/env python

from faker import Faker
import csv

fake = Faker()

with open("../data/has_oyster.csv", "w") as csvfile:
    fieldnames = ["id", "to_person"]
    writer = csv.DictWriter(csvfile, fieldnames=fieldnames)

    writer.writeheader()
    for i in range(1349):
        writer.writerow(
            {
                "id": i,
                "to_person": fake.random_int(min=0, max=999),
            }
        )
