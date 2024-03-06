# Publishing

## 修改版本号
在LibraryGroups文件中，加上Atlasv后缀
```
firebaseExtension.project.version = "${firebaseExtension.version}-Atlasv1"
```

## Java代码格式化
环境变量Java需要是11
```
java -version
java version "11.0.11"
```
```
./gradlew :<firebase-project>:googleJavaFormat
```

## All to maven local

`
./gradlew publishMavenAarPublicationToMavenLocal
`

## Releasing to mavenLocal/BuildDir

`
./gradlew publishReleasingLibrariesToMavenLocal
./gradlew publishReleasingLibrariesToBuildDir
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
