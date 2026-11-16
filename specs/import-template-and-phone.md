# Import: template rows + phone check
- Template: row 1 column names, row 2 one hint per column, nothing else. Import reads data from row 3, so the old
  hints row was always reported as "Row 3: Product Price is required or invalid" and the sample row (Raju Kumar)
  was imported as a real customer.
- Phone: a non-blank phone that is not 10 digits (or +91/91 + 10) now skips the row with
  "Phone must be a 10-digit number (got "...")". Before, the customer was imported silently without a phone.
  Blank or text-only phone cells still import without a phone.
- Repeat-safe: importing the same file again (same owner, same bytes) within a day returns the first import's result
  and saves nothing new. Before, a timeout after the server had saved, then "try again", imported every sale and
  payment twice (customers were already matched by phone). Kept in memory; a restart forgets it. Dry runs are not
  remembered. A failed import is forgotten, so trying again really runs.
