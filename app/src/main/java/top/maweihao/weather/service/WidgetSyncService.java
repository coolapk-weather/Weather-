package top.maweihao.weather.service;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.Log;

import io.reactivex.functions.Consumer;
import io.reactivex.schedulers.Schedulers;
import top.maweihao.weather.entity.HeWeather.NewHeWeatherNow;
import top.maweihao.weather.entity.NewWeather;
import top.maweihao.weather.model.WeatherRepository;
import top.maweihao.weather.refactor.MLocation;
import top.maweihao.weather.util.Constants;
import top.maweihao.weather.util.Utility;
import top.maweihao.weather.util.remoteView.WidgetUtils;

public class WidgetSyncService extends Service {

    private static final String TAG = WidgetSyncService.class.getSimpleName();
    public static final String force_refresh = "FORCE_REFRESH";
    public static final String county_name = "COUNTY_NAME";
//    public static boolean working = false;
    private long lastRefreshTime = 0;
    private boolean hasWidget;
    private int interval;
    private int failedInterval;
    private boolean forceRefresh;
    private boolean isBigWidgetOn;
    private String countyName;
    private WeatherRepository weatherRepository;

//    private PreferenceConfigContact configContact;

    @Override
    public IBinder onBind(Intent intent) {
        throw null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        initData();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: ");
        isBigWidgetOn = WidgetUtils.hasBigWidget(this);
        if (intent != null) {
            String name = intent.getStringExtra(county_name);
            forceRefresh = intent.getBooleanExtra(force_refresh, false);
            countyName = (TextUtils.isEmpty(name)) ? countyName : name;
        }
        int refreshInterval = (int) ((System.currentTimeMillis() - lastRefreshTime)
                / (60 * 1000));
        if ((hasWidget && refreshInterval >= interval - 5) || forceRefresh) {
            startAgain(interval);
            fetchData(weatherRepository.getLocationCached());
        } else {
            startAgain(failedInterval);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    private void initData() {
        Log.d(TAG, "onCreate");
//        working = true;
        hasWidget = WidgetUtils.hasAnyWidget(this);
        interval = 90;
        failedInterval = 30;
        weatherRepository = WeatherRepository.getInstance(this);
    }

    private void startAgain(int interval) {
        Log.d(TAG, "startAgain: interval ==" + interval);
        Intent intent = new Intent(this, WidgetSyncService.class);
        PendingIntent pendingIntent = PendingIntent.getService(this,
                Constants.WidgetSyncServiceRequestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
            alarmManager.set(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + interval * 60 * 1000, pendingIntent);
        }
    }

    private void fetchData(final MLocation location) {
        int minInterval = 5;  //hardcode temporary

        long cachedLastUpdateTime = weatherRepository.getLastUpdateTime();
        if (System.currentTimeMillis() - cachedLastUpdateTime <= minInterval * 60 * 1000) {
            lastRefreshTime = cachedLastUpdateTime;
            weatherRepository.getWeatherCached()
                    .subscribeOn(Schedulers.io())
                    .subscribe(new Consumer<NewWeather>() {
                        @Override
                        public void accept(NewWeather weather) throws Exception {
                            Log.d(TAG, "fetchData: use cached weather to refresh the widget");
                            WidgetUtils.refreshWidget(WidgetSyncService.this,
                                    weather, location.getCoarseLocation());
                        }
                    }, new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) throws Exception {
                            Log.e(TAG, "fetchData: get cached weather failed " + throwable);
                        }
                    });
        } else if (true /*Utility.isNetworkAvailable(this)*/) {
            if (isBigWidgetOn) {
                weatherRepository.getHeWeatherNow(location.getLocationStringReversed())
                        .subscribeOn(Schedulers.io())
                        .subscribe(new Consumer<NewHeWeatherNow>() {
                            @Override
                            public void accept(NewHeWeatherNow weatherNow) throws Exception {
                                if (weatherNow != null &&
                                        weatherNow.getHeWeather5().get(0).getStatus().equals("ok")) {
                                    lastRefreshTime = Utility.getHeWeatherUpdateTime(weatherNow);
                                    WidgetUtils.refreshWidget(WidgetSyncService.this,
                                            weatherNow, location.getCoarseLocation());
                                } else {
                                    Log.e(TAG, "fetchData: he api error");
                                    startAgain(failedInterval);
                                }
                            }
                        }, new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable throwable) throws Exception {
                                Log.e(TAG, "fetchData: get heWeatherNow failed " + throwable);
                                startAgain(failedInterval);
                            }
                        });
            } else {
                weatherRepository.getWeather(location.getLocationStringReversed())
                        .subscribeOn(Schedulers.io())
                        .subscribe(new Consumer<NewWeather>() {
                            @Override
                            public void accept(NewWeather weather) throws Exception {
                                if (weather.getStatus().equals("ok")) {
                                    lastRefreshTime = weather.getServer_time() * 1000;
                                    WidgetUtils.refreshWidget(WidgetSyncService.this,
                                            weather, location.getCoarseLocation());
                                } else {
                                    Log.e(TAG, "fetchData: weather api error");
                                    startAgain(failedInterval);
                                }
                            }
                        }, new Consumer<Throwable>() {
                            @Override
                            public void accept(Throwable throwable) throws Exception {
                                Log.e(TAG, "fetchData: get weather failed" + throwable);
                                startAgain(failedInterval);
                            }
                        });
            }
        } else {
            Log.e(TAG, "fetchData: no internet connect to refresh the widget");
            startAgain(failedInterval);
        }

