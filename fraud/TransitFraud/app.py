#!/usr/bin/env python

from flask import Flask, render_template, request
from flask_bootstrap import Bootstrap
from google.cloud import spanner
import json
from collections import namedtuple
from datetime import datetime

app = Flask(
    __name__,
    static_url_path="/docs",
    static_folder="docs",
)

bootstrap = Bootstrap()
s = spanner.Client()
instance = s.instance("transit")
client = instance.database("transitdb")

Person = namedtuple("Person", ["id", "name", "group"])
Card = namedtuple(
    "Card",
    [
        "id",
        "name",
        "group",
    ],
)
Address = namedtuple("Address", ["id", "name", "group"])
Edge = namedtuple("Edge", ["source", "target", "group"])


def get_card_history(client, card_id):
    query = """
    SELECT r.id, r.timestamp, s.name AS station_name, FROM Ride AS r JOIN Station AS s ON r.station_id = s.id WHERE r.oyster_id = {} ORDER BY r.timestamp DESC LIMIT 25;
    """.format(
        card_id
    )
    with client.snapshot() as snapshot:
        results = snapshot.execute_sql(query)
        return results


def station_id_to_name(client, station_id):
    query = "SELECT name from Station where id={}".format(int(station_id))
    with client.snapshot() as snapshot:
        results = snapshot.execute_sql(query)
        for row in results:
            return row[0]
    return None


def is_teleport(client, card_id, station_id, timestamp):
    query = """
    SELECT time AS ShortestPossibleTime, latest_ride.timestamp, station_id,
      TIMESTAMP_ADD(latest_ride.timestamp, INTERVAL CAST(time AS INT64) SECOND) AS timeLimit from ShortestRoute
        INNER JOIN (
          SELECT station_id, timestamp from Ride ride where oyster_id={} ORDER BY timestamp DESC LIMIT 1
        ) as latest_ride
      ON ShortestRoute.to_station = latest_ride.station_id
    WHERE from_station = {};
    """.format(
        card_id, station_id
    )
    with client.snapshot() as snapshot:
        results = snapshot.execute_sql(query)

        for row in results:
            if (row[1] - timestamp).total_seconds() < row[0]:
                return row
            else:
                return None


def data_from_graph(card_id):
    node_set = []
    link_set = []
    query = """
    GRAPH TransitGraph
        MATCH (o:Oyster{{id: {}}})-[o1:HAS_OYSTER]->(p:Person)<-[:HAS_INHABITANT]-(a:Address)-[:HAS_INHABITANT]->(q:Person)-[o2:HAS_OYSTER]-(r:Oyster)
        WHERE q.id != p.id
        RETURN p.id as pid, p.firstname as src_firstanme, p.lastname as src_lastname,
        o.id as src_card_id,
        a.id as address_id, a.address, 
        q.firstname as tgt_firstanme, q.lastname as tgt_lastname, q.id as p2id,  r.id as linked_card_id, r.is_suspect as sus
    """.format(
        card_id
    )
    with client.snapshot() as snapshot:
        results = snapshot.execute_sql(query)
        for row in results:
            node_set.append(
                Person(
                    "Person{}".format(row[0]), "{} {}".format(row[1], row[2]), "person"
                )
            )
            node_set.append(
                Person(
                    "Person{}".format(row[8]), "{} {}".format(row[6], row[7]), "person"
                )
            )
            node_set.append(Address("Address{}".format(row[4]), row[5], "address"))
            node_set.append(
                Card("Oyster{}".format(row[3]), "Oyster{}".format(row[3]), "card")
            )
            if row[10] > 0:
                node_set.append(
                    Card(
                        "Oyster{}".format(row[9]),
                        "SUSPECT-Oyster{}".format(row[9]),
                        "card{}".format(row[10]),
                    )
                )
            else:
                node_set.append(
                    Card(
                        "Oyster{}".format(row[9]),
                        "Oyster{}".format(row[9]),
                        "card{}".format(row[10]),
                    )
                )

            link_set.append(
                Edge("Person{}".format(row[8]), "Oyster{}".format(row[9]), "owns")
            )
            link_set.append(
                Edge("Person{}".format(row[0]), "Oyster{}".format(row[3]), "owns")
            )
            link_set.append(
                Edge("Person{}".format(row[0]), "Address{}".format(row[4]), "resides")
            )
            link_set.append(
                Edge("Person{}".format(row[8]), "Address{}".format(row[4]), "resides")
            )
    return json.dumps(
        {
            "nodes": [n._asdict() for n in set(node_set)],
            "links": [l._asdict() for l in set(link_set)],
        }
    )


@app.route("/data/<int:card_id>")
def data(card_id):
    data = data_from_graph(card_id)
    response = app.response_class(
        response=data, status=200, mimetype="application/json"
    )
    return response


@app.route("/view", methods=["POST"])
def view():
    card_id = request.form["cardid"]
    return render_template("view.html", card_id=card_id)

@app.route("/raw", methods=["POST"])
def raw():
    response='{"status": "UNKNOWN"}'
    query = """GRAPH TransitGraph
    MATCH p={}
    RETURN SAFE_TO_JSON(p) as thepath
    """.format(request.get_data().decode('utf-8'))
    print(query)
    with client.snapshot() as snapshot:
        results = snapshot.execute_sql(query)
        for row in results:
            response = json.dumps(row[0].__dict__["_array_value"])
    return(response)

@app.route("/cloneview", methods=["POST"])
def cloneview():
    card_id = request.form["cardid"]
    station_id = request.form["stationid"]
    timestamp = datetime.fromisoformat(request.form["timestamp"])
    row = is_teleport(client, card_id, station_id, timestamp)
    return render_template(
        "cloneview.html",
        row=row,
        station_id=station_id,
        card_id=card_id,
        history=get_card_history(client, card_id),
        from_station=station_id_to_name(client, station_id),
        to_station=station_id_to_name(client, row[2]),
    )


@app.route("/")
def index():
    return render_template("index.html")


@app.route("/clone")
def cloneform():
    return render_template("cloneform.html")


@app.route("/explanation")
def explanation():
    return render_template("explanation.html")


if __name__ == "__main__":
    bootstrap.init_app(app)
    app.debug = True
    app.run(port=5000, host="0.0.0.0")
