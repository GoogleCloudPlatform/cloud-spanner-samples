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
