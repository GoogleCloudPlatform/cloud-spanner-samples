package com.manolo.lor.spanner;

import java.util.ArrayList;
import java.util.List;

public class LorEngineResponse {

    private List<Node> nodes;
    private List<Edge> edges;
    private String query;
    
    public List<Node> getNodes() {
        return nodes;
    }

    public void setNodes(List<Node> nodes) {
        this.nodes = nodes;
    }

    public List<Edge> getEdges() {
        return edges;
    }

    public void setEdges(List<Edge> edges) {
        this.edges = edges;
    }

    public LorEngineResponse() {
        nodes = new ArrayList<Node>();
        edges = new ArrayList<Edge>();
    }
    
    public void addNode(Node n){
        nodes.add(n);
    }

    public void addEdge(Edge e){
        edges.add(e);
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    

    

}
