# SMS Error Alarm (Android)

문자메시지 내용에 **"ERROR"** 라는 단어가 포함되어 있으면, 휴대폰이 무음/진동
모드여도 상관없이 알람 소리와 진동을 강제로 울려주는 안드로이드 앱입니다.

## 동작 원리

1. `SmsReceiver`가 `android.provider.Telephony.SMS_RECEIVED` 브로드캐스트를
   가로채서 문자 본문을 확인합니다.
2. 본문에 `ERROR`(대소문자 무관)가 포함되어 있으면 `AlarmService`
   (포그라운드 서비스)를 실행합니다.
3. `AlarmService`는 소리를 **STREAM_ALARM / USAGE_ALARM** 속성으로
   재생합니다. 안드로이드에서 알람 스트림은 벨소리(RING) 스트림과 달리
   무음/진동 모드의 영향을 받지 않기 때문에, 사용자가 폰을 무음으로
   해놔도 소리가 울립니다.
4. 혹시라도 사용자가 알람 볼륨 자체를 0으로 낮춰놓은 경우를 대비해,
   서비스 실행 시 알람 스트림 볼륨을 최대로 강제 설정합니다(알람이 끝나면
   원래 값으로 복원).
5. 알림 채널에 `setBypassDnd(true)`를 설정해 방해금지 모드에서도 알림이
   뜨도록 하고, 진동도 별도로 함께 울립니다.
6. 알림의 "알람 끄기" 버튼을 누르거나 30초가 지나면 자동으로 정지합니다
   (지속 시간은 `AlarmService.AUTO_STOP_MILLIS`에서 조정 가능).

## 프로젝트 구조

```
SmsAlarmApp/
├── build.gradle                 (프로젝트 레벨)
├── settings.gradle
└── app/
    ├── build.gradle              (앱 모듈)
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/example/smsalarm/
        │   ├── MainActivity.kt      # 권한 요청 화면
        │   ├── SmsReceiver.kt       # 문자 수신 감지
        │   ├── AlarmService.kt      # 알람음/진동 재생
        │   └── StopAlarmReceiver.kt # 알림의 "끄기" 버튼 처리
        └── res/
            ├── layout/activity_main.xml
            └── values/{strings.xml, themes.xml}
```

## 빌드 방법

1. Android Studio에서 `SmsAlarmApp` 폴더를 **Open** 으로 엽니다.
   (Gradle 동기화가 자동으로 진행됩니다.)
2. 실제 기기(에뮬레이터는 SMS 수신을 실제로 테스트하기 어려움)를 연결하고
   Run 버튼으로 설치합니다.
3. 앱을 처음 실행하면 화면의 버튼을 순서대로 눌러 아래 권한을 모두
   허용해주세요.
   - **문자/알림 권한**: 문자 수신 감지 및 알림 표시에 필수
   - **방해금지 모드 접근**: 무음 모드에서 알림까지 확실히 노출되게 함
   - **배터리 최적화 제외**: 백그라운드에서 앱이 시스템에 의해 종료되지
     않도록 함 (강력 추천)
4. "테스트 알람 울려보기" 버튼으로 실제 문자 없이도 동작을 바로 확인할
   수 있습니다.

## 꼭 알아두어야 할 제약사항

- **Google Play 배포 제한**: `RECEIVE_SMS`/`READ_SMS` 권한을 쓰는 앱은
  Play 스토어 정책상 "기본 문자 앱(default SMS handler)"으로 등록된
  경우에만 배포가 허용됩니다. 개인적으로 APK를 직접 설치(sideload)해서
  쓰는 용도라면 문제 없지만, 스토어에 정식 출시하려면 앱을 기본 문자
  앱으로 만들거나 별도 심사 예외를 받아야 합니다.
- **제조사별 백그라운드 제한**: 삼성/샤오미 등 일부 제조사는 자체적으로
  강력한 배터리 절전 정책을 적용해 백그라운드 앱을 종료시키기도
  합니다. 이런 경우 기기 설정에서 이 앱을 "제한 없음"으로 따로
  설정해줘야 안정적으로 동작합니다.
- **완전 무음(벨소리 볼륨 0 + 알람 볼륨도 0)**: 사용자가 알람 볼륨까지
  수동으로 0으로 낮춰놓으면 시스템이 원천적으로 소리를 막기 때문에,
  이 경우엔 앱이 볼륨을 최대로 강제 설정하는 로직으로 대응합니다
  (코드에 이미 포함되어 있음).
- 이 앱은 **안드로이드 전용**입니다. iOS는 앱이 문자 내용을 직접 읽거나
  벨소리 모드를 무시하도록 허용하지 않아 동일한 기능 구현이
  불가능합니다.

## 커스터마이징

- 감지 키워드 변경: `SmsReceiver.kt`의 `TRIGGER_KEYWORD` 값 수정
- 알람 지속 시간 변경: `AlarmService.kt`의 `AUTO_STOP_MILLIS` 값 수정
- 알람음 변경: `AlarmService.kt`의 `startAlarm()`에서 `RingtoneManager`
  대신 `res/raw` 폴더에 직접 넣은 mp3/wav 파일을 사용하도록 교체 가능
