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
import requests
from bs4 import BeautifulSoup
from google.cloud import spanner
from langchain_core.documents import Document
from langchain.text_splitter import RecursiveCharacterTextSplitter
from langchain_google_vertexai import ChatVertexAI, VertexAIEmbeddings
from langchain_experimental.graph_transformers import LLMGraphTransformer
from langchain_google_spanner import SpannerGraphStore
from youtube_transcript_api import YouTubeTranscriptApi

# There is a check for the existence of a URL.
# Content parsing is skipepd if the URL exists, this may be
# a false negative for shortened URLs or URLs with parameters

# A list of URLs to scrape for Spanner documentation
URLS = [
    "https://cloud.google.com/spanner/docs?utm_campaign=CDR_0x6cb6c9c7_default_b450953078&utm_medium=external&utm_source=social",
    "https://cloud.google.com/spanner/docs/free-trial-instance?utm_campaign=CDR_0x6cb6c9c7_default_b450953078&utm_medium=external&utm_source=social",
    "https://cloud.google.com/spanner/docs/instances?utm_campaign=CDR_0x6cb6c9c7_default_b450953078&utm_medium=external&utm_source=social",
]

# Code assumes the URL has "youtube.com" in it
YOUTUBE_URLS = [
    "https://www.youtube.com/shorts/5pplCgqX_8E",
    "https://youtube.com/shorts/KHuGP4fgH9U",
    "https://youtube.com/shorts/d7Fc6NzaINE"
]

import config
spanner_client = spanner.Client(project=config.PROJECT_ID)
graph_store = SpannerGraphStore(
    client=spanner_client,
    instance_id=config.INSTANCE_ID,
    database_id=config.DATABASE_ID,
    graph_name='docs_kb',
    use_flexible_schema=True,
    static_node_properties=["embedding"],
)
ytt_api = YouTubeTranscriptApi()

def get_video_id(url):
    """
    Extracts the YouTube video ID from a given URL.
    This assumes shorts do not have additional parameters.
    """
    if "watch?v=" in url:
        return url.split("watch?v=")[1].split("&")[0]
    elif "youtu.be/" in url:
        return url.split("youtu.be/")[1].split("?")[0]
    elif "/shorts/" in url:
        return url.split("/shorts/")[1]
    return None

def get_youtube_transcript(url: str) -> str:
    """Fetches the transcript of a YouTube video."""
    try:
        video_id = get_video_id(url)
        if not video_id:
            return f" 🔪 Could not extract video ID from URL: {url}"

        print(f" 🦄  Processing Video ID: {video_id} ")
        transcript_list = ytt_api.fetch(video_id)

        transcript_texts = []
        for snippet in transcript_list:
            # print(f"  🦄 Transcription is: {snippet.text}")
            transcript_texts.append(snippet.text)

        return " ".join(transcript_texts)
    except Exception as e:
        return f"Error fetching YouTube transcript for {url}: {e}"

def scrape_documentation(url: str) -> str:
    """Fetches and extracts the main text content from a Google Cloud documentation page."""
    try:
        response = requests.get(url)
        response.raise_for_status()  # Raise an exception for bad status codes
        soup = BeautifulSoup(response.content, "html.parser")

        # The main content of Google Cloud documentation is usually within a <article> tag
        article = soup.find("article")
        if article:
            return article.get_text(separator="\n", strip=True)
        else:
            # Fallback if the <article> tag is not found
            return "Content not found in <article> tag."

    except requests.exceptions.RequestException as e:
        return f" 🔪 Error fetching URL {url}: {e}"

def document_exists(database, url):
    """Checks if a document or video with the given URL already exists in Spanner."""
    with database.snapshot() as snapshot:
        try:
            result = snapshot.execute_sql(
                "SELECT 1 FROM nodes WHERE node_type = 'Document' AND JSON_VALUE(properties, '$.source') = @url",
                params={"url": url},
                param_types={"url": spanner.param_types.STRING}
            )
            return len(list(result)) > 0
        except Exception as e:
            # This can happen if the table does not exist yet.
            if "Table not found" in str(e):
                return False
            raise e

def main():
    """Main function to scrape docs, create a graph, and save to Spanner."""
    try:
        spanner_client = spanner.Client(project=PROJECT_ID)
        instance = spanner_client.instance(INSTANCE_ID)
        database = instance.database(DATABASE_ID)
    except Exception as e:
        print(f" 🔪 An error occurred while connecting to Spanner: {e}")
        return

    print(" 🦄  Initializing components...")
    text_splitter = RecursiveCharacterTextSplitter(chunk_size=1500, chunk_overlap=200)
    llm = ChatVertexAI(model="gemini-2.5-flash", temperature=0)
    embeddings_model = VertexAIEmbeddings(model_name="text-embedding-004")
    llm_transformer = LLMGraphTransformer(
        llm=llm,
        node_properties=True,
        relationship_properties=True,
        allowed_nodes=["Document", "Concept", "CodeExample"],
        allowed_relationships=["contains", "REFERENCES", "HAS_EXAMPLE", "IN_DOCUMENT"],
    )

    all_urls = YOUTUBE_URLS + URLS
    for url in YOUTUBE_URLS:
        print(f"Processing {url}...")

        try:
            if document_exists(database, url):
                print(f"   🔪 Document {url} already exists in Spanner. Skipping.")
                continue
        except Exception as e:
            print(f" 🔪 An error occurred while checking for document existence for {url}: {e}")
            continue


        print(f"Scraping {url}...")
        if "youtube.com" in url:
            content = get_youtube_transcript(url)
        else:
            content = scrape_documentation(url)

        if "Error fetching" in content or "Content not found" in content:
            print(content)
            continue

        # Create a Document object
        doc = Document(page_content=content, metadata={"source": url})

        # Split the document into smaller chunks
        split_docs = text_splitter.split_documents([doc])

        if not split_docs:
            print(f"No content scraped from {url}. Skipping.")
            continue

        print(f"  🦄 Successfully scraped and split into {len(split_docs)} chunks.")

        print("Transforming document to graph...")
        try:
            graph_documents = llm_transformer.convert_to_graph_documents(split_docs)
            print(f" 🦄  Successfully transformed document to {len(graph_documents)} graph documents.")

            print(" 🦄  Generating embeddings for nodes and saving to graph...")
            for graph_doc in graph_documents:
                for node in graph_doc.nodes:
                    if "limit" in node.properties:
                        del node.properties["limit"]
                    if "function" in node.properties:
                        del node.properties["function"]

                    node_text = f"Node ID: {node.id}, Type: {node.type}"
                    if node.properties:
                        node_text += ", Properties: " + ", ".join(f"{k}: {v}" for k, v in node.properties.items())
                    embedding = embeddings_model.embed_query(node_text)
                    node.properties["embedding"] = embedding

                for rel in graph_doc.relationships:
                    if "limit" in rel.properties:
                        del rel.properties["limit"]
                    if "function" in rel.properties:
                        del rel.properties["function"]

                print(f"Saving doc {graph_doc}...")
                graph_store.add_graph_documents([graph_doc])
        except Exception as e:
            print(f" 🔪🔪 An error occurred during graph transformation for {url}: {e}")
            continue


        def insert_graph_data(transaction):
            all_nodes = {}
            all_relationships = set()

if __name__ == "__main__":
    main()