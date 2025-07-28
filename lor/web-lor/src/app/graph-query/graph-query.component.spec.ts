import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GraphQueryComponent } from './graph-query.component';

describe('GraphQueryComponent', () => {
  let component: GraphQueryComponent;
  let fixture: ComponentFixture<GraphQueryComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [GraphQueryComponent]
    })
    .compileComponents();

    fixture = TestBed.createComponent(GraphQueryComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
