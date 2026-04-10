GRAPH MarketingContextGraph
MATCH (c:Customers {{industry: 'Manufacturing', tier: 'Gold'}})<-[:AboutCustomer]-(d:Decisions {{signal_type: 'LOW_ADOPTION'}})-[:ResultedIn]->(o:Outcomes)
WHERE o.result = 'Renewed'
RETURN 
  d.timestamp AS Date,
  d.decision_type AS Action_Type,
  d.reasoning_text AS Success_Logic
ORDER BY d.timestamp DESC
LIMIT 3
