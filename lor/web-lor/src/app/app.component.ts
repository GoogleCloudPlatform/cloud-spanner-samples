import { Component, ViewChild } from '@angular/core';
import { GraphVisnetworkComponent } from './graph-visnetwork/graph-visnetwork.component';
import { GraphQueryComponent } from './graph-query/graph-query.component';
import { QueryEngineService} from './query-engine.service'
import { firstValueFrom, timeout } from 'rxjs';
import { GraphInputComponent } from './graph-input/graph-input.component';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrl: './app.component.css',
})
export class AppComponent {
  
  @ViewChild('gDraw') gDraw!: GraphVisnetworkComponent;
  @ViewChild('gInput') gInput!: GraphInputComponent;
  
  graphData: any;

  constructor(private queryEngine: QueryEngineService) {
  }

  async runquery(inputParams: any) {
    this.gDraw.isWaitingQuery = true;
    this.graphData = await this.queryEngine.run(inputParams);
    this.gInput.query = this.graphData.query;
    this.gDraw.isWaitingQuery = false;
    this.gDraw.drawGraph(this.graphData.nodes, this.graphData.edges);
  }
}