//        if (isBigWidgetOn && (weatherFull != null &&
//                System.currentTimeMillis() - weatherFullLastUpdateTime < minInterval * 60 * 1000)) {
//            WidgetUtils.refreshWidget(this, parseObject(weatherFull, ForecastBean.class));
//        } else if (!isBigWidgetOn && (weatherHeNow != null &&
//                System.currentTimeMillis() - weatherHeNowLastUpdateTime < minInterval * 60 * 1000)) {
//            WidgetUtils.refreshWidget(this, parseObject(weatherHeNow, HeNowWeather.class));
//        } else {
//            Log.d(TAG, " weather data out of date");
//            final String url = isBigWidgetOn ? configContact.getFurl()
//                    : HeWeatherUtil.initRequireUrl(HeWeatherUtil.MODE_NOW, coordinate);
//            Log.d(TAG, "fetchData: URL HERE" + HeWeatherUtil.initRequireUrl(HeWeatherUtil.MODE_NOW, coordinate));
//            new Thread(new Runnable() {
//                @Override
//                public void run() {
//                    if (url != null) {
//                        try {
//                            OkHttpClient client = new OkHttpClient();
//                            Request request = new Request.Builder()
//                                    .url(url).build();
//                            Response response = client.newCall(request).execute();
//
//                            if (isBigWidgetOn) {
//                                String body = response.body().string();
//                                configContact.applyWeatherFull(body);
//                                configContact.applyWeatherFullLastUpdateTime(System.currentTimeMillis());
//                                WidgetUtils.refreshWidget(WidgetSyncService.this,
//                                        parseObject(body, ForecastBean.class));
//                            } else {
//                                String body = response.body().string();
//                                configContact.applyWeatherHeNow(body);
//                                configContact.applyWeatherHeNowLastUpdateTime(System.currentTimeMillis());
//                                HeNowWeather heWeather = JSON.parseObject(body, HeNowWeather.class);
//                                WidgetUtils.refreshWidget(WidgetSyncService.this, heWeather);
//                            }
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                            startAgain(30);
//                            Log.e(TAG, "run: network error");
//                        }
//                    } else {
//                        startAgain(30);
//                        Log.e(TAG, "run: fUrl == null");
//                    }
//                }
//            }).start();
//
//        }
    }

}
