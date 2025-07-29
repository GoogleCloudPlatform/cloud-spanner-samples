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


@dataclass
class Person:
    id: int
    firstname: str
    lastname: str
    email: str
    phone: str
    age: int

    def __init__(self, person):
        self.id = int(person["id"])
        self.firstname = person["first_name"]
        self.lastname = person["last_name"]
        self.email = person["email"]
        self.phone = person["phone"]
        self.age = person["age"]


@dataclass
class Persons:
    list_items: list[Person]

    def __init__(self):
        with open("data/people.csv") as f:
            reader = csv.DictReader(f)
            self.list_items = [Person(row) for row in reader]


@dataclass
class Address:
    id: int
    address: str

    def __init__(self, address):
        self.id = int(address["id"])
        self.address = address["address"]


@dataclass
class Addresses:
    list_items: list[Address]

    def __init__(self):
        with open("data/addresses.csv") as f:
            reader = csv.DictReader(f)
            self.list_items = [Address(row) for row in reader]


@dataclass
class HasInhabitant:
    id: int
    to_person: int

    def __init__(self, inhabitant):
        self.id = int(inhabitant["id"])
        self.to_person = int(inhabitant["to_person"])


@dataclass
class HasInhabitants:
    list_items: list[HasInhabitant]

    def __init__(self):
        with open("data/inhabitants.csv") as f:
            reader = csv.DictReader(f)
            self.list_items = [HasInhabitant(row) for row in reader]


@dataclass
class HasOyster:
    id: int
    to_person: int

    def __init__(self, oyster):
        self.id = int(oyster["id"])
        self.to_person = int(oyster["to_person"])


@dataclass
class HasOysters:
    list_items: list[HasOyster]

    def __init__(self):
        with open("data/has_oyster.csv") as f:
            reader = csv.DictReader(f)
            self.list_items = [HasOyster(row) for row in reader]


@dataclass
class Oyster:
    id: int
    issue_date: str
    issue_station: int
    is_suspect: int

    def __init__(self, card):
        self.id = int(card["id"])
        self.issue_date = card["issue_date"]
        self.issue_station = int(card["issue_station"])
        self.is_suspect = int(card["is_suspect"])


@dataclass
class Oysters:
    list_items: list[Oyster]

    def __init__(self):
        with open("data/oysters.csv") as f:
            reader = csv.DictReader(f)
            self.list_items = [Oyster(row) for row in reader]
