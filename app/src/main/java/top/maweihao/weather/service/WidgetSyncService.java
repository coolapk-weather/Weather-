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
import top.maweihao.weather.contract.WeatherData;
import top.maweihao.weather.entity.dao.NewHeWeatherNow;
import top.maweihao.weather.entity.dao.NewWeather;
import top.maweihao.weather.model.WeatherRepository;
import top.maweihao.weather.entity.dao.MLocation;
import top.maweihao.weather.util.Constants;
import top.maweihao.weather.util.Utility;
import top.maweihao.weather.util.remoteView.WidgetUtils;
public class WidgetSyncService extends Service {

    private static final String TAG = WidgetSyncService.class.getSimpleName();

    public static final String force_refresh = "FORCE_REFRESH";
    private boolean forceRefresh;
    public static final String county_name = "COUNTY_NAME";
    private String countyName;
    public static final String from_widget = "from_widget";
    private boolean fromWidget;

    public static boolean working = false;
    private long lastRefreshTime = 0;
    private boolean hasWidget;
    private boolean isBigWidgetOn;

    private int interval;  //刷新成功时再次刷新的间隔
    private int failedInterval;  //刷新失败时再次刷新的间隔

    private WeatherRepository weatherRepository;

    @Override
    public IBinder onBind(Intent intent) {
        throw null;
    }

    @Override
    public void onCreate() {
        working = true;
        Log.d(TAG, "onCreate: ");
        super.onCreate();
        initData();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "HERE onStartCommand: ");

        if (intent != null && intent.getExtras() != null && intent.getExtras().getBoolean(WidgetSyncService.from_widget)) {
            Log.d(TAG, "onStartCommand: start job service");
            SyncService.scheduleSyncService(this.getApplicationContext(), true);
            stopSelf();
            return START_NOT_STICKY;
        }
        isBigWidgetOn = WidgetUtils.hasBigWidget(this);
        if (intent != null) {
            String name = intent.getStringExtra(county_name);
            forceRefresh = intent.getBooleanExtra(force_refresh, false);
            fromWidget = intent.getBooleanExtra(force_refresh, false);
            countyName = (TextUtils.isEmpty(name)) ? countyName : name;
        }
        int refreshInterval = (int) ((System.currentTimeMillis() - lastRefreshTime) / (60 * 1000));
        if (forceRefresh) {
            if ((hasWidget && refreshInterval >= interval - 5) || fromWidget) {
                startAgain(interval);
                fetchData(weatherRepository.getLocationCached());
            } else {
                if (isBigWidgetOn) {
                    WidgetUtils.refreshBigWidgetTime(this);
                }
                startAgain(interval);
                stopSelf();
            }
        } else {
            startAgain(interval);
            stopSelf();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        working = false;
    }

    private void initData() {
        Log.d(TAG, "onCreate");
//        working = true;
        hasWidget = WidgetUtils.hasAnyWidget(this);
        interval = 60;
        failedInterval = 20;
        weatherRepository = WeatherRepository.getInstance(this);
    }

    private void startAgain(int interval) {
        Log.d(TAG, "startAgain: interval ==" + interval);
        Intent intent = new Intent(this, WidgetSyncService.class);
        PendingIntent pendingIntent = PendingIntent.getService(this,
                Constants.WidgetSyncServiceRequestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        AlarmManager alarmManager = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarmManager != null) {
//            alarmManager.cancel(pendingIntent);
            alarmManager.set(AlarmManager.ELAPSED_REALTIME,
                    SystemClock.elapsedRealtime() + interval * 60 * 1000, pendingIntent);
        }
    }

