# Publishing

## All to maven local

`
./gradlew publishMavenAarPublicationToMavenLocal
`

## Releasing to maven local

`
./gradlew publishReleasingLibrariesToMavenLocal
`

Add release.json

```json
{
  "name": "atlasv133",
  "libraries": [
    ":firebase-crashlytics",
    ":firebase-crashlytics:ktx",
    ":firebase-perf",
    ":firebase-perf:ktx"
  ]
}
```