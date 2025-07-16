#! /usr/bin/env python

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
