# sugo-actor-android-call

Android で動く、通報用 actor。


## イベント

+ [emergency](#event/emergency)


### emergency <a id="event/emergency">

通報した。
データは以下の要素を含む。

|key|value type|description|
|:--|:--|:--|
|id|数値|通報の識別番号|
|date|文字列|RFC3339 形式の日時|
|location|数値の配列|緯度、経度、高度|

例えば、

```json
{
  "id": 331549022,
  "date": "2016-11-11T18:31:35.593+09:00",
  "location": [
    35.701526,
    139.7531492,
    0
  ]
}
```
