#!/usr/bin/env python
# Copyright 2025 Google LLC
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
