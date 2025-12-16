# Release Notes - JDK21 Initial Release

## Version: jdk21-initial

**Release Date:** 2025-12-16

### Overview
Complete modernization of AozoraEpub3 for Java 21 and contemporary tooling, with improved EPUB 3.2 support and comprehensive dependency updates.

### Key Changes

#### 🔧 Build & Tooling
- **Gradle**: Upgraded to 9.2.1 (from legacy version)
- **Java**: Now built and tested on Java 21 (JDK 21 LTS)
- **CI/CD**: GitHub Actions workflows for automated building, testing, and EPUB validation
- **Optional**: Java 25 evaluation support in CI pipeline

#### 📦 Dependencies Updated
- **Apache Velocity**: 2.4.1 (template rendering for EPUB)
- **JSoup**: 1.18.1 (HTML parsing for web scrapers)
- **Apache Commons**: CLI 1.7.0, Collections 4.5.0, Compress 1.27.1, Lang3 3.15.0
- **Batik**: 1.18 (SVG support)
- **SLF4J**: 2.0.16 (logging)
- **Junrar**: 7.5.5 (archive extraction)

#### 🎨 EPUB Template & CSS Enhancements
- **Gaiji (外字) Font Support**: Fixed manifest inclusion in OPF; font declarations now properly injected via Velocity context
- **Title/Cover Layout**: Improved CSS padding and vertical alignment for Kindle and iOS renderers
- **Vertical Writing**: Enhanced writing-mode directives for Kindle (known iOS limitation documented)
- **Package.vm**: Loop-based gaiji font item generation in manifest

#### 🔄 Web Features
- **Rate Limiting**: Increased throttle to 1500ms default (minimum 1000ms) to respect server load
- **Supported Platforms**: narou.rb-compatible conversion for web novel scrapers
- **Caution**: ncode.syosetu.com HTML structure may have changed; selector updates may be required

#### 📝 Documentation
- **README.md**: 
  - Added project origin attribution (hmdev)
  - Documented narou.rb usage intent
  - Listed environment requirements and execution examples
  - Known issues: iPhone Kindle vertical writing rendering variance
  - Web scraping rate limits and potential selector breakage warnings
- **DEVELOPMENT.md**: Updated tooling versions, added epubcheck validation steps, clarified Velocity configuration
- **THIRD-PARTY-NOTICES.txt**: Updated to reflect current dependency versions
- **LICENSE.txt**: Noted derivative nature and JDK 21/25 modernization

#### 🔐 Security & Maintenance
- **Git Configuration**: Support for anonymous commit authorship (no-reply email configuration)
- **.gitignore**: Hardened against secrets leakage (keys, certs, .env, credentials)
- **Copilot Instructions**: Added AI assistant guidance for project contributions

### Testing
- **Unit Tests**: All 5 JUnit 4.13.2 tests passing
- **Build Status**: `BUILD SUCCESSFUL` with Gradle 9.2.1 and Java 21
- **EPUB Validation**: Ready for epubcheck validation in CI/CD pipeline

### Compatibility
- **Base Version**: Inherits all functionality and device presets from hmdev/AozoraEpub3
- **Device Presets**: Kobo (Touch, Glo, Full), Kindle Paperwhite, Reader (T3, standard)
- **Input Format**: Aozora Bunko text (.txt with ruby, bouten, gaiji, images support)
- **Output**: EPUB 3.2 compliant with 電書協/電書連ガイド (denso-booken) support

### Breaking Changes
None. This release is fully backward-compatible with existing Aozora text inputs and presets.

### Known Issues
- **iPhone Kindle**: Vertical writing mode title page rendering may vary; documented as expected behavior
- **ncode.syosetu.com**: Web scraper selectors in `web/ncode.syosetu.com/extract.txt` may need updating if HTML structure changed

### Migration Guide
Simply replace the JAR with the new build:
```bash
./gradlew distZip
# Use the generated zip in build/distributions/
```

Or build and run directly:
```bash
java -jar build/libs/AozoraEpub3.jar -of -d output/ input.txt
```

### Credits
- **Original Author**: hmdev
- **JDK 21 Modernization & Dependencies**: AozoraEpub3-JDK21 Contributors
- **Upstream**: narou.rb project collaboration intent

### Links
- **GitHub Repository**: https://github.com/AozoraEpub3-JDK21/AozoraEpub3-JDK21
- **Previous Stable**: pre-jdk21 tag (hmdev's final release)
- **Build**: See DEVELOPMENT.md for setup and contribution guidelines

---

**Summary**: This release brings AozoraEpub3 into the modern Java ecosystem while preserving compatibility with the original hmdev codebase. All tests pass, and builds are reproducible under Java 21.
