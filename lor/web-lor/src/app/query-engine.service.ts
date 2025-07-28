import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom, Observable, timeout } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class QueryEngineService {

  constructor(private http: HttpClient) { }

  res:any;

  async run(inputParams:any) {
    const res = await firstValueFrom(this.http.post('/api/run', inputParams).pipe(timeout(10000)));
    return res;
  }
}

