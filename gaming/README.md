# Spanner Gaming Samples

This repository contains sample code for the following use-cases when using Cloud Spanner for the backend:

- Player creation, login, and skin changes

## How to use this

### Setup infrastructure
A terraform file is provided that creates the appropriate resources for these samples.

Resources that are created:
- Spanner instance and database based on user variables in main.tfvars
- (FUTURE) GKE cluster to run the load generators

To set up the infrastructure, do the following:

- Copy `infrastructure/terraform.tfvars.sample` to `infrastructure/terraform.tfvars`
- Modify `infrastructure/terraform.tfvars` for PROJECT and instance configuration
- `terraform apply` from within infrastructure directory

```
cd infrastructure
cp terraform.tfvars.sample terraform.tfvars
vi terraform.tfvars # modify variables

terraform apply
```

### Setup schema
Schema is managed by [Wrench](https://github.com/cloudspannerecosystem/wrench).

After installing wrench, migrate the schema by running the `schema.bash` file (replace project/instance/database information with what was used in terraform file):

```
export SPANNER_PROJECT_ID=PROJECTID
export SPANNER_INSTANCE_ID=INSTANCEID
export SPANNER_DATABASE_ID=DATABASEID
./schema.bash
```

### Player profile sample
- Configure the `profile-service` by copying the `profile-service/config.yml.template` file to `profile-service/config.yml`, and modify the Spanner connection details:
```
spanner:
  project_id: YOUR_GCP_PROJECT_ID
  instance_id: YOUR_SPANNER_INSTANCE_ID
  database_id: YOUR_SPANNER_DATABASE_ID

```

- Run the profile service

```
cd src/golang/profile-service
go run .
```

- Configure the `matchmaking-service` by copying the `matchmaking-service/config.yml.template` file to `matchmaking-service/config.yml`, and modify the Spanner connection details:
```
spanner:
  project_id: YOUR_GCP_PROJECT_ID
  instance_id: YOUR_SPANNER_INSTANCE_ID
  database_id: YOUR_SPANNER_DATABASE_ID

```

- Run the match-making service

```
cd src/golang/matchmaking-service
go run .
```

- [Generate load](generators/README.md).


### Generator dependencies

The generators are run by Locust.io, which is a Python framework for generating load.

There are several dependencies required to get the generators to work:

- [pyenv](https://github.com/pyenv/pyenv)
- Python 3

#### PyEnv
Pyenv is used to manage multiple versions of Python, and libraries installed through pip separately for different projects.
Follow the PyEnv [installation guide](https://github.com/pyenv/pyenv#installation) to set this up.

Once PyEnv is setup, then you need to install Python 3.6. For instance, to install the latest (at the moment) of `3.6.15`, do this:

```
pyenv install 3.6.15
pyenv global 3.6.15
python -V
# Python 3.6.15
```

#### Python dependencies
Next, install pip3 dependencies:

```
pip3 install -r requirements.txt
```

## How to build the services

A Makefile is provided to build the services. Example commands:

```
make profile
make matchmaking
make item
make tradepost
make build-all
```

> NOTE: The build command currently assumes GOOS=linux and GOARCH=386, so building on other platforms currently is not supported.

## How to run the service tests
A Makefile is provided to test the services. Both unit tests and integration tests are provided.

Example commands:

```
make profile-test
make profile-test-integration

make test-all-unit
make test-all-integration

make test-all
```

> NOTE: The tests rely on [testcontainers-go](https://github.com/testcontainers/testcontainers-go), so [Docker](https://www.docker.com/) must be installed.


## How to clean up

If the Spanner instance was created using terraform, then from the `infrastructure` directory you can destroy the infrastructure.

```
cd infrastructure
terraform destroy
```

### Clean up build and tests
The Makefile provides a `make clean` command that removes the binaries and docker containers that were created as part of building and testing the services.

```
make clean
```
