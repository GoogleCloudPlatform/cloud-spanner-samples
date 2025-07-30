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
