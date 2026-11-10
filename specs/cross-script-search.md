# Cross-script search (English ↔ Telugu / Hindi)

### Problem
Owners save customer and village names in whatever script they type: "Bhaskar Naidu" or "భాస్కర్ నాయుడు",
"Warangal" or "వరంగల్". Names are shown exactly as typed. Search only matches the same script, so an owner who
typed the name in Telugu and searches "bhaskar" finds nothing (and the other way round). Hindi comes next.

### Solution
Every customer name and village gets a hidden **search key**: the text turned into plain English letters, then
reduced to a sound skeleton that ignores spelling differences (th/t, sh/s, w/v, long/short vowels, double letters,
vowels after a word's first letter). "Bhaskar Naidu" and "భాస్కర్ నాయుడు" both become `bskrnd`.
Search matches what was typed (as today) **or** the search key. The key is never shown or returned.

- Telugu and Devanagari (Hindi, Marathi) letters convert with one table: Unicode lays the Indic blocks out in the
  same order. Another Indic script = one more block range.
- Keys are written on every save of a name or village; rows saved before this release are filled once on start.
- Search text shorter than 3 letters, or a phone number, uses the plain match only (short keys match too much).
- The same function will run in the app (item search) — `src/test/resources/search-key-vectors.json` holds the
  expected keys both sides test against.

### API Contract
No new endpoints, no response changes. Existing customer search (`GET /api/v1/customers/search`, filter, village
listing) and village search return extra matches from the other script.

### Data Model
- `users.name_key` VARCHAR(255) NULL
- `location_details.village_key` VARCHAR(255) NULL
Added by ddl-auto (nullable). Filled on save and by a one-time start-up fill.

### Edge Cases
- Name typed with Telugu keyboard joiners (ZWJ/ZWNJ) or decomposed vowel signs: normalised away first.
- Mixed scripts in one name ("Ravi రెడ్డి"): each part converted.
- Different spellings of the same place (Etunagaram / ఏటూరునాగారం = "Eturu-nagaram"): no rule can match these;
  the plain match still works in the script the owner typed.
- Words in a different order ("naidu bhaskar"): not matched (same as today).

### Out of Scope
- Showing names in another script (names are shown as typed).
- Merging the same village typed in two scripts (app asks "Same as Warangal?" — later, app PR).
- Ranking cross-script matches below exact ones (result sets per shop are small).
