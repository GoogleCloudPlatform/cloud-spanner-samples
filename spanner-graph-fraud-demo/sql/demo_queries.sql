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

-- Cymbal Bank fraud demo: the queries to run in Spanner Studio, in order.
-- Select one statement at a time and click Run (or press Ctrl/Cmd+Enter).
-- Expected results are noted above each query.


-- ---------------------------------------------------------------------------
-- 1. Start from the complaints: full-text search inside a graph query.
--    SEARCH() uses the search index on the transfer memos.
--    Expect: 3 transfers. Eleanor Whitfield -> Will Scarlett (12500) and
--    Ana Little (9800), Aiko Tanaka -> Sam Stutely (9500). The other
--    deposits used different words, so keyword search misses them.
-- ---------------------------------------------------------------------------
GRAPH PaymentsNetwork
MATCH (sender:Person)-[t:Transfers]->(receiver:Person)
WHERE SEARCH(t.MemoTokens, 'investment pool')
RETURN sender.Name AS sender, receiver.Name AS receiver,
       t.Memo AS memo, t.Amount AS amount
ORDER BY amount DESC;


-- ---------------------------------------------------------------------------
-- 2. Find pass-through accounts with no starting point.
--    NEXT chains query steps; LET and FILTER work on the results so far.
--    Expect: exactly 10 accounts, each forwarding 92% of what it received.
-- ---------------------------------------------------------------------------
GRAPH PaymentsNetwork
MATCH (:Person)-[inflow:Transfers]->(acct:Person)
RETURN acct, SUM(inflow.Amount) AS total_in
GROUP BY acct
NEXT
MATCH (acct)-[outflow:Transfers]->(:Person)
RETURN acct, total_in, SUM(outflow.Amount) AS total_out
GROUP BY acct, total_in
NEXT
LET pass_through = total_out / total_in
FILTER pass_through > 0.8 AND total_in >= 5000
RETURN acct.Name AS account, total_in, total_out,
       ROUND(pass_through, 2) AS pass_through
ORDER BY total_in DESC;


-- ---------------------------------------------------------------------------
-- 3. Follow the money (GQL, 2 hops).
--    Big deposits that are passed on within an hour lead to one person.
--    Expect: Robin Locksley | 8 victims | 10 mules | 115100 | 105892
-- ---------------------------------------------------------------------------
GRAPH PaymentsNetwork
MATCH (victim:Person)-[deposit:Transfers]->(mule:Person)-[payout:Transfers]->(boss:Person)
WHERE deposit.Amount >= 5000
  AND payout.CreatedAt BETWEEN deposit.CreatedAt
                           AND TIMESTAMP_ADD(deposit.CreatedAt, INTERVAL 1 HOUR)
RETURN boss.Name AS ringleader,
       COUNT(DISTINCT victim.Name) AS victims,
       COUNT(DISTINCT mule.Name) AS mules,
       SUM(deposit.Amount) AS stolen,
       SUM(payout.Amount) AS collected
GROUP BY ringleader
ORDER BY collected DESC;


-- ---------------------------------------------------------------------------
-- 4. See the ring (open the Graph tab of the results).
--    Expect: 10 paths. Victims on the outside, 10 mules, Robin in the middle.
-- ---------------------------------------------------------------------------
GRAPH PaymentsNetwork
MATCH p = (victim:Person)-[deposit:Transfers]->(mule:Person)-[payout:Transfers]->(boss:Person {Name: 'Robin Locksley'})
WHERE deposit.Amount >= 5000
RETURN SAFE_TO_JSON(p) AS money_trail;


-- ---------------------------------------------------------------------------
-- 5a. Rank everyone by money flow with PageRank (graph algorithm, Preview).
--     It runs on Data Boost compute, so the payments workload isn't touched,
--     and writes each score back into the People.PageRank column.
--     Expect: no rows, just success. The first run can take up to 90 seconds
--     while the algorithm compute starts.
-- ---------------------------------------------------------------------------
EXPORT DATA OPTIONS (
  format = 'CLOUD_SPANNER',
  table = 'People',
  write_mode = 'update_ignore_all'
) AS
GRAPH PaymentsNetwork
CALL PageRank(node_labels => ['Person'], edge_labels => ['Transfers'],
              edge_weight_property => 'Amount')
