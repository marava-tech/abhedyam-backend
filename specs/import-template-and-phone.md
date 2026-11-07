# Import: template rows + phone check
- Template: row 1 column names, row 2 one hint per column, nothing else. Import reads data from row 3, so the old
  hints row was always reported as "Row 3: Product Price is required or invalid" and the sample row (Raju Kumar)
  was imported as a real customer.
- Phone: a non-blank phone that is not 10 digits (or +91/91 + 10) now skips the row with
  "Phone must be a 10-digit number (got "...")". Before, the customer was imported silently without a phone.
  Blank or text-only phone cells still import without a phone.
