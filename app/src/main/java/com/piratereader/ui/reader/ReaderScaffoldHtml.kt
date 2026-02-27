package com.piratereader.ui.reader

import com.piratereader.data.library.LibraryBook

object ReaderScaffoldHtml {
    fun build(book: LibraryBook): String {
        val safeTitle = escapeHtml(book.title)
        val safeAuthors = escapeHtml(book.authors.ifBlank { "Unknown author" })
        val safePath = escapeHtml(book.localPath)
        val tocText = if (book.tocEntryCount > 0) "${book.tocEntryCount} entries" else "No TOC parsed yet"

        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8" />
              <meta name="viewport" content="width=device-width, initial-scale=1" />
              <style>
                :root {
                  color-scheme: light dark;
                  --bg: #f4f1e8;
                  --fg: #1f1a14;
                  --muted: #5f564b;
                  --panel: #ffffffcc;
                  --accent: #5b7c99;
                  --line: #d9d0bf;
                }
                @media (prefers-color-scheme: dark) {
                  :root {
                    --bg: #171513;
                    --fg: #f1ebe1;
                    --muted: #b9ad9c;
                    --panel: #211f1ccc;
                    --accent: #9fc3e3;
                    --line: #3a342d;
                  }
                }
                body {
                  margin: 0;
                  padding: 20px 16px 48px;
                  background: var(--bg);
                  color: var(--fg);
                  font: 16px/1.6 Georgia, "Times New Roman", serif;
                }
                .frame {
                  max-width: 56rem;
                  margin: 0 auto;
                  background: var(--panel);
                  border: 1px solid var(--line);
                  border-radius: 14px;
                  padding: 18px;
                  backdrop-filter: blur(2px);
                }
                h1 {
                  margin: 0 0 6px;
                  font-size: 1.4rem;
                  line-height: 1.25;
                }
                .author {
                  color: var(--muted);
                  margin-bottom: 14px;
                }
                .note {
                  padding: 10px 12px;
                  border-left: 4px solid var(--accent);
                  background: rgba(91, 124, 153, 0.08);
                  margin: 12px 0 16px;
                }
                .meta {
                  display: grid;
                  grid-template-columns: minmax(0, 12rem) 1fr;
                  gap: 8px 12px;
                  font-size: 0.95rem;
                }
                .label {
                  color: var(--muted);
                }
                code {
                  font-family: "JetBrains Mono", "Fira Code", monospace;
                  font-size: 0.9em;
                  word-break: break-all;
                }
                .roadmap {
                  margin-top: 18px;
                  border-top: 1px solid var(--line);
                  padding-top: 14px;
                }
                .roadmap ul {
                  margin: 8px 0 0 20px;
                  padding: 0;
                }
              </style>
            </head>
            <body>
              <div class="frame">
                <h1>$safeTitle</h1>
                <div class="author">$safeAuthors</div>
                <div class="note">
                  Reader fallback view. PirateReader could not load the requested EPUB chapter into the WebView,
                  but library metadata and book storage details are still available below.
                </div>
                <div class="meta">
                  <div class="label">Format</div><div>EPUB</div>
                  <div class="label">TOC</div><div>${escapeHtml(tocText)}</div>
                  <div class="label">Stored Path</div><div><code>$safePath</code></div>
                </div>
                <div class="roadmap">
                  <strong>Reader diagnostics</strong>
                  <ul>
                    <li>Confirm this file path exists and is readable by the app</li>
                    <li>Re-import the EPUB if the package was moved or corrupted</li>
                    <li>Check malformed EPUB contents (chapter/resource references)</li>
                    <li>Use the stored TOC count to verify package parsing completed</li>
                  </ul>
                </div>
              </div>
            </body>
            </html>
        """.trimIndent()
    }

    private fun escapeHtml(value: String): String =
        buildString(value.length) {
            value.forEach { ch ->
                when (ch) {
                    '&' -> append("&amp;")
                    '<' -> append("&lt;")
                    '>' -> append("&gt;")
                    '"' -> append("&quot;")
                    '\'' -> append("&#39;")
                    else -> append(ch)
                }
            }
        }
}
