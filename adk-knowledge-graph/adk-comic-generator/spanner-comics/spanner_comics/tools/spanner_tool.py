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

import os
from dotenv import load_dotenv
from google.cloud import spanner
from langchain_google_spanner import SpannerGraphStore, SpannerGraphVectorContextRetriever
from langchain_google_vertexai import VertexAIEmbeddings

load_dotenv()
def get_spanner_retriever():
    """Initializes and returns a SpannerGraphVectorContextRetriever."""
    project_id = os.environ.get("SPANNER_PROJECT")
    instance_id = os.environ.get("SPANNER_INSTANCE")
    database_id = os.environ.get("SPANNER_DATABASE")

    spanner_client = spanner.Client(project=project_id)
    graph_store = SpannerGraphStore(
        client=spanner_client,
        instance_id=instance_id,
        database_id=database_id,
        graph_name='docs_kb',
    )
    
    embedding_service = VertexAIEmbeddings(model_name="text-embedding-004", project=project_id)
    docs = SpannerGraphVectorContextRetriever.from_params(
        graph_store=graph_store,
        embedding_service=embedding_service,
        embeddings_column="embedding",
        top_k=1,
        expand_by_hops=1,
    )
    return docs

def query_knowledge_graph(question: str) -> str:
    """Queries the Spanner knowledge graph with a given question."""
    retriever = get_spanner_retriever()
    docs = retriever.invoke(question)
    return "\n\n".join([doc.page_content for doc in docs])

if __name__ == '__main__':
    # Example usage
    question = "What are zones?"
    answer = query_knowledge_graph(question)
    print(f"Question: {question}")
    print(f"Answer: {answer}")