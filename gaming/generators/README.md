# Generators

These generators leverage the [Locust](https://locust.io) python framework for generating load against the exposed REST APIs of the sample services.

The generators can be run via the command line, or a web interface.

If using the web interface, when you run the `locust` command for each service, you can point your web browser to the exposed port to determine
concurrency of the load, in terms of "users". Then the load runs until the test is stopped in the browser.

Various charts are provided by the web interface to indicate the performance of the load test.

If you do not want to use the web interface, the command line options specify the user concurrency, as well as a run time. Statistics are printed on the
command line for the test.


Provided generators do the following:

- _authentication\_server.py_: mimics player signup and player retrieval by UUID. Login is not handled currently due to the necessity to track password creation.

Run on the CLI:
```
locust -H http://127.0.0.1:8080 -f authentication_server.py --headless -u=2 -r=2 -t=10s
```

Run webUI on port 8090:
```
locust --web-port 8090 -f authentication_server.py
# Connect browser to http://localhost:8090
```

- _match\_server.py_: mimics game servers matching players together, and closing games out.

Run on the CLI:
```
locust -H http://127.0.0.1:8081 -f match_server.py --headless -u=1 -r=1 -t=10s
```

Run on port 8091:
```
locust --web-port 8091 -f match_server.py
# Connect browser to http://localhost:8091
```

- _item\_generator.py_: generates some random items.

Run on the CLI for 5 minutes:
```
locust -H http://127.0.0.1:8082 -f item_generator.py --headless -u=1 -r=1 -t=60s
```

- _game\_server.py_: mimics adding loot and money to players during the course of a game

Run on the CLI:
```
locust -H http://127.0.0.1:8082 -f game_server.py --headless -u=1 -r=1 -t=10s
```

Run on port 8092:
```
locust --web-port 8092 -f game_server.py
# Connect browser to http://localhost:8092
```

- _trading\_server.py_: mimics posting and closing orders on trading post

Run on the CLI:
```
locust -H http://127.0.0.1:8083 -f trading_server.py --headless -u=1 -r=1 -t=10s
```

Run on port 8093:
```
locust --web-port 8093 -f trading_server.py
# Connect browser to http://localhost:8093
```


## TODO
- Plan to add a bash script to run generators
