# Leveraging GNNs for Autonomous Network Operations (ANO)

This example notebook and synthetically generated data showcases a Root Cause Analysis (RCA) detection use case by leveraging Spanner Graph digital twin and Graph Neural Networks (GNNs) built on Distributed Graph Flow (DGF).

### Data Load
The data folder contains graph export of the Telco graph in parquet format. Please load the nodesets and edgesets into a spanner database and define a graph over it. You can leverage BigQuery reverse ETL to do this.
* Load these parquet files to your GCS bucket.
* Follow https://docs.cloud.google.com/bigquery/docs/loading-data-cloud-storage-parquet to load individual parquet files to corresponding node and edge tables.
* Reverse ETL these tables to your Spanner database. https://docs.cloud.google.com/bigquery/docs/export-to-spanner
* Once the data is migrated to Spanner, use below ddl to create Spanner Graph.

```
CREATE OR REPLACE PROPERTY GRAPH tmf_graph_all
  NODE TABLES(
    tmf_node_anomaly AS anomaly
      KEY(anomaly_id)
      LABEL anomaly PROPERTIES(
        anomaly_id,
        anomaly_label,
        anomaly_type,
        composite_anomaly_score,
        criticality,
        impact_score),

    tmf_node_entity AS entity
      KEY(eid)
      LABEL entity PROPERTIES(
        criticality_tier,
        eid,
        entity_type,
        network_domain,
        network_segment),

    tmf_node_event AS event
      KEY(event_id)
      LABEL event PROPERTIES(
        change_request_id,
        event_id,
        incident_type,
        root_cause,
        t_end,
        t_start,
        ttr_minutes)
  )
  EDGE TABLES(
    tmf_edge_anomaly_affects_entity AS anomaly_affects_entity
      KEY(anomaly_id, eid)
      SOURCE KEY(anomaly_id) REFERENCES anomaly(anomaly_id)
      DESTINATION KEY(eid) REFERENCES entity(eid)
      LABEL affects PROPERTIES(
        anomaly_id,
        eid),

    tmf_edge_event_triggers_anomaly AS event_triggers_anomaly
      KEY(event_id, anomaly_id)
      SOURCE KEY(event_id) REFERENCES event(event_id)
      DESTINATION KEY(anomaly_id) REFERENCES anomaly(anomaly_id)
      LABEL triggers PROPERTIES(
        anomaly_id,
        event_id),

    tmf_edge_entity_to_entity
      KEY(eid, to_eid)
      SOURCE KEY(eid) REFERENCES entity(eid)
      DESTINATION KEY(to_eid) REFERENCES entity(eid)
      LABEL connects PROPERTIES(
        direction,
        edge_type,
        eid,
        network_domain,
        to_eid)
  );
```

### The notebook does following things: 
* Connect to the Digital Twin: Use the DGF Spanner Graph connector (dgf.io.read_spanner_graph) to load the network topology directly from Spanner Graph's Digital Twin into the DGF environment.
* Train a Supervised Node (or Edge) Prediction model: Depending on the training data and objective, you will train a supervised node prediction model to predict a target node feature or an edge prediction model to predict an edge between the root cause entity node and the affected entity node. For the given sample data you will use the high-level dgf.learning.train_node_model API to train a supervised node prediction model.
* Use the node prediction model to predict root cause node: The node prediction model can be directly used to predict the impact score on the node with the anomaly. Entity nodes affected by the anomaly with highest predicted impact score will be the top candidates for root cause.
* Deploy to Gemini Enterprise (formerly Vertex AI): Export  the model and host it on a Gemini Enterprise endpoint to enable scalable, low-latency predictions.
* Real-time Inference: Make prediction calls to the inference endpoint with the anomaly date as input. The endpoint will return the predicted root cause Entity nodes.


### Tear Down
* To delete the data, first drop the property graph, then you can drop the tables.
* Also, delete the model endpoint and the model from the Gemini Enterprise Agent platform.