YIELD node, score
RETURN node.PersonId AS PersonId, score AS PageRank;


-- ---------------------------------------------------------------------------
-- 5b. The scores are now an ordinary node property.
--     Expect: Robin Locksley 0.227, far ahead of Marian Fitzwalter 0.122
--     (Robin sends her money), Elena Petrova 0.089 and Owen Hughes 0.065.
-- ---------------------------------------------------------------------------
GRAPH PaymentsNetwork
MATCH (p:Person)
WHERE p.PageRank IS NOT NULL
RETURN p.Name AS person, ROUND(p.PageRank, 3) AS page_rank
ORDER BY page_rank DESC
LIMIT 5;


-- ---------------------------------------------------------------------------
-- 6. Is Robin's partner involved? Shortest path from a victim to Marian,
--    up to 6 hops in either direction (open the Graph tab).
--    Expect: 3 hops: Harold Chen - Alan Dale - Robin Locksley - Marian
--    Fitzwalter. She's connected to the money only through Robin.
-- ---------------------------------------------------------------------------
GRAPH PaymentsNetwork
MATCH p = ANY SHORTEST (victim:Person {Name: 'Harold Chen'})-[t:Transfers]-{1,6}(partner:Person {Name: 'Marian Fitzwalter'})
RETURN SAFE_TO_JSON(p) AS connection, PATH_LENGTH(p) AS hops;


-- ---------------------------------------------------------------------------
-- 7a. Risk signals on their own are noisy.
--     Expect: 17 signals (8 IMPOSSIBLE_TRAVEL, 6 SHARED_DEVICE, 3 NEW_DEVICE).
-- ---------------------------------------------------------------------------
SELECT SignalType, COUNT(*) AS signals
FROM AccountSignals
GROUP BY SignalType
ORDER BY signals DESC;


-- ---------------------------------------------------------------------------
-- 7b. Graph + relational in one query: signals on people who paid Robin.
--     GRAPH_TABLE turns a graph pattern into a table you can JOIN.
--     Expect: 12 rows, all 10 mules, zero false positives. Robin's partner
--     and roommate also paid him, but have no signals, so they drop out.
-- ---------------------------------------------------------------------------
SELECT associate.name AS associate, s.SignalType, s.Details, s.EventTime
FROM GRAPH_TABLE(
  PaymentsNetwork
  MATCH (person:Person)-[:Transfers]->(boss:Person {Name: 'Robin Locksley'})
  RETURN DISTINCT person.PersonId AS person_id, person.Name AS name
) AS associate
JOIN AccountSignals AS s ON s.PersonId = associate.person_id
ORDER BY s.EventTime;


-- ---------------------------------------------------------------------------
-- 8. Vector search: whose profile bio sounds like Robin's?
--    Bios were embedded with gemini-embedding-2 (128 dimensions).
--    Expect: 12 ring accounts first (distance <= 0.12), including Gwen
--    Gamwell and Kai Sherwood who never moved money. Next is 0.196.
-- ---------------------------------------------------------------------------
SELECT p.Name, p.ProfileBio,
       ROUND(COSINE_DISTANCE(p.ProfileBioEmbedding, robin.ProfileBioEmbedding), 3) AS bio_distance
FROM People AS p, People AS robin
WHERE robin.Name = 'Robin Locksley'
  AND p.PersonId != robin.PersonId
ORDER BY bio_distance
LIMIT 15;


-- ---------------------------------------------------------------------------
-- 9. Graph + vector in one GQL query: everyone who paid Robin, ranked by how
--    much their profile photo looks like his.
--    Expect: the 10 mules at photo distance 0.05 to 0.16, then Marian
--    Fitzwalter (0.212) and Owen Hughes (0.230), his genuine contacts.
-- ---------------------------------------------------------------------------
GRAPH PaymentsNetwork
MATCH (associate:Person)-[t:Transfers]->(boss:Person {Name: 'Robin Locksley'})
RETURN associate.Name AS associate,
       t.Amount AS sent_to_robin,
       ROUND(COSINE_DISTANCE(associate.ProfileBioEmbedding, boss.ProfileBioEmbedding), 3) AS bio_distance,
       ROUND(COSINE_DISTANCE(associate.ProfilePhotoEmbedding, boss.ProfilePhotoEmbedding), 3) AS photo_distance,
       associate.ProfilePhotoUrl AS photo
