#!/usr/bin/env python

from faker import Faker
import csv
from datetime import datetime
from dateutil.relativedelta import relativedelta

fake = Faker()

with open("../data/oysters.csv", "w") as csvfile:
    fieldnames = ["id", "issue_date", "issue_station", "is_suspect"]
    writer = csv.DictWriter(csvfile, fieldnames=fieldnames)

    writer.writeheader()
    for i in range(1350):
        writer.writerow(
            {
                "id": i,
                "issue_date": fake.date_time_between_dates(
                    datetime_start=datetime.now() - relativedelta(years=3),
                    datetime_end=datetime.now(),
                ).strftime("%Y-%m-%d"),
                "issue_station": fake.random_int(min=0, max=1000),
                "is_suspect": 0,
            }
        )
