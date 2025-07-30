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
