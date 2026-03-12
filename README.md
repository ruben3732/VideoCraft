# 🎬 VideoCraft — Android Video Editor

A full-featured Android video editing app with AI-powered features, Hinglish caption support, stock media integration, and professional timeline editing.

---

## ✨ Features

| Feature | Description |
|---|---|
| 📹 **Import & Edit** | Import videos & images from gallery, trim, split, reorder clips |
| ⚡ **Speed Control** | 0.1x slow motion to 4x fast forward |
| 🎵 **Audio** | Add background music, control volume per track, mute clips |
| 📝 **Text Overlays** | 16 custom fonts, 6 styles (Shadow, Outline, Neon, Bubble...), emojis |
| 🖼️ **Image / GIF Overlays** | Overlay images or GIFs with opacity, scale, rotation controls |
| 📐 **Aspect Ratios** | 9:16 (Reels/TikTok), 16:9 (YouTube), 1:1, 4:5, 4:3, 21:9 |
| 🔑 **Keyframes** | Animate position, scale, opacity of overlays over time |
| 💬 **Auto Captions** | Speech-to-text with English, Hindi & **Hinglish** support |
| 🌐 **Stock Library** | Browse millions of free photos/videos/GIFs (Pexels, Pixabay, Giphy) |
| ⚡ **AI Edit** | 1-click removes silences, filler words, and suggests B-roll |
| 📤 **Export** | Full HD export with share & save to gallery |

---

## 🚀 Setup

### 1. Prerequisites

- Android Studio Hedgehog (2023.1+) or newer
- Android SDK 34
- Java 17 (bundled with Android Studio)

### 2. Clone the project

```bash
git clone https://github.com/YOUR_USERNAME/VideoCraft.git
cd VideoCraft
```

### 3. Get free API keys

| Service | Link | What it provides |
|---|---|---|
| **Pexels** | [pexels.com/api](https://www.pexels.com/api/) | Free photos & videos |
| **Pixabay** | [pixabay.com/api/docs](https://pixabay.com/api/docs/) | Free photos & videos |
| **Giphy** | [developers.giphy.com](https://developers.giphy.com/) | Free GIFs |

### 4. Add API keys to `gradle.properties`

```properties
PEXELS_API_KEY=your_pexels_key_here
PIXABAY_API_KEY=your_pixabay_key_here
GIPHY_API_KEY=your_giphy_key_here
```

> 💡 All three APIs have generous free tiers — no credit card needed.

### 5. Build & Run

Open in Android Studio → click **Run** ▶

---

## 📱 Screenshots

> Coming soon — build the app and take your own!

---

## 🏗️ Architecture

```
app/src/main/
├── java/com/videocraft/editor/
│   ├── App.kt                          # Application class
│   ├── data/
│   │   ├── api/                        # Retrofit API services
│   │   │   ├── ApiClient.kt
│   │   │   ├── PexelsApiService.kt
│   │   │   ├── PixabayApiService.kt
│   │   │   ├── GiphyApiService.kt
│   │   │   └── StockMediaRepository.kt
│   │   └── model/
│   │       └── Models.kt               # All data classes
│   ├── ui/
│   │   ├── home/
│   │   │   ├── HomeActivity.kt         # Gallery picker
│   │   │   └── RecentVideosAdapter.kt
│   │   ├── editor/
│   │   │   ├── VideoEditorActivity.kt  # Main editor
│   │   │   ├── VideoEditorViewModel.kt
│   │   │   ├── OverlayCanvasView.kt    # Custom overlay renderer
│   │   │   ├── panels/                 # Bottom tool panels
│   │   │   │   ├── SpeedControlPanel.kt
│   │   │   │   ├── AudioPanel.kt
│   │   │   │   ├── TextPanel.kt
│   │   │   │   └── OtherPanels.kt
│   │   │   └── timeline/
│   │   │       └── TimelineView.kt     # Custom timeline with keyframes
│   │   ├── caption/
│   │   │   ├── CaptionActivity.kt      # Caption generator
│   │   │   ├── CaptionViewModel.kt
│   │   │   └── CaptionAdapter.kt
│   │   ├── stock/
│   │   │   ├── StockMediaActivity.kt   # Browse Pexels/Pixabay/Giphy
│   │   │   ├── StockMediaViewModel.kt
│   │   │   └── StockMediaAdapter.kt
│   │   └── aiedit/
│   │       ├── AIEditActivity.kt       # AI Edit screen
│   │       └── AIEditViewModel.kt
│   └── utils/
│       ├── FFmpegUtils.kt              # All video processing
│       ├── AudioAnalyzer.kt            # Silence/pause detection
│       ├── CaptionProcessor.kt         # Speech recognition + Hinglish
│       ├── FileUtils.kt                # MediaStore, file ops
│       └── Extensions.kt              # Kotlin extension functions
└── res/
    ├── layout/                         # All XML layouts
    ├── values/                         # Colors, strings, themes
    ├── drawable/                       # Icons, backgrounds
    └── xml/                            # FileProvider, backup rules
```

---

## 🛠️ Key Libraries

| Library | Purpose |
|---|---|
| `FFmpegKit 6.0` | Video processing (trim, speed, export, cut) |
| `Media3 ExoPlayer` | Video playback in editor |
| `Retrofit 2` | Pexels / Pixabay / Giphy API calls |
| `Glide 4` | Image loading & thumbnails |
| `Android SpeechRecognizer` | Speech-to-text for captions |
| `Material Components` | UI components |

---

## 💬 Caption Languages

- **English** — standard English captions
- **Hinglish** — code-mixed Hindi + English in Roman script (e.g., "Yeh trick bahut useful hai")
- **Hindi** — Devanagari script

The caption engine uses Android's built-in Speech Recognizer with `en-IN` locale which natively handles Hinglish.

---

## 🎯 Roadmap (Future Enhancements)

- [ ] Whisper API integration for accurate offline captions
- [ ] Sticker packs
- [ ] Transitions between clips
- [ ] Color grading / LUTs
- [ ] Sound FX library
- [ ] Undo/redo stack
- [ ] Project save/load
- [ ] TikTok / YouTube direct upload

---

## 📄 License

MIT License. Free photos/videos from Pexels and Pixabay under their respective free-use licenses. GIFs from Giphy.

---

Made with ❤️ using Android + Kotlin + FFmpegKit
