import { Component, ElementRef, ViewChild } from '@angular/core';
import { DataSet } from 'vis-data';
import { Network } from 'vis-network';

@Component({
  selector: 'app-graph-visnetwork',
  templateUrl: './graph-visnetwork.component.html',
  styleUrl: './graph-visnetwork.component.css'
})
export class GraphVisnetworkComponent {
  @ViewChild('lornetwork', { static: false }) lornetwork!: ElementRef;
  private networkInstance: any;
  isWaitingQuery: boolean = false;
  isWaitingDrawing: boolean = false;
  valueDraw: number = 0;

  constructor() { }

  drawGraph(nodesArr : any, edgesArr : any){

    var nodes = new DataSet<any>(nodesArr);
    var edges = new DataSet<any>(edgesArr);

    const data = { nodes, edges };
    
    var options = {
      physics: {
        barnesHut:{
          gravitationalConstant: -3000,
          springConstant:0.02
        },
      },
      edges: {color: "gray"},
      groups: {
        animal: {color:{background:'#FFA07A'}},
        orcs: {color:{background:'#778899'}},
        hobbit: {color:{background:'#87CEFA'}},
        ents: {color:{background:'#228B22'}},
        men: {color:{background:'#00FF7F'}},
        dwarf: {color:{background:'#FF6347'}},
        ainur: {color:{background:'#DAA520'}},
        elves: {color:{background:'#FFFF00'}}
      }
    };

    this.networkInstance = new Network(this.lornetwork.nativeElement, data, options);
    this.isWaitingDrawing = true;
    this.valueDraw = 0;

    this.networkInstance.on("stabilizationProgress",  (params: { iterations: number; total: number; }) => {
      this.valueDraw = 100 * params.iterations / params.total;
    });
    this.networkInstance.once("stabilizationIterationsDone", () => {
      this.isWaitingDrawing = false;
    });
  }
}
