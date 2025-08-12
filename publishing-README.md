# Publishing

## 修改版本号
在LibraryGroups文件中，修改VERSION_SUFFIX
```
const val VERSION_SUFFIX = "Atlasv1"
```

## Releasing to mavenLocal/BuildDir

`
./gradlew publishReleasingLibrariesToMavenLocal
./gradlew publishReleasingLibrariesToBuildDir
`

In release-atlasv.json

```json
{
  "name": "m168",
  "libraries": [
    ":firebase-functions"
  ]
}
```
