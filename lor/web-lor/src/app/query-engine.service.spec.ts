import { TestBed } from '@angular/core/testing';

import { QueryEngineService } from './query-engine.service';

describe('QueryEngineService', () => {
  let service: QueryEngineService;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    service = TestBed.inject(QueryEngineService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
