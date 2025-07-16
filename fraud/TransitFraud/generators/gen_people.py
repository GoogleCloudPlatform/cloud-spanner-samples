#!/usr/bin/env python

from faker import Faker
import csv

fake = Faker()

with open("../data/people.csv", "w") as csvfile:
    fieldnames = ["id", "first_name", "last_name", "email", "phone", "age"]
    writer = csv.DictWriter(csvfile, fieldnames=fieldnames)

    writer.writeheader()
    for i in range(1000):
        writer.writerow(
            {
                "id": i,
                "first_name": fake.first_name(),
                "last_name": fake.last_name(),
                "email": fake.email(),
                "phone": fake.phone_number(),
                "age": fake.random_int(min=18, max=80),
            }
        )
