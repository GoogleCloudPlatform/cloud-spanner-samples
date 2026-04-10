import os
import json
from google.cloud import spanner
from google import genai
from google.genai import types

# --- 1. Configuration ---
PROJECT_ID = "xxxx"
INSTANCE_ID = "graphxx"
DATABASE_ID = "marketinggraph"

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
# Join that directory with the PDF filename
PDF_PATH = os.path.join(SCRIPT_DIR, "Corporate_Retention_Policies.pdf")

os.environ["GOOGLE_CLOUD_PROJECT"] = PROJECT_ID
os.environ["GOOGLE_GENAI_USE_VERTEXAI"] = "True"

os.environ["GOOGLE_CLOUD_SPANNER_ENABLE_METRICS"] = "false"
os.environ["OTEL_SDK_DISABLED"] = "true"

client = genai.Client(vertexai=True, project=PROJECT_ID, location="us-central1")
spanner_client = spanner.Client(project=PROJECT_ID)
database = spanner_client.instance(INSTANCE_ID).database(DATABASE_ID)

# --- 2. The PDF Extractor ---
def extract_policies_from_pdf(file_path):
    """Uses Gemini to parse unstructured PDF text into structured Policy nodes."""
    print(f"📖 Analyzing {file_path} for Corporate Guardrails...")
    
    with open(file_path, "rb") as f:
        pdf_data = f.read()

    prompt = """
    Extract all formal business policies from this document regarding customer retention, 
    discounts, and account management. 
    
    Return a JSON list of objects with these keys:
    - policy_id: A short code (e.g., 'POL-101').
    - name: A clear title for the policy.
    - definition: The full rule text.
    - is_active: Always set to true for extracted rules.
    """

    response = client.models.generate_content(
        model="gemini-2.0-flash",
        contents=[
            types.Part.from_bytes(data=pdf_data, mime_type="application/pdf"),
            prompt
        ],
        config=types.GenerateContentConfig(response_mime_type="application/json")
    )
    
    return json.loads(response.text)

# --- 3. The Spanner Upsert ---
def sync_to_spanner(policies):
    def _upsert(transaction):
        for p in policies:
            transaction.execute_update(
                "INSERT OR UPDATE Policies (policy_id, name, rule_definition, is_active) "
                "VALUES (@id, @name, @def, @active)",
                params={
                    "id": p["policy_id"],
                    "name": p["name"],
                    "def": p["definition"],
                    "active": p["is_active"]
                }
            )
    database.run_in_transaction(_upsert)
    print(f"✅ Ingested {len(policies)} policies into the Governance Layer.")

if __name__ == "__main__":
    extracted_data = extract_policies_from_pdf(PDF_PATH)
    sync_to_spanner(extracted_data)
