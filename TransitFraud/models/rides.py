#! /usr/bin/env python
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


from dataclasses import dataclass
import csv
import uuid


@dataclass
class Ride:
    id: str
    oyster_id: int
    station_id: int
    timestamp: str

    def __init__(self, ride):
        self.id = str(uuid.uuid4())
        self.oyster_id = int(ride["oyster_id"])
        self.station_id = int(ride["ride_station"])
        self.timestamp = ride["ride_date"]


@dataclass
class Rides:
    list_items: list[Ride]

    def __init__(self, fromCsv=True):
        if fromCsv:
            with open("data/rides.csv") as f:
                reader = csv.DictReader(f)
                self.list_items = [Ride(row) for row in reader]

    def split(self, n):
        res = []
        for i in range(0, len(self.list_items), n):
            res.append(self.list_items[i : i + n])
        return res
