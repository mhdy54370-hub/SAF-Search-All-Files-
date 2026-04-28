# SAF — Search All Files

An Android app that searches the **content** of files on your phone — not just filenames.  
Enter a word or phrase and SAF finds it inside PDFs, Word documents, text files, and even images (via OCR).

---

## Features

| Feature | Details |
|---|---|
| 📄 Document search | TXT, PDF, DOCX, ODT, MD, JSON, CSV, code files, and more |
| 🖼️ Image OCR search | Extracts text from JPG, PNG, BMP using on-device ML Kit |
| ⚙️ Background indexing | Runs via WorkManager — phone can be locked |
| 🔔 Notifications | Notifies you when indexing is complete |
| 🔒 On-device only | No internet required, no data leaves your phone |
| 🆓 Completely free | No ads, no subscriptions |

---

## Screenshots

> *(Add screenshots here after building)*

---

## Requirements

- Android 8.0 (API 26) or higher
- Storage permission (`MANAGE_EXTERNAL_STORAGE` on Android 11+)

---

## Build Instructions

### Option A — Android Studio (recommended)

1. Install [Android Studio](https://developer.android.com/studio) (free)
2. Clone this repo:
   ```bash
   git clone https://github.com/YOUR_USERNAME/SAF.git
   ```
3. Open Android Studio → **Open** → select the `SAF` folder
4. Wait for Gradle sync to finish (first time downloads ~200 MB)
5. Connect your Android phone via USB (enable Developer Mode + USB Debugging)
6. Press the ▶ **Run** button

### Option B — Command line

```bash
# Clone
git clone https://github.com/YOUR_USERNAME/SAF.git
cd SAF

# Build debug APK
./gradlew assembleDebug

# APK will be at:
# app/build/outputs/apk/debug/app-debug.apk

# Install directly to connected phone
./gradlew installDebug
```

> **Windows users:** use `gradlew.bat` instead of `./gradlew`

---

## Publish to GitHub

```bash
# Inside the SAF folder:
git init
git add .
git commit -m "Initial commit — SAF v1.0"

# Create a repo on github.com, then:
git remote add origin https://github.com/YOUR_USERNAME/SAF.git
git branch -M main
git push -u origin main
```

---

## How It Works

1. **Index** — tap "Index Files". WorkManager scans your storage in the background.  
   - Documents: text extracted directly (PDF via PdfBox, DOCX via ZIP/XML parsing)  
   - Images: text extracted via Google ML Kit OCR (on-device, free)  
   - All extracted text is stored in a local SQLite database.

2. **Search** — enter a word/phrase and tap "Search Files".  
   SAF queries the local database instantly (no re-scanning needed).

3. **Open** — tap any result to open the file with the appropriate app.

---

## Architecture

```
com.saf.searchallfiles/
├── MainActivity.kt            # Main UI (search bar, checkboxes, index button)
├── SearchResultsActivity.kt   # Results list
├── data/
│   ├── AppDatabase.kt         # Room database
│   ├── IndexedFile.kt         # DB entity
│   └── IndexedFileDao.kt      # DB queries
├── parser/
│   ├── DocumentParser.kt      # TXT / PDF / DOCX / ODT extraction
│   └── OcrParser.kt           # ML Kit image OCR
├── worker/
│   └── IndexingWorker.kt      # WorkManager background task
└── adapter/
    └── ResultsAdapter.kt      # RecyclerView adapter for results
```

---

## Roadmap

- [ ] Audio file search (speech-to-text transcription)
- [ ] Incremental indexing (only re-index changed files)
- [ ] Regex search support
- [ ] Search history
- [ ] Export results to CSV

---

## License

MIT License — free to use, modify, and distribute.
