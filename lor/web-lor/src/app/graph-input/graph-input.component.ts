import { Component, OnInit, Output, EventEmitter, inject, model, ElementRef} from '@angular/core';
import { FormGroup, FormControl } from "@angular/forms";
import { ChkItem } from "../chkitem";
import { MAT_DIALOG_DATA, MatDialog, MatDialogActions, MatDialogContent, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';

@Component({
  selector: 'app-graph-input',
  templateUrl: './graph-input.component.html',
  styleUrl: './graph-input.component.css',
})

export class GraphInputComponent implements OnInit {
  @Output() queryLaunch = new EventEmitter<any>();

  kinds: string[] = ['animal', 'orcs', 'hobbit', 'ents', 'men', 'dwarf', 'ainur', 'elves'];
  kindsSelected: any[] = [];
  
  characterIds: string[] = ['frod', 'sams', 'ganda', 'arag', 'pipp', 'merr', 'goll', 'gimli', 'bilb', 'lego', 'saur', 'fara', 'saru', 'boro', 'theod', 'elro', 'eome', 'treeb', 'tomb', 'dene'];
  characterLabels: string[] = ['Frodo', 'Sam', 'Gandalf', 'Aragorn', 'Pippin', 'Merry', 'Gollum', 'Gimli', 'Bilbo', 'Legolas', 'Sauron', 'Faramir', 'Saruman', 'Boromir', 'Théoden', 'Elrond', 'Éomer', 'Treebeard', 'Bombadil', 'Denethor'];
  characterSelected: any[] = [];

  placesIds: string[] = ['andu', 'bage', 'bree', 'dtow', 'edor', 'gond', 'helm', 'hton', 'isen', 'lori', 'loth', 'mdoo', 'mirk', 'mord', 'morg', 'mori', 'nume', 'oldf', 'orth', 'osgi', 'rive', 'roha', 'shir', 'tiri'];
  placesLabels: string[] = ['Anduin', 'Bag End', 'Bree', 'Dark Tower', 'Edoras', 'Gondor', 'Helm', 'Hobbiton', 'Isengard', 'Lórien', 'Lothlórien', 'Mount Doom', 'Mirkwood', 'Mordor', 'Morgul', 'Moria', 'Númenor', 'Old Forest', 'Orthanc', 'Osgiliath', 'Rivendell', 'Rohan', 'Shire', 'Minas Tirith'];
  placesSelected: any[] = [];

  kindsListChk: ChkItem[];
  charactersListChk: ChkItem[];
  placesListChk: ChkItem[];

  minStrenght = 1;
  maxStrenght = 533;

  form: FormGroup;
  
  // Query Dialog
  query = model('');
  readonly dialog = inject(MatDialog);

  constructor() { 

    this.kindsListChk = [];
    for (let i = 0; i < this.kinds.length; i++) {
      this.kindsListChk.push(new ChkItem(this.kinds[i], this.kinds[i]))
    }

    this.charactersListChk  = [];
    for (let i = 0; i < this.characterIds.length; i++) {
      this.charactersListChk.push(new ChkItem(this.characterIds[i], this.characterLabels[i]))
    }

    this.placesListChk  = [];
    for (let i = 0; i < this.placesIds.length; i++) {
      this.placesListChk.push(new ChkItem(this.placesIds[i], this.placesLabels[i]))
    }

    this.form = new FormGroup({
      kinds_fc: new FormControl(this.kindsListChk),  
      characters_fc: new FormControl(this.charactersListChk),
      places_fc: new FormControl(this.placesListChk),
      minStrenght_fc: new FormControl(this.minStrenght),
      maxStrenght_fc: new FormControl(this.maxStrenght),
      terrain_fc: new FormControl()
    });
  }

  ngOnInit() {
    for (let i = 0; i < this.kinds.length; i++) {
      this.kindsSelected.push(this.kinds[i]);
    }
    for (let i = 0; i < this.characterIds.length; i++) {
      this.characterSelected.push(this.characterIds[i]);
    }
    for (let i = 0; i < this.placesIds.length; i++) {
      this.placesSelected.push(this.placesIds[i]);
    }
  }

  runQuery(){
    let qInput = {
      kinds: this.kindsSelected,
      characters: this.characterSelected,
      places: this.placesSelected,
      minStrenght: this.minStrenght,
      maxStrenght: this.maxStrenght
    }
    this.queryLaunch.emit(qInput);
  }

  viewQuery() : void{
    const dialogRef = this.dialog.open(DialogQuery, {
      data: {query: this.query},
    });
  }

  
}

export interface DialogQueryData {
  query: string;
}

@Component({
  selector: 'dialog-query',
  templateUrl: 'dialog-query.html',
  standalone: true,
  imports: [
    MatDialogContent,
    MatDialogActions,
    MatButtonModule],
})
export class DialogQuery {
  readonly dialogRef = inject(MatDialogRef<DialogQuery>);
  readonly data = inject<DialogQueryData>(MAT_DIALOG_DATA);

  onClose(): void {
    this.dialogRef.close();
  }
}