    private void fetchData(final MLocation location) {
        int minInterval = 5;
        long now = System.currentTimeMillis();
        long cachedLastUpdateTime = weatherRepository.getLastUpdateTime();
        if (now - cachedLastUpdateTime <= minInterval * 60 * 1000) {
            lastRefreshTime = cachedLastUpdateTime;
            weatherRepository.getWeatherCached()
                    .subscribeOn(Schedulers.io())
                    .subscribe(new Consumer<NewWeather>() {
                        @Override
                        public void accept(NewWeather weather) throws Exception {
                            Log.d(TAG, "fetchCachedData: use cached weather to refresh the widget");
                            WidgetUtils.refreshWidget(WidgetSyncService.this,
                                    weather, location.getCoarseLocation());
                            stopSelf();
                        }
                    }, new Consumer<Throwable>() {
                        @Override
                        public void accept(Throwable throwable) throws Exception {
                            Log.e(TAG, "fetchCachedData: get cached weather failed " + throwable);
                            requestWeatherAndUpdate(location, weatherRepository, failedInterval);
                        }
                    });
        } else {
            if (!isBigWidgetOn) {
                long lut = weatherRepository.getLastHeNowUpdateTime();
                if (now - lut <= minInterval * 60 * 1000) {
                    lastRefreshTime = lut;
                    weatherRepository.getHeWeatherNowCached()
                            .subscribeOn(Schedulers.io())
                            .subscribe(new Consumer<NewHeWeatherNow>() {
                                @Override
                                public void accept(NewHeWeatherNow weather) throws Exception {
                                    Log.d(TAG, "fetchCachedData: use cached he weather to refresh the widget");
                                    WidgetUtils.refreshWidget(WidgetSyncService.this,
                                            weather, location.getCoarseLocation());
                                    stopSelf();
                                }
                            }, new Consumer<Throwable>() {
                                @Override
                                public void accept(Throwable throwable) throws Exception {
                                    Log.e(TAG, "fetchCachedData: get cached weather failed " + throwable);
                                    requestHeAndUpdate(location, weatherRepository, failedInterval);
                                }
                            });
                } else {
                    requestHeAndUpdate(location, weatherRepository, failedInterval);
                }
            } else {
                requestWeatherAndUpdate(location, weatherRepository, failedInterval);
            }
        }

    }

    private void requestWeatherAndUpdate(final MLocation location, WeatherData model, final int failedInterval) {
        model.getWeather(location.getLocationStringReversed())
                .subscribeOn(Schedulers.io())
                .subscribe(new Consumer<NewWeather>() {
                    @Override
                    public void accept(NewWeather weather) throws Exception {
                        if (weather.getStatus().equals("ok")) {
                            lastRefreshTime = weather.getServer_time() * 1000;
                            WidgetUtils.refreshWidget(WidgetSyncService.this,
                                    weather, location.getCoarseLocation());
                            Log.d(TAG, "fetchCachedData: fetch weather succeed!");
                        } else {
                            Log.e(TAG, "fetchCachedData: weather api error");
                            startAgain(failedInterval);
                        }
                        stopSelf();
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.e(TAG, "fetchCachedData: get weather failed" + throwable);
                        startAgain(failedInterval);
                        stopSelf();
                    }
                });
    }

    private void requestHeAndUpdate(final MLocation location, WeatherData model, final int failedInterval) {
        Log.d(TAG, "fetchCachedData: here" + location.getLocationStringReversed());
        model.getHeWeatherNow(location.getLocationStringReversed())
                .subscribeOn(Schedulers.io())
                .subscribe(new Consumer<NewHeWeatherNow>() {
                    @Override
                    public void accept(NewHeWeatherNow weatherNow) throws Exception {
                        if (weatherNow != null &&
                                weatherNow.getHeWeather5().get(0).getStatus().equals("ok")) {
                            lastRefreshTime = Utility.getHeWeatherUpdateTime(weatherNow);
                            WidgetUtils.refreshWidget(WidgetSyncService.this,
                                    weatherNow, location.getCoarseLocation());
                            Log.d(TAG, "fetchCachedData: fetch he weather succeed!");
                        } else {
                            Log.e(TAG, "fetchCachedData: he api error");
                            startAgain(failedInterval);
                        }
                        stopSelf();
                    }
                }, new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable throwable) throws Exception {
                        Log.e(TAG, "fetchCachedData: get heWeatherNow failed " + throwable);
                        startAgain(failedInterval);
                        stopSelf();
                    }
                });
    }

}
