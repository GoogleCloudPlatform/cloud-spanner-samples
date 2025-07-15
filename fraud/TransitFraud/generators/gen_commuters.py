#!/usr/bin/env python

from faker import Faker
import csv
from datetime import datetime
from dateutil.relativedelta import relativedelta

fake = Faker()

with open("../data/rides.csv", "w") as csvfile:
    fieldnames = ["oyster_id", "ride_date", "ride_station"]
    writer = csv.DictWriter(csvfile, fieldnames=fieldnames)

    writer.writeheader()
    for i in range(1350):
        morning_hour = fake.random_int(min=6, max=8)
        evening_hour = fake.random_int(min=16, max=20)
        half_hour = fake.random_int(min=0, max=1)
        morning_station = fake.random_int(min=0, max=653)
        evening_station = fake.random_int(min=0, max=653)
        for j in range(30):
            writer.writerow(
                {
                    "oyster_id": i,
                    "ride_date": fake.date_time_between_dates(
                        datetime_start=datetime.now() - relativedelta(days=j),
                        datetime_end=datetime.now() - relativedelta(days=j - 1),
                    ).strftime("%Y-%m-%d")
                    + "T{:02}:{:02}:{:02}.0Z".format(
                        morning_hour,
                        fake.random_int(min=0, max=20) + half_hour * 30,
                        fake.random_int(min=0, max=59),
                    ),
                    "ride_station": morning_station,
                }
            )
            writer.writerow(
                {
                    "oyster_id": i,
                    "ride_date": fake.date_time_between_dates(
                        datetime_start=datetime.now() - relativedelta(days=j),
                        datetime_end=datetime.now() - relativedelta(days=j - 1),
                    ).strftime("%Y-%m-%d")
                    + "T{:02}:{:02}:{:02}.0Z".format(
                        evening_hour,
                        fake.random_int(min=0, max=20) + half_hour * 30,
                        fake.random_int(min=0, max=59),
                    ),
                    "ride_station": evening_station,
                }
            )
