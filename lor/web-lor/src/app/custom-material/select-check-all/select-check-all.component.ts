import { Component, Input, ViewEncapsulation } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MatCheckboxChange } from '@angular/material/checkbox';
import { ChkItem } from "../../chkitem";

@Component({
  selector: 'app-select-check-all',
  templateUrl: "./select-check-all.component.html",
  styleUrls: ['./select-check-all.component.css'],
  encapsulation: ViewEncapsulation.None
})
export class SelectCheckAllComponent {
  @Input() model: FormControl = new FormControl();
  @Input() values = ([] as ChkItem[]);
  @Input() text = 'Select All'; 

  isChecked(): boolean {
    return this.model.value && this.values.length
      && this.model.value.length === this.values.length;
  }

  isIndeterminate(): boolean {
    return this.model.value && this.values.length && this.model.value.length
      && this.model.value.length < this.values.length;
  }

  toggleSelection(change: MatCheckboxChange): void {
    if (change.checked) {
      let selvalues = [];
      for (let i = 0; i < this.values.length; i++) {
        selvalues.push(this.values[i].id)
      }
      //this.model.setValue(this.values);
      this.model.setValue(selvalues);
    } else {
      this.model.setValue([]);
    }
  }
}