ORDER BY photo_distance;


-- ---------------------------------------------------------------------------
-- 10. Catch the sleepers: accounts with no transfers at all whose photo
--     looks like Robin's.
--     Expect: Kai Sherwood (0.098) and Gwen Gamwell (0.113), both created in
--     the same 13-minute signup burst as the mules. Arjun Mehta and Lena
--     Hoffmann are genuinely new customers (0.200 and 0.246).
-- ---------------------------------------------------------------------------
GRAPH PaymentsNetwork
MATCH (suspect:Person), (boss:Person {Name: 'Robin Locksley'})
WHERE NOT EXISTS {
  MATCH (someone:Person)-[:Transfers]-(:Person)
  WHERE someone = suspect
}
RETURN suspect.Name AS suspect,
       suspect.SignupTime AS signed_up,
       ROUND(COSINE_DISTANCE(suspect.ProfilePhotoEmbedding, boss.ProfilePhotoEmbedding), 3) AS photo_distance,
       ROUND(COSINE_DISTANCE(suspect.ProfileBioEmbedding, boss.ProfileBioEmbedding), 3) AS bio_distance,
       suspect.ProfilePhotoUrl AS photo
ORDER BY photo_distance;


-- ---------------------------------------------------------------------------
-- 11. Let Gemini draft the case summary, straight from SQL. ML.PREDICT sends
--     the money trail from GRAPH_TABLE to GeminiModel (created by setup.sh).
--     Expect: about 3 sentences naming Robin Locksley, the 10 intermediary
--     accounts and the 92% forwarding. The wording changes on every run.
-- ---------------------------------------------------------------------------
SELECT content AS case_summary
FROM ML.PREDICT(MODEL GeminiModel, (
  SELECT CONCAT(
           'You are a bank fraud analyst. In 3 short sentences, summarize ',
           'this case for a suspicious activity report. Transfers: ',
           STRING_AGG(CONCAT(victim, ' sent ', CAST(amount AS STRING),
                             ' to ', mule, ' who forwarded ',
                             CAST(payout AS STRING), ' to ', boss), '. ')) AS prompt
  FROM GRAPH_TABLE(PaymentsNetwork
    MATCH (v:Person)-[d:Transfers]->(m:Person)-[o:Transfers]->(b:Person)
    WHERE d.Amount >= 5000
      AND o.CreatedAt BETWEEN d.CreatedAt
                          AND TIMESTAMP_ADD(d.CreatedAt, INTERVAL 1 HOUR)
    RETURN v.Name AS victim, d.Amount AS amount, m.Name AS mule,
           o.Amount AS payout, b.Name AS boss)));


-- ---------------------------------------------------------------------------
-- 12. Act on it in the same database. One transactional UPDATE freezes
--     every account whose bio AND photo both look like Robin's.
--     Expect: 13 rows modified (Robin, 10 mules, 2 sleepers).
-- ---------------------------------------------------------------------------
UPDATE People
SET Status = 'FROZEN'
WHERE COSINE_DISTANCE(ProfileBioEmbedding,
        (SELECT ProfileBioEmbedding FROM People WHERE Name = 'Robin Locksley')) < 0.17
  AND COSINE_DISTANCE(ProfilePhotoEmbedding,
        (SELECT ProfilePhotoEmbedding FROM People WHERE Name = 'Robin Locksley')) < 0.17
THEN RETURN Name, Status;


-- ---------------------------------------------------------------------------
-- Reset after a rehearsal: unfreezes everyone (undoes 12) and clears the
-- PageRank scores (undoes 5a).
-- ---------------------------------------------------------------------------
UPDATE People SET Status = 'ACTIVE', PageRank = NULL WHERE TRUE;
