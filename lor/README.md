# Lord of The Rings Spanner Graph Demo

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![Python](https://img.shields.io/badge/python-3.x-blue.svg)](https://www.python.org/)
[![Docker](https://img.shields.io/badge/docker-%230db7ed.svg)](https://www.docker.com/)
[![Terraform](https://img.shields.io/badge/terraform-%235835CC.svg)](https://www.terraform.io/)
[![SpringBoot](https://img.shields.io/badge/SpringBoot-6DB33F?style=flat-square&logo=Spring&logoColor=white)](https://www.terraform.io/)
[![Angular](https://img.shields.io/badge/-Angular-DD0031?style=flat-square&logo=angular&logoColor=white)](https://www.terraform.io/)

This demo shows a graph example based in the *Lord of the Rings* characters and places. 
Source data come from [this repo](https://github.com/morethanbooks/projects/tree/master/LotR) from [José Calvo](https://www.morethanbooks.eu/graph-network-of-the-lord-of-the-rings/).

![Example graph](img/graph_example.jpg?raw=true)

# Build

We are going to build this end-to-end architecture: ![Solution architecture](img/lor_architecture.jpg?raw=true)

## Prerequisites

* Google Cloud Project created with a billing account 
* Access to Google Cloud CloudShell

## Environment

Open a CloudShell session in the Google Cloud Console. Run the following, using your own values for the fields PROJECT and REGION.

```
PROJECT=[YOUR_VALUE_HERE]
REGION=[YOUR_VALUE_HERE]
SPANNER_INSTANCE_ID=graph-demo
LOR_DB=lor_graph_db
REPOSITORY=repo-$REGION
IMAGE=spanner-lor
SERVICE=spanner-lor

gcloud config set project $PROJECT
PROJECT_NUMBER=$(gcloud projects describe $PROJECT --format="value(projectNumber)")

gcloud services enable compute.googleapis.com
gcloud services enable iam.googleapis.com
gcloud services enable cloudresourcemanager.googleapis.com
```

## Build infrastructure

We will use terraform scripts included in the repo.Infra created:

* VPC network “lor-network”
* GCS bucket “$PROJECT”
* Spanner instance, database and tables

```
git clone https://github.com/mahurtado/LoRSpannerGraph
cd LoRSpannerGraph/infra-lor/
echo project = \"$PROJECT\" >> my-config.tfvars
echo region  = \"$REGION\" >> my-config.tfvars

terraform init
terraform plan -var-file=my-config.tfvars 
terraform apply -var-file=my-config.tfvars
```

Type “yes”. Infrastructure creation takes four minutes approximately.

**Checkpoint**. Spanner instance, database and tables should be created:

![Checkpoint](img/checkpoint_spanner_creation.jpg?raw=true)

## Load data into Spanner

Download source files and prepare for loading. Then move to GCS bucket.

```
curl -O https://raw.githubusercontent.com/morethanbooks/projects/master/LotR/ontologies/ontology.csv
tail -n +2 ontology.csv > ontology.tmp && mv ontology.tmp ontology.csv
sed $'s/\t/:/g' ontology.csv > ontology.tmp && mv ontology.tmp ontology.csv

gcloud storage cp ontology.csv gs://$PROJECT

curl -O https://raw.githubusercontent.com/morethanbooks/projects/master/LotR/tables/networks-id-3books.csv
tail -n +2 networks-id-3books.csv > networks-id-3books.tmp && mv networks-id-3books.tmp  networks-id-3books.csv
sed $'s/,/:/g' networks-id-3books.csv >  networks-id-3books.tmp && mv networks-id-3books.tmp networks-id-3books.csv

gcloud storage cp networks-id-3books.csv gs://$PROJECT

rm ontology.csv
rm networks-id-3books.csv
```

Now we will use this [Dataflow template](https://cloud.google.com/dataflow/docs/guides/templates/provided/cloud-storage-to-cloud-spanner) to import files into Spanner. For each file, create the [manifest file](https://cloud.google.com/spanner/docs/import-export-csv#create-json-manifest) and upload to our GCS bucket:

```
echo "{
  \"tables\": [
    {
      \"table_name\": \"Ontology\",
      \"file_patterns\": [
        \"gs://$PROJECT/ontology.csv\"
      ],
      \"columns\": [
        {\"column_name\": \"OntologyId\", \"type_name\": \"STRING\"},
        {\"column_name\": \"Type\", \"type_name\": \"STRING\"},
        {\"column_name\": \"Label\", \"type_name\": \"STRING\"},
        {\"column_name\": \"FreqSum\", \"type_name\": \"INT64\"},
        {\"column_name\": \"Subtype\", \"type_name\": \"STRING\"},
        {\"column_name\": \"Gender\", \"type_name\": \"STRING\"}
      ]
    }
  ]
}" > ontology_load.json
gcloud storage cp ontology_load.json gs://$PROJECT
rm ontology_load.json

echo "{
  \"tables\": [
    {
      \"table_name\": \"Reference\",
      \"file_patterns\": [
        \"gs://$PROJECT/networks-id-3books.csv\"
      ],
      \"columns\": [
        {\"column_name\": \"IdSource\", \"type_name\": \"STRING\"},
        {\"column_name\": \"IdTarget\", \"type_name\": \"STRING\"},
        {\"column_name\": \"Times\", \"type_name\": \"INT64\"},
        {\"column_name\": \"Type\", \"type_name\": \"STRING\"}      ]
    }
  ]
}" > reference_load.json
gcloud storage cp reference_load.json gs://$PROJECT
rm reference_load.json

```

**Checkpoint**: see files uploaded to GCS Bucket:

![Checkpoint](img/checkpoint_gcs.jpg?raw=true)

Now run the Dataflow jobs for loading data:

```
gcloud dataflow jobs run load_ontology \
    --gcs-location gs://dataflow-templates-$REGION/latest/GCS_Text_to_Cloud_Spanner \
    --region $REGION --disable-public-ips --subnetwork=https://www.googleapis.com/compute/v1/projects/$PROJECT/regions/$REGION/subnetworks/lor-network-region \
    --parameters \
instanceId=$SPANNER_INSTANCE_ID,\
databaseId=$LOR_DB,\
importManifest=gs://$PROJECT/ontology_load.json,\
columnDelimiter=:

gcloud dataflow jobs run load_references \
    --gcs-location gs://dataflow-templates-$REGION/latest/GCS_Text_to_Cloud_Spanner \
    --region $REGION --disable-public-ips --subnetwork=https://www.googleapis.com/compute/v1/projects/$PROJECT/regions/$REGION/subnetworks/lor-network-region \
    --parameters \
instanceId=$SPANNER_INSTANCE_ID,\
databaseId=$LOR_DB,\
importManifest=gs://$PROJECT/reference_load.json,\
columnDelimiter=:

```

**Checkpoint**. Go to Dataflow jobs in the console, see the jobs running:

![Checkpoint](img/checkpoint_dataflow.jpg?raw=true)

Data loading will take around 6 minutes.

**Checkpoint**. Go to Spanner in the console, choose instance and database, then go to Database Studio and run this query:

```
SELECT 'Reference', count(*) as total from Reference 
UNION ALL
SELECT 'Ontology', count(*) as total from Ontology 
```

Raw data is loaded into tables Reference (1444 rows) and Ontology (75 rows).

Now will do some transformations to differentiate entities between “Persons” and “Places”, and create the proper relations.

From Spanner Data Studio will do some data movement:

```
INSERT INTO Persons (Id, Label, FreqSum, Subtype, Gender)
SELECT OntologyId, Label, FreqSum, Subtype, Gender FROM Ontology WHERE Type='per';

INSERT INTO Places (Id, Label, FreqSum)
SELECT OntologyId, Label, FreqSum FROM Ontology WHERE Type='pla';

INSERT INTO PlacesPersons (IdPlace, IdPerson)
select A.IdSource,A.IdTarget from Reference A 
join Ontology B on A.IdSource=B.OntologyId 
where B.Type='pla'
and exists (select true from Persons C where C.Id=A.IdTarget);

INSERT INTO PlacesPersons (IdPlace, IdPerson)
select A.IdTarget,A.IdSource from Reference A 
join Ontology B on A.IdTarget=B.OntologyId 
where B.Type='pla'
and exists (select true from Persons C where C.Id=A.IdSource);
```

**Checkpoint**: Run from the Spanner Data Studio

```
SELECT 'Persons', count(*) as total from Persons 
UNION ALL
SELECT 'Places', count(*) as total from Places
UNION ALL
SELECT 'PlacesPersons', count(*) as total from PlacesPersons
```

See data loaded: Persons (43 rows), Places (24 rows), PlacesPersons (500 rows)

## Create graph

Next step is creating the property graph in Spanner. 

```
CREATE OR REPLACE PROPERTY GRAPH LoRGraph
  NODE TABLES (
    Persons,
    Places
  )
  EDGE TABLES (
    Reference
      SOURCE KEY (IdSource) REFERENCES Persons (Id)
      DESTINATION KEY (IdTarget) REFERENCES Persons (Id),
    PlacesPersons
      SOURCE KEY (IdPerson) REFERENCES Persons (Id)
      DESTINATION KEY (IdPlace) REFERENCES Places (Id)
  );
```

**Checkpoint**: Let us find Frodo’s relations:

```
GRAPH LoRGraph 
MATCH (p1:Persons)-[ref:Reference]->(p2:Persons)
WHERE p1.id='frod' OR p2.id='frod'
RETURN p1.id as p1_id, p2.id  as p2_id;
```

It should return 38 results

## Example Notebook

At this point you can run the [example notebook](notebook-lor/LoR_Spanner_Graph.ipynb)

![Notebook graph](img/notebook_1.jpg?raw=true)

## Build Cloud Run backend service

Now let us buld the back end service in Cloud Run. This demo uses Java SpringBoot. Continue from CloudShell session:

```
cd $HOME/LoRSpannerGraph/backend-lor/

# Policies
gcloud org-policies reset constraints/iam.allowedPolicyMemberDomains --project=$PROJECT

# Build app
mvn clean install -DskipTests

# Build container
gcloud auth configure-docker
docker build --tag=$REGION-docker.pkg.dev/$PROJECT/$REPOSITORY/$IMAGE:latest .

# Push to artifact registry
docker push $REGION-docker.pkg.dev/$PROJECT/$REPOSITORY/$IMAGE:latest

# Deploy cloud run service
gcloud run deploy $SERVICE --image $REGION-docker.pkg.dev/$PROJECT/$REPOSITORY/$IMAGE:latest \
--region=$REGION \
--set-env-vars="INSTANCE_ID=$SPANNER_INSTANCE_ID,DATABASE_ID=$LOR_DB" \
--allow-unauthenticated
SPANNER_INSTANCE_ID=graph-demo
LOR_DB=lor_graph_db
```

Save the endpoint created to a variable:
Example URL: https://spanner-lor-191614030982.europe-southwest1.run.app

It has this format:
```
SERVICE_ENDPOINT=https://$SERVICE-$PROJECT_NUMBER.$REGION.run.app
```

**Checkpoint**: Call the service from command line:

```
curl -X POST -H 'Content-Type: application/json' \
-d '{"kinds":["animal","orcs","hobbit","ents","men","dwarf","ainur","elves"],"characters":["frod","sams","ganda","arag","pipp","merr","goll","gimli","bilb","lego","saur","fara","saru","boro","theod","elro","eome","treeb","tomb","dene"],"places":["andu","bage","bree","dtow","edor","gond","helm","hton","isen","lori","loth","mdoo","mirk","mord","morg","mori","nume","oldf","orth","osgi","rive","roha","shir","tiri"],"minStrenght":1,"maxStrenght":533}' \
$SERVICE_ENDPOINT/api/run

```

A JSON response with nodes & edges should be returned.

## Build Firebase Angular frontend service

Access the firebase console:
https://console.firebase.google.com

Click “Create a new project”, then “Add Firebase to Google Cloud project”. Choose your project name.
Confirm Billig plan (Pay As you Go)
In the next screen, click “Continue”
In the next screen, **disable Google Analytics**. Click “Add Firebase”
Next screen, click “Continue”

Now continue from CloudShell.

```
cd ../web-lor/
```

Install Angular CLI

```
npm install -g @angular/cli 
npm install
```

Install Firebase CLI:

```
npm install -g firebase-tools
firebase login
```

NOTE: If this error or similar appear:
Firebase CLI v13.20.2 is incompatible with Node.js v16.4.0 Please upgrade Node.js to version >=18.0.0 || >=20.0.0
Then run:
```
nvm install 22.9.0
```

Now add current project to Firebase and prepare hosting.

```
firebase use $PROJECT
firebase init hosting
```

Choose options:
* directory **dist/web-lor/browser**
* single-page app: Yes
* Automatic builds: No

Edit file **firebase.json**. Add the code block  "source": "/api/\*", above "source": "\*\*" **Change the region value to your own region**.

```
...
"rewrites": [
      {
        "source": "/api/*",
        "run": {
          "serviceId": "spanner-lor",  
          "region": "europe-southwest1",    
          "pinTag": true  
        }            
      },
      {
        "source": "**",
        "destination": "/index.html"
      }
    ]

```

Build the Angular Project

```
npm install
ng build
```

Deploy to Firebase:

```
firebase deploy
```

*Checkpoint*: Access the app “Hosting URL”, click “Run”, first time takes a while to draw.  https://[PROJECT].web.app

![Checkpoint](img/graph_example.jpg?raw=true)

# Run

Now you have your application deplye and accesible in internet. Play with the web application to see different graphs, for example, choose minimum relation Relation strength to 10 and limit to kinds Hobbits, Men, Elves and Dwarfs:

![Graph example](img/graph_1.jpg?raw=true)

Click on “View Query” to see what is running in Spanner:

![Graph example](img/query_web.jpg?raw=true)


# Contributing
Pull requests are welcome. 

## License

Apache License 2.0. See the [LICENSE](LICENSE.txt) file.