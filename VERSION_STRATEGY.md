# Version Strategy for 은행 입금 알림

## Current Version
- **versionCode**: 1
- **versionName**: "1.0.0"

## Version Naming Convention
- **Format**: MAJOR.MINOR.PATCH
- **Example**: 1.2.3

### Version Code Strategy
- Increment by 1 for each release
- Formula: (MAJOR * 10000) + (MINOR * 100) + PATCH
- Examples:
  - 1.0.0 = 10000
  - 1.0.1 = 10001
  - 1.1.0 = 10100
  - 2.0.0 = 20000

### Version Name Strategy
- **MAJOR**: Breaking changes, major features, UI overhaul
- **MINOR**: New features, significant improvements
- **PATCH**: Bug fixes, minor improvements

## Release Schedule
- **Patch releases**: As needed for bug fixes
- **Minor releases**: Monthly or bi-monthly
- **Major releases**: Annually or for significant updates

## Update Checklist
1. Update versionCode in app/build.gradle
2. Update versionName in app/build.gradle
3. Update CHANGELOG.md with release notes
4. Tag git commit with version number (v1.0.0)
5. Build release AAB
6. Upload to Google Play Console

## Future Versions Roadmap
- **1.0.1**: Bug fixes from initial user feedback
- **1.1.0**: Add more bank support, improve TTS quality
- **1.2.0**: Add transaction history feature
- **2.0.0**: UI redesign, multi-language support