/**
 * @license
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
