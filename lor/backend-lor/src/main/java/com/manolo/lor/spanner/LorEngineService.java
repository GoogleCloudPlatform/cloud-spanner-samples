package com.manolo.lor.spanner;

import java.util.HashSet;

import org.springframework.stereotype.Service;

import com.google.cloud.spanner.DatabaseClient;
import com.google.cloud.spanner.DatabaseId;
import com.google.cloud.spanner.LazySpannerInitializer;
import com.google.cloud.spanner.ResultSet;
import com.google.cloud.spanner.SpannerOptions;
import com.google.cloud.spanner.Statement;

@Service
public class LorEngineService {

    public static final int TOTAL_CHARACTERS = 20;
    public static final int TOTAL_KINDS = 8;
    public static final int TOTAL_PLACES = 24;

    public static final int MIN_STRENGTH = 1;
    public static final int MAX_STRENGTH = 533;

    private static final String INSTANCE_ID = System.getenv("INSTANCE_ID");
    private static final String DATABASE_ID = System.getenv("DATABASE_ID");

    private static final LazySpannerInitializer SPANNER_INITIALIZER = new LazySpannerInitializer();

    DatabaseClient getClient() throws Throwable {
        return SPANNER_INITIALIZER.get().getDatabaseClient(DatabaseId.of(SpannerOptions.getDefaultProjectId(), INSTANCE_ID, DATABASE_ID));
    }

    public LorEngineResponse run(LorEngineRequest req) {
        String query = buildQuery(req);

        LorEngineResponse res = new LorEngineResponse();
        
        res.setQuery(query);

        HashSet<Node> nodeSet = new HashSet<Node>();
        HashSet<Edge> edgeSet = new HashSet<Edge>();
        try {
            DatabaseClient dbClient = getClient();
            ResultSet resultSet = dbClient.singleUse().executeQuery(Statement.of(query));
            while (resultSet.next()) {
                String from_id = resultSet.getString(0);
                String from_label = resultSet.getString(1);
                String from_subtype = resultSet.getString(2);
                String to_id = resultSet.getString(3);
                String to_label = resultSet.getString(4);
                String to_subtype = resultSet.getString(5);
                
                Node n_from = new Node(from_id,from_label, from_subtype);
                Node n_to = new Node(to_id, to_label, to_subtype);
                nodeSet.add(n_from);
                nodeSet.add(n_to);

                Edge edge = new Edge(from_id, to_id);
                edgeSet.add(edge);
            }
            for(Node n : nodeSet)
                res.addNode(n);
            for(Edge e : edgeSet)
                res.addEdge(e);
        } 
        catch (Throwable e) {
            e.printStackTrace();
        }
        return res;
    }
    
    private String buildQuery(LorEngineRequest req) {
        StringBuffer res = new StringBuffer();
        res.append("GRAPH LoRGraph \n");
        res.append("MATCH  (p1:Persons)-[ref:Reference]->(p2:Persons) \n");
        boolean isWhere = false;
        // REFERENCES
        if(req.getMinStrength() > MIN_STRENGTH || req.getMaxStrength() < MAX_STRENGTH){
            isWhere = true;
            res.append("WHERE ref.times BETWEEN " + req.getMinStrength()  + " and " + req.getMaxStrength()  + "\n");
        }
        // CHARACTERS
        if(req.getCharacters().length < TOTAL_CHARACTERS){
            StringBuffer sbP1 = new StringBuffer();
            StringBuffer sbP2 = new StringBuffer();
            if(!isWhere){
                sbP1.append("WHERE ");
                isWhere = true;
            }
            else{
                sbP1.append("AND ");
            }

            sbP1.append("(p1.id IN (");
            sbP2.append("OR p2.id IN (");
            String characters[] = req.getCharacters();
            for(int i = 0; i < characters.length; i++){
                sbP1.append("'" + characters[i] + "'");
                sbP2.append("'" + characters[i] + "'");
                if(i < (characters.length-1)){
                    sbP1.append(", ");
                    sbP2.append(", ");
                }
            }
            sbP1.append(")");
            sbP2.append("))");
            res.append(sbP1.toString() + " \n");
            res.append(sbP2.toString() + " \n");
        }
        // KINDS
        if(req.getKinds().length < TOTAL_KINDS){
            StringBuffer sbP1 = new StringBuffer();
            StringBuffer sbP2 = new StringBuffer();
            if(!isWhere){
                sbP1.append("WHERE ");
                isWhere = true;
            }
            else{
                sbP1.append("AND ");
            }
            sbP1.append(" p1.subtype IN (");
            sbP2.append("AND p2.subtype IN (");
            String kinds[] = req.getKinds();
            for(int i = 0; i < kinds.length; i++){
                sbP1.append("'" + kinds[i] + "'");
                sbP2.append("'" + kinds[i] + "'");
                if(i < (kinds.length-1)){
                    sbP1.append(", ");
                    sbP2.append(", ");
                }
            }
            sbP1.append(")");
            sbP2.append(")");
            res.append(sbP1.toString() + " \n");
            res.append(sbP2.toString() + " \n");
        }
        // PLACES
        if(req.getPlaces().length < TOTAL_PLACES){
            res.append("RETURN p1,p2 \n");
            res.append("NEXT \n");
            res.append("MATCH (p1:Persons)-[:PlacesPersons]->(place:Places) \n");
            res.append("MATCH (p2:Persons)-[:PlacesPersons]->(place:Places) \n");
            res.append("WHERE place.id IN (");
            String places[] = req.getPlaces();
            for(int i = 0; i < places.length; i++){
                res.append("'" + places[i] + "'");
                if(i < (places.length-1)){
                    res.append(", ");
                }
            }
            res.append(") \n");
        }
        res.append("RETURN \n");
        res.append("p1.id as from_id, p1.label as from_label, p1.subtype as from_subtype,\n");
        res.append("p2.id as to_id, p2.label as to_label, p2.subtype as to_subtype");
        return res.toString();
    }
    
}
