import { Component } from '@angular/core';

@Component({
  selector: 'app-graph-query',
  templateUrl: './graph-query.component.html',
  styleUrl: './graph-query.component.css'
})
export class GraphQueryComponent {
  query: string="SELECT * FROM TEST";
  constructor() { }

  setQuery(q: string){
    this.query = q;
  }
}
