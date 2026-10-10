# Copyright 2026 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Builds data/*.sql from tools/dataset.py and the profile photos.

Bio and photo embeddings come from gemini-embedding-2 (128 dimensions, the
same vector space for text and images). They are cached in
tools/embeddings.json, so Vertex AI is only called when bios or photos change.
setup.sh never calls this script: it loads the generated SQL.

Usage:
  pip install google-genai
  python3 tools/build_data.py --project YOUR_PROJECT [--account YOUR_ACCOUNT]
"""

import argparse
import hashlib
import json
import os
import subprocess
import sys

import dataset

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CACHE = os.path.join(ROOT, "tools", "embeddings.json")
MODEL = "gemini-embedding-2"
DIMS = 128
PEOPLE_PER_INSERT = 8  # Keeps every statement far below the 128 KB arg limit.
LICENSE_HEADER = """\
-- Copyright 2026 Google LLC
--
-- Licensed under the Apache License, Version 2.0 (the "License");
-- you may not use this file except in compliance with the License.
-- You may obtain a copy of the License at
--
--     https://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

"""


def sql_str(value):
    escaped = value.replace("\\", "\\\\").replace("'", "\\'")
    if ";" in escaped:
        raise ValueError("Semicolons break setup.sh splitting: " + value)
    return "'" + escaped + "'"


def sql_vec(vec):
    return "ARRAY<FLOAT32>[" + ", ".join("%.6g" % x for x in vec) + "]"


def embed_all(project, account):
    cache = json.load(open(CACHE)) if os.path.exists(CACHE) else {}
    todo = []
    for pid, _, _, _, bio in dataset.PEOPLE:
        path = os.path.join(ROOT, "profile_pics",
                            dataset.photo_stem(pid) + ".jpg")
        sha = hashlib.sha256(open(path, "rb").read()).hexdigest()
        entry = cache.get(pid, {})
        if entry.get("bio_text") != bio or entry.get("photo_sha") != sha:
            todo.append((pid, bio, path, sha))
    if not todo:
        return cache

    from google import genai
    from google.genai import types
    from google.oauth2.credentials import Credentials
    cmd = ["gcloud", "auth", "print-access-token"]
    if account:
        cmd.append("--account=" + account)
    token = subprocess.run(cmd, capture_output=True, text=True,
                           check=True).stdout.strip()
    client = genai.Client(vertexai=True, project=project, location="global",
                          credentials=Credentials(token))
    config = types.EmbedContentConfig(output_dimensionality=DIMS)

    def embed(part):
        resp = client.models.embed_content(
            model=MODEL, contents=[types.Content(parts=[part])], config=config)
        return [float(x) for x in resp.embeddings[0].values]

    for pid, bio, path, sha in todo:
        print("Embedding", pid, file=sys.stderr)
        image = types.Part.from_bytes(data=open(path, "rb").read(),
                                      mime_type="image/jpeg")
        cache[pid] = {"bio_text": bio, "photo_sha": sha,
                      "bio": embed(types.Part.from_text(text=bio)),
                      "photo": embed(image)}
    with open(CACHE, "w") as f:
        json.dump(cache, f)
    return cache


def people_sql(cache):
    rows = []
    for pid, name, segment, signup, bio in dataset.PEOPLE:
        url = dataset.PHOTO_URL.format(dataset.photo_stem(pid))
        rows.append("  (%s, %s, %s, %s, %s, %s, %s, TIMESTAMP %s)" % (
            sql_str(name), sql_str(pid), sql_str(segment), sql_str(bio),
            sql_str(url), sql_vec(cache[pid]["bio"]),
            sql_vec(cache[pid]["photo"]), sql_str(signup)))
    header = ("INSERT INTO People (Name, PersonId, Segment, ProfileBio, "
              "ProfilePhotoUrl, ProfileBioEmbedding, ProfilePhotoEmbedding, "
              "SignupTime) VALUES\n")
    step = PEOPLE_PER_INSERT
    return "".join(header + ",\n".join(rows[i:i + step]) + ";\n\n"
                   for i in range(0, len(rows), step))


def transfers_sql():
    # Transfers is interleaved in People, so the sender column is PersonId.
    rows = ["  (%s, %s, %s, NUMERIC %s, %s, TIMESTAMP %s)" % (
        sql_str(s), sql_str(tid), sql_str(r), sql_str(a), sql_str(m),
        sql_str(ts)) for tid, s, r, a, m, ts in dataset.transfer_rows()]
    return ("INSERT INTO Transfers (PersonId, TransferId, ReceiverId, Amount, "
            "Memo, CreatedAt) VALUES\n" + ",\n".join(rows) + ";\n")


def signals_sql():
    rows = ["  (%s, %s, %s, %s, TIMESTAMP %s)" % (
        sql_str(sid), sql_str(p), sql_str(t), sql_str(d), sql_str(ts))
        for sid, p, t, d, ts in dataset.signal_rows()]
    return ("INSERT INTO AccountSignals (SignalId, PersonId, SignalType, "
            "Details, EventTime) VALUES\n" + ",\n".join(rows) + ";\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--project", required=True,
                        help="Project used to call Vertex AI.")
    parser.add_argument("--account", default="",
                        help="Optional gcloud account for the access token.")
    args = parser.parse_args()

    cache = embed_all(args.project, args.account)
    banner = LICENSE_HEADER + (
        "-- Generated by tools/build_data.py from tools/dataset.py. "
        "Do not edit by hand.\n\n")
    os.makedirs(os.path.join(ROOT, "data"), exist_ok=True)
    for name, body in [("01_people.sql", people_sql(cache)),
                       ("02_transfers.sql", transfers_sql()),
                       ("03_signals.sql", signals_sql())]:
        with open(os.path.join(ROOT, "data", name), "w") as f:
            f.write(banner + body)
        print("Wrote data/" + name, file=sys.stderr)


if __name__ == "__main__":
    main()
