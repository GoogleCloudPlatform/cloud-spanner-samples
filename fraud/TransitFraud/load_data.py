#!/usr/bin/env python

from models.stations import *
from models.people import *
from models.utils import *
from models.rides import *
from google.cloud import spanner
from sys import exit

if __name__ == "__main__":
    s = spanner.Client()
    instance = s.instance("transit")
    client = instance.database("transitdb")
    stations = Stations()
    client.run_in_transaction(writeSpanner, stations)
    addresses = Addresses()
    client.run_in_transaction(writeSpanner, addresses)
    people = Persons()
    client.run_in_transaction(writeSpanner, people)

    inhabitants = HasInhabitants()
    client.run_in_transaction(writeSpanner, inhabitants)

    oysters = Oysters()
    client.run_in_transaction(writeSpanner, oysters)

    hasoysters = HasOysters()
    client.run_in_transaction(writeSpanner, hasoysters)

    routes = Routes()
    client.run_in_transaction(writeSpanner, routes)
    shortest_routes = ShortestRoutes()
    for i in shortest_routes.split(1000):
        x = ShortestRoutes(fromCsv=False)
        x.list_items = i
        client.run_in_transaction(writeSpanner, x)

    rides = Rides()
    for i in rides.split(1000):
        x = Rides(fromCsv=False)
        x.list_items = i
        client.run_in_transaction(writeSpanner, x)
