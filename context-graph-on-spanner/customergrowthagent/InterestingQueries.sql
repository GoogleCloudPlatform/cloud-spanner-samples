GRAPH MarketingContextGraph
MATCH (c:Customers {customer_id: 'CUST-101'})<-[:AboutCustomer]-(d:Decisions)-[:ResultedIn]->(o:Outcomes)
RETURN 
  d.timestamp AS Date,
  d.type AS Action_Type,
  d.reasoning_text AS Agent_Reasoning,
  o.result AS Outcome,
  o.revenue_impact AS Impact
ORDER BY d.timestamp DESC


GRAPH MarketingContextGraph
MATCH (c:Customers {industry: 'Manufacturing', tier: 'Gold'})<-[:AboutCustomer]-(d:Decisions {signal_type: 'LOW_ADOPTION'})-[:ResultedIn]->(o:Outcomes)
WHERE o.result = 'Renewed' AND d.signal_type = 'LOW_ADOPTION'
RETURN d.timestamp, d.signal_type, d.decision_type, d.reasoning_text, o.revenue_impact
ORDER BY d.timestamp DESC

GRAPH MarketingContextGraph
MATCH (c:Customers {{industry: '{industry}', tier: '{tier}'}})<-[:AboutCustomer]-(d:Decisions {{signal_type: '{signal_type}'}})-[:ResultedIn]->(o:Outcomes)
WHERE o.result = 'Renewed'
RETURN 
  d.timestamp AS Date,
  d.decision_type AS Action_Type,
  d.reasoning_text AS Success_Logic
ORDER BY d.timestamp DESC
LIMIT 3

GRAPH MarketingContextGraph
MATCH (c:Customers)-[:AboutCustomer]-(d:Decisions)-[:ResultedIn]-(o:Outcomes)
RETURN 
  c.customer_id AS Customer, 
  c.tier AS Tier, 
  d.type AS DecisionType, 
  d.reasoning_text AS AgentReasoning, 
  o.result AS Status, 
  o.revenue_impact AS Revenue
ORDER BY c.customer_id, d.timestamp DESC;

--To review data inserted
GRAPH MarketingContextGraph
MATCH (c:Customers)-[:AboutCustomer]-(d:Decisions)-[:ResultedIn]-(o:Outcomes)
RETURN 
  c.customer_id AS Customer, 
  c.tier AS Tier, 
  d.type AS DecisionType, 
  d.reasoning_text AS AgentReasoning, 
  o.result AS Status, 
  o.revenue_impact AS Revenue
ORDER BY c.customer_id, d.timestamp DESC;

SELECT 
    c.industry, 
    c.tier, 
    d.signal_type 
FROM Customers AS c
JOIN Decisions AS d ON c.customer_id = d.customer_id
--WHERE c.customer_id = @cid
ORDER BY d.timestamp DESC
LIMIT 1

--SupportContextGraph
GRAPH SupportContextGraph
MATCH (c:Customers {customer_id: 'CUST-001'})<-[:AboutCustomer]-(d:Decisions)
-- Step 2: Traverse to the outcome to see the historical "Why"
MATCH (d)-[:ResultedIn]->(o:Outcomes)
-- Step 3: Check the governing policy
MATCH (d)-[:FollowedPolicy]->(p:Policies)
RETURN 
  d.timestamp AS Date,
  d.type AS Action_Taken,
  d.reasoning_text AS AI_Reasoning,
  o.result AS Final_Result,
  o.revenue_impact AS MRR_Impact
ORDER BY d.timestamp ASC

