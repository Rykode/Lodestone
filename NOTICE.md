# Third-party components

Lodestone is released under the MIT licence (see `LICENSE`). It includes or depends on:

| Component | Use | Licence |
|---|---|---|
| [Tesseract OCR](https://github.com/tesseract-ocr/tesseract) and its English language data (`eng.traineddata`, in `app/src/main/assets/tessdata/`) | Reads text inside pictures and PDFs on the phone | Apache-2.0 |
| [Tesseract4Android](https://github.com/adaptech-cz/Tesseract4Android) 4.8.0 | Android build of Tesseract. Its native libraries also include [Leptonica](http://www.leptonica.org/) (BSD-2-Clause style licence), libjpeg(-turbo) (IJG / BSD-3-Clause) and libpng (libpng licence) | Apache-2.0 for the wrapper |
| [Roboto Flex](https://github.com/googlefonts/roboto-flex) | App typeface | SIL Open Font License 1.1 |
| AndroidX, Jetpack Compose and Material 3 | User interface | Apache-2.0 |
| Kotlin and kotlinx.coroutines | Language and concurrency | Apache-2.0 |

The licence texts for Tesseract's data and Roboto Flex are in `app/src/main/assets/licenses/`. The app shows the Roboto Flex text under Settings → About and credits Tesseract there by name. Leptonica, libjpeg and libpng keep their own notices in their upstream projects.
