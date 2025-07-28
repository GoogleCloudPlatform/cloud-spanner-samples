import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GraphVisnetworkComponent } from './graph-visnetwork.component';

describe('GraphVisnetworkComponent', () => {
  let component: GraphVisnetworkComponent;
  let fixture: ComponentFixture<GraphVisnetworkComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [GraphVisnetworkComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(GraphVisnetworkComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
