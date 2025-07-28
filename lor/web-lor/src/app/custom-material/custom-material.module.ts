import { NgModule } from '@angular/core';
import { MatSelectModule } from '@angular/material/select';
import { MatCheckboxModule} from '@angular/material/checkbox';
import { SelectCheckAllComponent } from './select-check-all/select-check-all.component';

@NgModule({
  imports: [
    MatSelectModule, MatCheckboxModule
  ],
  exports: [
    MatSelectModule, MatCheckboxModule, SelectCheckAllComponent
  ],
  declarations: [SelectCheckAllComponent]
})
export class CustomMaterialModule {
}