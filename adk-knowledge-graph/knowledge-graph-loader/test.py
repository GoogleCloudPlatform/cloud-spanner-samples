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

from google.cloud import spanner
from langchain_google_spanner import SpannerGraphStore, SpannerGraphVectorContextRetriever
from langchain_google_vertexai import ChatVertexAI, VertexAIEmbeddings

import config

spanner_client = spanner.Client(project=config.PROJECT_ID)
graph = SpannerGraphStore(
    client=spanner_client,
    instance_id=config.INSTANCE_ID,
    database_id=config.DATABASE_ID,
    graph_name="docs_kb",
)
embedding_service = VertexAIEmbeddings(model_name="text-embedding-004")
retriever = SpannerGraphVectorContextRetriever.from_params(
        graph_store=graph,
        embedding_service=embedding_service,
        # label_expr="Document",
        embeddings_column="embedding",
        top_k=1,
        expand_by_hops=1,

    )
results = retriever.invoke("What are zones?")
print(f" Graph results: {results}")

# Only uncomment this if you want to DROP the graph
# This is irreversible!
# graph.cleanup()