/*
To show the broader impact of a Context Graph beyond a single customer, we use Community Detection and Similarity Clustering.For an ISV, this is the "Macro-Context"—it proves that the failure of the 50% discount isn't just a one-off fluke with Customer A, but a systemic pattern across a specific "Community" of customers.The "Community of Failure" GQL QueryThis query uses the Spanner Graph to find all customers in the "Manufacturing" industry who were given a high discount and still churned. It groups them by the Reasoning used in the decision to show a "Failure Cluster.
*/
GRAPH SupportContextGraph
MATCH (ind:Industry {name: 'Manufacturing'})<-[:InIndustry]-(c:Customers)
MATCH (c)<-[:AboutCustomer]-(d:Decisions {type: 'Retention_Offer'})
MATCH (d)-[:ResultedIn]->(o:Outcomes {result: 'Churned'})
WHERE d.amount >= 0.40
RETURN 
  d.reasoning_text AS Failed_Logic,
  COUNT(c) AS Customer_Count,
  SUM(o.revenue_impact) AS Total_Loss
ORDER BY Customer_Count DESC
/*
Why this is a "Spanner Power Move" for ISVs:Macro-Context Reasoning: In your demo, the AI Agent can now say: "I am rejecting the 50% discount because our Context Graph shows this logic has failed 15 times in the Manufacturing sector, resulting in $75,000 in lost revenue over the last year."Pattern Discovery: ISVs like HubSpot or Pega can use this to identify "Bad Policies." If the graph shows a cluster of failures for a specific policy, they can automatically update the Policy node to "Inactive" or "Restrictive."Cross-Tenant Intelligence (Optional): For multi-tenant ISVs, Spanner's scale allows them to see these patterns across their entire fleet of customers (anonymized, of course) to provide "Benchmark-as-a-Service" insights.Final Summary for your Demo DeckDemo ComponentWhat it proves to the ISVThe State ClockSpanner holds the "Truth" (Customer/SLA data).The Event ClockThe Context Graph holds the "Wisdom" (Decision/Outcome history).The Policy GuardrailThe ADK enforces the "Rules" (Governance).The Community QuerySpanner scales the "Insights" (Systemic patterns).*/
  
--Insert scripts if/when needed
-- 1. Insert the 'Success Twin' Customer
INSERT OR UPDATE Customers (customer_id, industry, tier, mrr) 
VALUES ('CUST-105', 'Manufacturing', 'Gold', 25000);

-- 2. Insert a successful Decision for the Twin
INSERT Decisions (decision_id, type, reasoning_text, timestamp)
VALUES ('DEC-105-SUCCESS', 'Strategic Advisory', 'Pivoted from price to value-based automation workshop.', '2025-02-10 10:00:00');

-- 3. Insert the positive Outcome
INSERT Outcomes (outcome_id, result, revenue_impact)
VALUES ('OUT-105-SUCCESS', 'Renewed', 25000);

-- 4. Create the Edges
INSERT AboutCustomer (decision_id, customer_id) VALUES ('DEC-105-SUCCESS', 'CUST-105');
INSERT ResultedIn (decision_id, outcome_id) VALUES ('DEC-105-SUCCESS', 'OUT-105-SUCCESS');

-- Create a new Manufacturing success story (CUST-105)
INSERT OR UPDATE Customers (customer_id, industry, tier, mrr) 
VALUES ('CUST-105', 'Manufacturing', 'Gold', 25000);

-- Codify the successful 'Strategic Advisory' decision
INSERT OR UPDATE Decisions (decision_id, type, reasoning_text, timestamp)
VALUES ('DEC-105-SUCCESS', 'Strategic Advisory', 'Conducted a Routing Automation Advisory Workshop to solve complexity friction.', '2025-05-10 10:00:00');

-- Codify the 'Renewed' outcome
INSERT OR UPDATE Outcomes (outcome_id, result, revenue_impact)
VALUES ('OUT-105-SUCCESS', 'Renewed', 25000);

-- Link them
INSERT OR UPDATE AboutCustomer (decision_id, customer_id) VALUES ('DEC-105-SUCCESS', 'CUST-105');
INSERT OR UPDATE ResultedIn (decision_id, outcome_id) VALUES ('DEC-105-SUCCESS', 'OUT-105-SUCCESS');

select *
from Decisions
