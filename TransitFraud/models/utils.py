# Copyright 2025 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

def writeSpanner(transaction, c, batch=1000, debug=False):
    rows = []
    print("Writing {} to Spanner".format(c.list_items[0].__class__.__name__))
    columns = c.list_items[0].__dataclass_fields__.keys()
    if debug:
        print(columns)
    for item in c.list_items:
        rows.append(tuple(item.__dict__.values()))
        if debug:
            print(tuple(item.__dict__.values()))
        if len(rows) % batch == 0:
            try:
                transaction.insert(
                    table=c.list_items[0].__class__.__name__,
                    columns=columns,
                    values=rows,
                )
                print("wrote {} rows".format(len(rows)))
                rows = []
            except:
                exit(1)
    if len(rows) > 0:
        try:
            transaction.insert(
                table=c.list_items[0].__class__.__name__, columns=columns, values=rows
            )
            print("wrote {} rows".format(len(rows)))
        except:
            print(rows)
            exit(1)
