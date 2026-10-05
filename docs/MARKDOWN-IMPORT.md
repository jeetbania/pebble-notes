# Local Markdown import

The converter reads CommonMark with GitHub-style tables, strikethrough and checklist markers. It creates a mergeable Pebble backup; it never writes to a live library or fetches remote URLs.

Install the pinned dependency from `scripts/markdown-import-requirements.txt` in a virtual environment, then run `scripts/import-markdown.py SOURCE_FOLDER OUTPUT.leafbackup`. Import the output using Settings → Backups → Import backup on Mac. Take a library backup first. Imported notes default to an Apple Notes collection; `--collection` changes it. Subfolders remain nested collections.

The first leading Heading 1 becomes the note title. Other headings become styled text blocks; bullet, numbered and checked/unchecked rows become editable list blocks. Inline bold, italic, strikethrough and links use UTF-16 span offsets. Tables become editable Pebble tables. Explicitly fenced code stays literal because Pebble has no code block type. Unfenced indented blocks made entirely of checklist rows are treated as Apple Notes export checklists. Block quotes keep their visible quote markers.

Relative standalone images inside the export folder can be embedded. Missing images retain their name and source reference; nothing is fetched. Inline images remain readable references. Unsupported raw HTML remains literal. Table cells retain link destinations as text because cells have no rich spans. Original export files are never modified.

Repeat conversion of the exact same export produces the same note and revision IDs, preventing duplicates on reimport. Source changes generate a new note rather than overwriting existing content. Output can contain private note text and should remain local; do not commit it.

References: [CommonMark](https://spec.commonmark.org/0.30/) and [GitHub Flavored Markdown](https://github.github.io/gfm/).

Tests: run `tests/test_markdown_import.py` with the virtual environment’s Python. Validate the complete converted backup with the shared core before a real import. Mac backup import does this automatically.
