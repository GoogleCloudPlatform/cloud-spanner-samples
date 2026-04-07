--When usage drops for a Gold Tier client, what intervention has the highest probability of a full-price renewal?
GRAPH MarketingContextGraph
MATCH (c:Customers {tier: 'Gold'})<-[:AboutCustomer]-(d:Decisions {signal_type: 'LOW_ADOPTION'})-[:ResultedIn]->(o:Outcomes)
WHERE o.result = 'Renewed'
RETURN 
  d.decision_type AS Intervention, 
  COUNT(*) AS Success_Count,
  AVG(o.revenue_impact) AS Avg_Revenue_Retained
GROUP BY Intervention
ORDER BY Success_Count DESC
LIMIT 1;

--Does a recommendation (e.g., deep discount) actually prevent churn, or does it simply delay it?
GRAPH MarketingContextGraph
MATCH (c:Customers)<-[:AboutCustomer]-(d:Decisions {decision_type: 'Discount'})-[:ResultedIn]->(o:Outcomes)
/* We check the results of those who received a discount */
RETURN 
  o.result AS Final_Status,
  COUNT(c.customer_id) AS Customer_Count,
  AVG(o.revenue_impact) AS Total_Financial_Impact
GROUP BY Final_Status;
